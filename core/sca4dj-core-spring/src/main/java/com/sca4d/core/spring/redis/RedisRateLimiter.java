package com.sca4d.core.spring.redis;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.concurrent.RateLimit;
import com.sca4d.core.api.concurrent.RateLimitDecision;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.name.Key;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/** 使用 Redis CAS 原子消耗多个应用实例共享的令牌桶额度。 */
public class RedisRateLimiter implements RateLimiter {

    private final ProxyManager<String> proxyManager;

    /**
     * 创建使用当前 App 隔离名称的共享限流器。
     *
     * @param proxyManager Bucket4j Redis CAS 代理
     */
    public RedisRateLimiter(
            @NotNull ProxyManager<String> proxyManager) {
        this.proxyManager = Objects.requireNonNull(proxyManager, "Bucket4j 代理不能为空");
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
    public @NotNull RateLimitDecision tryAcquire(@NotNull Key key, @NotNull RateLimit limit) {
        Key checkedKey = Objects.requireNonNull(key, "限流键不能为空");
        RateLimit checkedLimit = Objects.requireNonNull(limit, "限流要求不能为空");
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(
                        bandwidth -> bandwidth
                                .capacity(checkedLimit.permits())
                                .refillGreedy(checkedLimit.permits(), checkedLimit.window()))
                .build();
        Key storageKey = checkedKey.part(checkedLimit.permits()).part(checkedLimit.window().toNanos());
        ConsumptionProbe probe = proxyManager
                .getProxy(storageKey.format(COLON), () -> configuration)
                .tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitDecision.allow();
        }
        return RateLimitDecision.deny(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }
}
