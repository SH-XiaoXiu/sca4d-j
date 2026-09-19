package com.sca4d.auth.totp.enrollment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.totp.TotpErrorCode;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 使用 Redis TTL 保存加密 enrollment，并以 value CAS 完成一次性消费。 */
public final class RedisTotpEnrollmentStore {

    private static final DefaultRedisScript<Long> CONSUME = consumeScript();

    private final StringRedisTemplate redis;

    private final TotpEnrollmentCodec codec;

    private final Clock clock;

    private final String keyPrefix;

    private final String accountKeyPrefix;

    /**
     * 创建 Redis enrollment store。
     *
     * @param redis Redis 字符串操作模板
     * @param objectMapper enrollment 状态序列化器
     * @param clock TTL 与过期状态的时间来源
     * @param runtimeNamespace 当前应用的运行时命名空间
     */
    public RedisTotpEnrollmentStore(
            @NotNull StringRedisTemplate redis,
            @NotNull ObjectMapper objectMapper,
            @NotNull Clock clock,
            @NotNull RuntimeNamespace runtimeNamespace) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.codec = new TotpEnrollmentCodec(objectMapper);
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        RuntimeNamespace checkedNamespace = Objects.requireNonNull(runtimeNamespace, "运行时命名空间不能为空");
        this.keyPrefix = checkedNamespace.sca4dj("auth", "totp-enrollment") + ':';
        this.accountKeyPrefix = checkedNamespace.sca4dj("auth", "totp-enrollments", "account") + ':';
    }

    void create(TotpEnrollmentState state) {
        Duration ttl = remainingTtl(state);
        String enrollmentKey = key(state.id());
        Boolean created = redis.opsForValue().setIfAbsent(enrollmentKey, codec.encode(state), ttl);
        if (!Boolean.TRUE.equals(created)) {
            throw new IllegalStateException("TOTP enrollment 标识发生冲突");
        }
        String accountKey = accountKey(state.accountId());
        try {
            redis.opsForSet().add(accountKey, enrollmentKey);
            redis.expire(accountKey, ttl);
        } catch (RuntimeException exception) {
            redis.delete(enrollmentKey);
            throw exception;
        }
    }

    TotpEnrollmentState loadById(String enrollmentId) {
        String encoded = redis.opsForValue().get(key(enrollmentId));
        if (encoded == null) {
            throw unavailable();
        }
        TotpEnrollmentState state = codec.decode(encoded);
        if (!state.id().equals(enrollmentId) || !clock.instant().isBefore(state.expiresAt())) {
            redis.delete(key(enrollmentId));
            throw unavailable();
        }
        if (state.status() != TotpEnrollmentStatus.PENDING) {
            throw unavailable();
        }
        return state;
    }

    void consume(TotpEnrollmentState state) {
        TotpEnrollmentState consumedState = state.consume();
        // Lua 在 Redis 内原子比较状态版本与 PENDING 状态，并保留原 TTL 写入 CONSUMED 状态。
        // 并发激活或替换只有一个调用能成功，失败方得到统一的 enrollment 不可用结果。
        Long consumed = redis.execute(
                CONSUME,
                List.of(key(state.id())),
                Long.toString(state.version()),
                state.status().name(),
                codec.encode(consumedState));
        if (!Long.valueOf(1).equals(consumed)) {
            throw unavailable();
        }
    }

    /** 删除一个已经验证归属关系的 enrollment。 */
    void delete(TotpEnrollmentState state) {
        TotpEnrollmentState checked = Objects.requireNonNull(state, "TOTP enrollment 不能为空");
        String enrollmentKey = key(checked.id());
        redis.delete(enrollmentKey);
        redis.opsForSet().remove(accountKey(checked.accountId()), enrollmentKey);
    }

    /**
     * 删除 Account 仍在 TTL 内的全部 enrollment，包括已经消费但尚未过期的状态。
     *
     * @param accountId 待清理 enrollment 的 Account
     */
    public void deleteByAccount(long accountId) {
        String accountKey = accountKey(accountId);
        java.util.Set<String> enrollmentKeys = redis.opsForSet().members(accountKey);
        if (enrollmentKeys != null && !enrollmentKeys.isEmpty()) {
            redis.delete(enrollmentKeys);
        }
        redis.delete(accountKey);
    }

    private Duration remainingTtl(TotpEnrollmentState state) {
        Duration ttl = Duration.between(clock.instant(), state.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            throw unavailable();
        }
        return ttl;
    }

    private String key(String enrollmentId) {
        String checked = Objects.requireNonNull(enrollmentId, "TOTP enrollment 标识不能为空");
        if (checked.isBlank()) {
            throw unavailable();
        }
        return keyPrefix + checked;
    }

    private String accountKey(long accountId) {
        return accountKeyPrefix + accountId;
    }

    private static BusinessException unavailable() {
        return new BusinessException(TotpErrorCode.ENROLLMENT_UNAVAILABLE);
    }

    private static DefaultRedisScript<Long> consumeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/authentication/totp-enrollment/consume.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
