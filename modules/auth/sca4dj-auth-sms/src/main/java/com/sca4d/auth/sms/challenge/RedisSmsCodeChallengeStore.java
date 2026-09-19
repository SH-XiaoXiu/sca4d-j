package com.sca4d.auth.sms.challenge;

import com.sca4d.auth.sms.SmsCodeChallengeId;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 使用 Redis Hash TTL 和 Lua 原子消费 SMS Code Challenge。 */
public final class RedisSmsCodeChallengeStore {
    private static final DefaultRedisScript<Long> VERIFY = verifyScript();

    private final StringRedisTemplate redis;

    private final Clock clock;

    private final String challengePrefix;

    private final String cooldownPrefix;

    /** 创建按运行命名空间隔离的 SMS Challenge Store。 */
    public RedisSmsCodeChallengeStore(
            @NotNull StringRedisTemplate redis,
            @NotNull Clock clock,
            @NotNull RuntimeNamespace namespace) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.clock = Objects.requireNonNull(clock, "Clock 不能为空");
        RuntimeNamespace checked = Objects.requireNonNull(namespace, "RuntimeNamespace 不能为空");
        this.challengePrefix = checked.sca4dj("authentication", "sms-code", "challenge") + ':';
        this.cooldownPrefix = checked.sca4dj("authentication", "sms-code", "cooldown") + ':';
    }

    /** @return 当前不存在冷却记录并成功登记时为 true */
    public boolean reserveCooldown(@NotNull String targetHash, @NotNull Duration interval) {
        return Boolean.TRUE.equals(
                redis.opsForValue()
                        .setIfAbsent(
                                cooldownPrefix + targetHash,
                                "1",
                                Objects.requireNonNull(interval, "重发间隔不能为空")));
    }

    /** 使用 Challenge 剩余有效时间新增状态。 */
    public void create(@NotNull SmsCodeChallengeState state) {
        SmsCodeChallengeState checked = Objects.requireNonNull(state, "Challenge 状态不能为空");
        String key = key(checked.challengeId());
        if (Boolean.TRUE.equals(redis.hasKey(key)))
            throw new IllegalStateException("SMS Challenge 标识冲突");
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
            throw new IllegalStateException("SMS Challenge 已经过期");
        }
        redis.expire(key, ttl);
    }

    /** @return 仍存在于 Redis 的 Challenge */
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
            throw new IllegalStateException("SMS Challenge Redis 数据损坏", exception);
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

    private String key(SmsCodeChallengeId id) {
        return challengePrefix + id.value();
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
