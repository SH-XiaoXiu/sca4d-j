package com.sca4d.auth.email.challenge;

import static java.lang.Boolean.TRUE;

import com.sca4d.auth.email.EmailCodeChallengeId;
import com.sca4d.auth.email.EmailCodeErrorCode;
import com.sca4d.auth.email.exception.AuthEmailException;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 使用 Redis Hash TTL 和 Lua 原子消费 Email Code Challenge。 */
public class RedisEmailCodeChallengeStore {

    private static final DefaultRedisScript<Long> VERIFY = verifyScript();

    private final @NotNull Clock clock;

    private final @NotNull RedisTemplate<Key, String> redis;

    private final @NotNull Key challengePrefix;

    private final @NotNull Key cooldownPrefix;

    /**
     * @param redis Email Challenge 的 Redis 操作模板
     * @param clock 到期时间来源
     */
    public RedisEmailCodeChallengeStore(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull Clock clock) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");

        this.challengePrefix = Key.of(TreeCode.ofColon("AUTHENTICATION:EMAIL_CODE:CHALLENGE"));
        this.cooldownPrefix = Key.of(TreeCode.ofColon("AUTHENTICATION:EMAIL_CODE:COOLDOWN"));
    }

    /**
     * 为规范化目标登记重发冷却时间。
     *
     * @return 当前不存在冷却记录并成功登记时为 {@code true}
     */
    @CheckReturnValue
    public boolean reserveCooldown(@NotNull String targetHash, @NotNull Duration interval) {
        return TRUE.equals(
                redis.opsForValue()
                        .setIfAbsent(
                                cooldownPrefix.part(Objects.requireNonNull(targetHash, "冷却目标不能为空")),
                                "1",
                                Objects.requireNonNull(interval, "重发间隔不能为空")));
    }

    /**
     * 使用 Challenge 剩余有效时间新增 Redis Hash 状态。
     *
     * <p>标识已存在时直接拒绝，不覆盖原状态。方法先整体写入 Hash，再按 {@code expiresAt} 设置 TTL；
     * 若有效期不晚于当前时间，会删除刚写入的 Hash 并抛出过期错误。存在性检查与写入不在同一个 Redis 原子操作内，
     * 调用方应使用新生成的 UUID 作为标识。
     *
     * @param state 待保存的 Challenge 状态
     * @throws BusinessException 标识已存在时抛出 {@link EmailCodeErrorCode#CHALLENGE_ID_CONFLICT}；
     *                           {@code expiresAt} 不晚于当前时间时抛出 {@link EmailCodeErrorCode#CHALLENGE_EXPIRED}
     */
    public void create(@NotNull EmailCodeChallengeState state) {
        EmailCodeChallengeState checked = Objects.requireNonNull(state, "Challenge 状态不能为空");
        Key key = key(checked.challengeId());
        if (TRUE.equals(redis.hasKey(key))) {
            throw new BusinessException(EmailCodeErrorCode.CHALLENGE_ID_CONFLICT);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("accountId", Long.toString(checked.accountId()));
        fields.put("codeHash", checked.codeHash());
        fields.put("maskedRecipient", checked.maskedRecipient());
        fields.put("expiresAt", Long.toString(checked.expiresAt().toEpochMilli()));
        fields.put("resendAt", Long.toString(checked.resendAt().toEpochMilli()));
        fields.put("attemptCount", Integer.toString(checked.attemptCount()));
        fields.put("status", checked.status().name());
        fields.put("version", Long.toString(checked.version().value()));
        redis.opsForHash().putAll(key, fields);
        Duration ttl = Duration.between(clock.instant(), checked.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            redis.delete(key);
            throw new BusinessException(EmailCodeErrorCode.CHALLENGE_EXPIRED);
        }
        redis.expire(key, ttl);
    }

    /**
     * 读取仍存在于 Redis 的 Challenge，不修改任何数据。
     *
     * @param challengeId Challenge 标识
     * @return 仍存在于 Redis 的 Challenge；Hash 不存在或已过 TTL 时为空
     * @throws AuthEmailException Hash 字段缺失、为空或无法解析时抛出，表示 Redis 中的 Challenge 数据已损坏
     */
    @CheckReturnValue
    public @Nullable EmailCodeChallengeState find(@NotNull EmailCodeChallengeId challengeId) {
        Map<Object, Object> fields = redis.opsForHash().entries(key(challengeId));
        if (fields.isEmpty()) {
            return null;
        }
        try {
            return new EmailCodeChallengeState(
                    challengeId,
                    Long.parseLong(fieldValue(fields, "accountId")),
                    fieldValue(fields, "codeHash"),
                    fieldValue(fields, "maskedRecipient"),
                    Instant.ofEpochMilli(Long.parseLong(fieldValue(fields, "expiresAt"))),
                    Instant.ofEpochMilli(Long.parseLong(fieldValue(fields, "resendAt"))),
                    Integer.parseInt(fieldValue(fields, "attemptCount")),
                    EmailCodeChallengeStatus.valueOf(fieldValue(fields, "status")),
                    new Version(Long.parseLong(fieldValue(fields, "version"))));
        } catch (RuntimeException exception) {
            throw new AuthEmailException("Email Code Challenge Redis 数据损坏", exception);
        }
    }

    /**
     * 按读取版本原子记录一次校验结果。
     *
     * <p>Lua 脚本在 Challenge 缺失、状态不是 PENDING、已过期或版本与 {@code state} 不一致时不写入。
     * 版本一致且仍为 PENDING 时：{@code valid} 为 true 则状态改为 CONSUMED；为 false 则错误次数加一，
     * 次数达到 {@code maximumAttempts} 时状态改为 EXHAUSTED。写入后版本都会递增，包括计入错误次数的失败。
     *
     * @param state consume 读取到的 Challenge 状态，其版本用于比较
     * @param valid 本次验证码校验是否成功
     * @param maximumAttempts 允许的最大错误次数
     * @return 成功消费后的新版本；校验失败（已计入错误次数）、未写入或版本并发变化时为空
     */
    @CheckReturnValue
    public @Nullable Version verify(
            @NotNull EmailCodeChallengeState state,
            boolean valid,
            int maximumAttempts) {
        Long result = redis.execute(
                VERIFY,
                List.of(key(state.challengeId())),
                Long.toString(state.version().value()),
                valid ? "1" : "0",
                Integer.toString(maximumAttempts),
                Long.toString(clock.instant().toEpochMilli()));
        return result != null && result > 0 ? new Version(result) : null;
    }

    /** 从 Hash 字段读取必填文本。 */
    private static @NotNull String fieldValue(@NotNull Map<Object, Object> fields, @NotNull String name) {
        Object value = fields.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Challenge 缺少字段: " + name);
        }
        return text;
    }

    /** @return 当前 App 隔离名称下的 Challenge key */
    private @NotNull Key key(@NotNull EmailCodeChallengeId challengeId) {
        return challengePrefix.part(Objects.requireNonNull(challengeId, "Challenge 标识不能为空").value());
    }

    /** 加载按版本、状态和到期时间原子消费或累计错误次数的 Lua。 */
    private static @NotNull DefaultRedisScript<Long> verifyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/authentication/email-code/verify.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
