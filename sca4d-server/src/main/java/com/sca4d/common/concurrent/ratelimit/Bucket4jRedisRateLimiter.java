package com.sca4d.common.concurrent.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.jetbrains.annotations.NotNull;

/**
 * 基于 Redis Bucket4j 的限流器。
 */
public class Bucket4jRedisRateLimiter implements RateLimiter {

    private final ProxyManager<String> proxyManager;

    /**
     * 创建使用 Redis CAS 状态和隐式配置版本替换的 Bucket4j 限流器。
     *
     * @param proxyManager 已配置 Redis 状态过期策略的 Bucket4j 代理管理器
     */
    public Bucket4jRedisRateLimiter(@NotNull ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public @NotNull RateLimitResult tryConsume(@NotNull String key, @NotNull RateLimitRule rule) {
        BucketConfiguration configuration = configuration(rule);
        BucketProxy bucket = proxyManager
                .builder()
                .withImplicitConfigurationReplacement(rule.configurationVersion(), TokensInheritanceStrategy.AS_IS)
                .build(key, () -> configuration);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitResult.allowed(probe.getRemainingTokens());
        }
        return RateLimitResult.rejected(probe.getRemainingTokens(), probe.getNanosToWaitForRefill());
    }

    /**
     * 将固定限流规则转换为 Bucket4j 贪婪补充配置。
     *
     * @param rule 已完成正数校验的容量、补充量和窗口
     * @return 可由 ProxyManager 在所有节点复用的 Bucket 配置
     */
    private @NotNull BucketConfiguration configuration(@NotNull RateLimitRule rule) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rule.getCapacity())
                        .refillGreedy(rule.getRefillTokens(), rule.getWindow())
                        .build())
                .build();
    }
}
