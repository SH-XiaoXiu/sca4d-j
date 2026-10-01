package com.sca4d.auth.totp.enrollment;

import static com.sca4d.core.api.name.NameFormat.COLON;
import static java.lang.Boolean.TRUE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.totp.TotpErrorCode;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 使用 Redis TTL 保存加密 enrollment，并以 value CAS 完成一次性消费。 */
public class RedisTotpEnrollmentStore {

    private static final DefaultRedisScript<Long> CONSUME = consumeScript();

    private final Clock clock;

    private final RedisTemplate<Key, String> redis;

    private final TotpEnrollmentCodec codec;

    private static final TreeCode ENROLLMENT = TreeCode.ofColon("AUTH:TOTP_ENROLLMENT");

    private static final TreeCode ACCOUNT = TreeCode.ofColon("AUTH:TOTP_ENROLLMENTS:ACCOUNT");

    /**
     * 创建 Redis enrollment store。
     *
     * @param redis Redis 字符串操作模板
     * @param objectMapper enrollment 状态序列化器
     * @param clock TTL 与过期状态的时间来源
     */
    public RedisTotpEnrollmentStore(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull ObjectMapper objectMapper,
            @NotNull Clock clock) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.codec = new TotpEnrollmentCodec(objectMapper);
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    void create(TotpEnrollmentState state) {
        Duration ttl = remainingTtl(state);
        Key enrollmentKey = key(state.id());
        Boolean created = redis.opsForValue().setIfAbsent(enrollmentKey, codec.encode(state), ttl);
        if (!TRUE.equals(created)) {
            throw new IllegalStateException("TOTP enrollment 标识发生冲突");
        }
        Key accountKey = accountKey(state.accountId());
        try {
            redis.opsForSet().add(accountKey, enrollmentKey.format(COLON));
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
        Key enrollmentKey = key(checked.id());
        redis.delete(enrollmentKey);
        redis.opsForSet().remove(accountKey(checked.accountId()), enrollmentKey.format(COLON));
    }

    /**
     * 删除 Account 仍在 TTL 内的全部 enrollment，包括已经消费但尚未过期的状态。
     *
     * @param accountId 待清理 enrollment 的 Account
     */
    public void deleteByAccount(long accountId) {
        Key accountKey = accountKey(accountId);
        Set<String> enrollmentKeys = redis.opsForSet().members(accountKey);
        if (enrollmentKeys != null && !enrollmentKeys.isEmpty()) {
            redis.delete(enrollmentKeys.stream().map(value -> Key.parse(value, COLON)).toList());
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

    private Key key(String enrollmentId) {
        String checked = Objects.requireNonNull(enrollmentId, "TOTP enrollment 标识不能为空");
        if (checked.isBlank()) {
            throw unavailable();
        }
        return Key.of(ENROLLMENT).part(checked);
    }

    private Key accountKey(long accountId) {
        return Key.of(ACCOUNT).part(accountId);
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
