package com.sca4d.core.spring.redis;

import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.api.concurrent.RateLimitDecision;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 使用 Redis CAS 原子消耗多个应用实例共享的令牌桶额度。 */
public final class RedisRateLimiter implements RateLimiter {

    private final ProxyManager<String> proxyManager;

    private final RuntimeNamespace runtimeNamespace;

    /**
     * 创建使用当前应用 Redis 命名空间的共享限流器。
     *
     * @param proxyManager Bucket4j Redis CAS 代理
     * @param runtimeNamespace 当前应用的运行时命名空间
     */
    public RedisRateLimiter(
            @NotNull ProxyManager<String> proxyManager,
            @NotNull RuntimeNamespace runtimeNamespace) {
        this.proxyManager = Objects.requireNonNull(proxyManager, "Bucket4j 代理不能为空");
        this.runtimeNamespace = Objects.requireNonNull(runtimeNamespace, "运行时命名空间不能为空");
    }

    /**
     * 按业务键、额度和窗口取得共享令牌桶，并原子消耗一个令牌。
     *
     * <p>额度和窗口进入 Redis 键，避免同一个业务键改变配置后继续使用旧令牌桶。
     *
     * @param key 业务限流键
     * @param limit 令牌桶容量和完全恢复窗口
     * @return 是否消耗令牌以及被拒绝后的精确等待时间
     */
    @Override
    public @NotNull RateLimitDecision tryAcquire(@NotNull String key, @NotNull RateLimit limit) {
        String checkedKey = Objects.requireNonNull(key, "限流键不能为空");
        RateLimit checkedLimit = Objects.requireNonNull(limit, "限流要求不能为空");
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(
                        bandwidth -> bandwidth
                                .capacity(checkedLimit.permits())
                                .refillGreedy(checkedLimit.permits(), checkedLimit.window()))
                .build();
        String[] keySegments = checkedKey.split(":", -1);
        String[] storageSegments = new String[keySegments.length + 3];
        storageSegments[0] = "rate-limit";
        System.arraycopy(keySegments, 0, storageSegments, 1, keySegments.length);
        storageSegments[storageSegments.length - 2] = Long.toString(checkedLimit.permits());
        storageSegments[storageSegments.length - 1] = Long.toString(checkedLimit.window().toNanos());
        String storageKey = runtimeNamespace.sca4dj(storageSegments);
        ConsumptionProbe probe = proxyManager
                .getProxy(storageKey, () -> configuration)
                .tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitDecision.allow();
        }
        return RateLimitDecision.deny(java.time.Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }
}
