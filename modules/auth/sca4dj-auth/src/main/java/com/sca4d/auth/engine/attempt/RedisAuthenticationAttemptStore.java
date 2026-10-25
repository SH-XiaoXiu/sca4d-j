package com.sca4d.auth.engine.attempt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.authentication.AuthErrorCode;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** 使用 Redis TTL 保存认证尝试，并以自动续租的分布式锁串行修改同一尝试。 */
@ApiStatus.Internal
public class RedisAuthenticationAttemptStore {

    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(2);

    private final AuthenticationAttemptCodec codec;

    private final Clock clock;

    private final DistributedLock distributedLock;

    private final RedisTemplate<Key, String> redis;

    private static final TreeCode ATTEMPT = TreeCode.ofColon("AUTHENTICATION:ATTEMPT");

    /**
     * 创建 Redis 认证尝试存储。
     *
     * @param redis Redis 字符串操作
     * @param objectMapper JSON 映射器
     * @param clock TTL 时间来源
     * @param distributedLock 自动续租的跨实例分布式锁
     */
    public RedisAuthenticationAttemptStore(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull ObjectMapper objectMapper,
            @NotNull Clock clock,
            @NotNull DistributedLock distributedLock) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.codec = new AuthenticationAttemptCodec(objectMapper);
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "认证尝试分布式锁不能为空");
    }

    /** 使用尝试的剩余有效时间新增状态；标识已经存在时拒绝覆盖。 */
    public void create(@NotNull AuthenticationAttemptState state) {
        Duration ttl = remainingTtl(state);
        Boolean created = redis.opsForValue().setIfAbsent(attemptKey(state.id()), codec.encode(state), ttl);
        if (!Boolean.TRUE.equals(created)) {
            throw new BusinessException(AuthErrorCode.AUTH_ATTEMPT_ID_CONFLICT);
        }
    }

    /** 读取仍在有效期内的尝试；缺失、损坏或过期时抛出异常。 */
    public @NotNull AuthenticationAttemptState loadById(@NotNull AuthenticationAttemptId id) {
        String encoded = redis.opsForValue().get(attemptKey(id));
        if (encoded == null) {
            throw new BusinessException(AuthErrorCode.AUTH_ATTEMPT_EXPIRED);
        }
        AuthenticationAttemptState state = codec.decode(encoded);
        if (!state.id().equals(id) || !clock.instant().isBefore(state.expiresAt())) {
            redis.delete(attemptKey(id));
            throw new BusinessException(AuthErrorCode.AUTH_ATTEMPT_EXPIRED);
        }
        return state;
    }

    /** 使用当前剩余有效时间覆盖尝试；调用方必须已经取得该尝试的租约。 */
    public void save(@NotNull AuthenticationAttemptState state) {
        redis.opsForValue().set(attemptKey(state.id()), codec.encode(state), remainingTtl(state));
    }

    /** 删除已消费或不再需要的尝试；记录不存在时保持幂等。 */
    public void delete(@NotNull AuthenticationAttemptId id) {
        redis.delete(attemptKey(id));
    }

    /**
     * 在最多等待两秒取得的自动续租分布式锁内执行操作。
     *
     * @throws IllegalStateException 等待期间始终由其他实例持有租约时抛出
     */
    public <T> T withLease(@NotNull AuthenticationAttemptId id, @NotNull Supplier<T> action) {
        AuthenticationAttemptId checkedId = Objects.requireNonNull(id, "认证尝试标识不能为空");
        return distributedLock.execute(
                new LockKey(TreeCode.of("AUTHENTICATION:ATTEMPT"), checkedId.value()),
                ACQUIRE_TIMEOUT,
                Objects.requireNonNull(action, "认证尝试操作不能为空"));
    }

    /** 返回状态相对当前时钟的剩余有效时间，已经到期时拒绝写入 Redis。 */
    private @NotNull Duration remainingTtl(@NotNull AuthenticationAttemptState state) {
        Duration ttl = Duration.between(clock.instant(), state.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            throw new BusinessException(AuthErrorCode.AUTH_ATTEMPT_EXPIRED);
        }
        return ttl;
    }

    /** 返回当前 App 隔离名称下的认证尝试 Redis key。 */
    private @NotNull Key attemptKey(@NotNull AuthenticationAttemptId id) {
        return Key.of(ATTEMPT).part(Objects.requireNonNull(id, "认证尝试标识不能为空").value());
    }

}
