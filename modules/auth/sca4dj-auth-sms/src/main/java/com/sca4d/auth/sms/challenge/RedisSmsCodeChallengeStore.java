package com.sca4d.auth.sms.challenge;

import static java.lang.Boolean.TRUE;

import com.sca4d.auth.sms.SmsCodeChallengeId;
import com.sca4d.auth.sms.SmsCodeErrorCode;
import com.sca4d.auth.sms.exception.AuthSmsException;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
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

/** 使用 Redis Hash TTL 和 Lua 原子消费 SMS Code Challenge。 */
public class RedisSmsCodeChallengeStore {
    private static final DefaultRedisScript<Long> VERIFY = verifyScript();

    private final Clock clock;

    private final RedisTemplate<Key, String> redis;

    private final Key challengePrefix;

    private final Key cooldownPrefix;

    /** 创建按 App 隔离名称隔离的 SMS Challenge Store。 */
    public RedisSmsCodeChallengeStore(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull Clock clock) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");

        this.challengePrefix = Key.of(TreeCode.ofColon("AUTHENTICATION:SMS_CODE:CHALLENGE"));
        this.cooldownPrefix = Key.of(TreeCode.ofColon("AUTHENTICATION:SMS_CODE:COOLDOWN"));
    }

    /** @return 当前不存在冷却记录并成功登记时为 true */
    public boolean reserveCooldown(@NotNull String targetHash, @NotNull Duration interval) {
        return TRUE.equals(
                redis.opsForValue()
                        .setIfAbsent(
                                cooldownPrefix.part(targetHash),
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
     * @throws BusinessException 标识已存在时抛出 {@link SmsCodeErrorCode#CHALLENGE_ID_CONFLICT}；
     *                           {@code expiresAt} 不晚于当前时间时抛出 {@link SmsCodeErrorCode#CHALLENGE_EXPIRED}
     */
    public void create(@NotNull SmsCodeChallengeState state) {
        SmsCodeChallengeState checked = Objects.requireNonNull(state, "Challenge 状态不能为空");
        Key key = key(checked.challengeId());
        if (TRUE.equals(redis.hasKey(key)))
            throw new BusinessException(SmsCodeErrorCode.CHALLENGE_ID_CONFLICT);
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
            throw new BusinessException(SmsCodeErrorCode.CHALLENGE_EXPIRED);
        }
        redis.expire(key, ttl);
    }

    /**
     * 读取仍存在于 Redis 的 Challenge，不修改任何数据。
     *
     * @param challengeId Challenge 标识
     * @return 仍存在于 Redis 的 Challenge；Hash 不存在或已过 TTL 时为空
     * @throws AuthSmsException Hash 字段缺失、为空或无法解析时抛出，表示 Redis 中的 Challenge 数据已损坏
     */
    public @Nullable SmsCodeChallengeState find(@NotNull SmsCodeChallengeId challengeId) {
        Map<Object, Object> fields = redis.opsForHash().entries(key(challengeId));
        if (fields.isEmpty())
            return null;
        try {
            return new SmsCodeChallengeState(
                    challengeId,
                    Long.parseLong(field(fields, "accountId")),
                    field(fields, "codeHash"),
                    field(fields, "maskedRecipient"),
                    Instant.ofEpochMilli(Long.parseLong(field(fields, "expiresAt"))),
                    Instant.ofEpochMilli(Long.parseLong(field(fields, "resendAt"))),
                    Integer.parseInt(field(fields, "attemptCount")),
                    SmsCodeChallengeStatus.valueOf(field(fields, "status")),
                    new Version(Long.parseLong(field(fields, "version"))));
        } catch (RuntimeException exception) {
            throw new AuthSmsException("SMS Challenge Redis 数据损坏", exception);
        }
    }

    /** @return 成功消费后的新版本；失败或并发变化时为空 */
    public @Nullable Version verify(@NotNull SmsCodeChallengeState state, boolean valid, int maximumAttempts) {
        Long result = redis.execute(
                VERIFY,
                List.of(key(state.challengeId())),
                Long.toString(state.version().value()),
                valid ? "1" : "0",
                Integer.toString(maximumAttempts),
                Long.toString(clock.instant().toEpochMilli()));
        return result != null && result > 0 ? new Version(result) : null;
    }

    private Key key(SmsCodeChallengeId id) {
        return challengePrefix.part(id.value());
    }

    private static String field(Map<Object, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof String text) || text.isBlank())
            throw new IllegalStateException("Challenge 缺少字段: " + name);
        return text;
    }

    private static DefaultRedisScript<Long> verifyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/authentication/sms-code/verify.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
