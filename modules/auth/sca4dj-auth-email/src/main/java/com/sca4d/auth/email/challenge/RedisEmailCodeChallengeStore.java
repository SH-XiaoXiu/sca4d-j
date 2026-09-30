package com.sca4d.auth.email.challenge;

import static java.lang.Boolean.TRUE;

import com.sca4d.auth.email.EmailCodeChallengeId;
import com.sca4d.core.api.concurrent.Version;
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

    /** 使用 Challenge 剩余有效时间新增状态，标识冲突时拒绝覆盖。 */
    public void create(@NotNull EmailCodeChallengeState state) {
        EmailCodeChallengeState checked = Objects.requireNonNull(state, "Challenge 状态不能为空");
        Key key = key(checked.challengeId());
        if (TRUE.equals(redis.hasKey(key))) {
            throw new IllegalStateException("Email Code Challenge 标识冲突");
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
            throw new IllegalStateException("Email Code Challenge 已经过期");
        }
        redis.expire(key, ttl);
    }

    /** @return 仍存在于 Redis 的 Challenge；缺失时为空 */
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
            throw new IllegalStateException("Email Code Challenge Redis 数据损坏", exception);
        }
    }

    /**
     * 按读取版本原子记录一次校验结果。
     *
     * @return 成功消费后的新版本；失败、过期、耗尽或并发变化时为空
     */
    @CheckReturnValue
    public Version verify(
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
