package com.sca4d.common.concurrent.ratelimit;

import org.jetbrains.annotations.NotNull;

/** 使用集群共享令牌桶状态执行单次容量判定的扩展点。 */
public interface RateLimiter {

    /**
     * 从指定 endpoint 与主体维度的桶中原子消费一个令牌。
     *
     * @param key 已摘要原始维度值的 Redis Bucket key
     * @param rule 容量、补充量和窗口均已校验的完整规则
     * @return 是否允许、剩余令牌和拒绝后的精确等待时间
     */
    @NotNull
    RateLimitResult tryConsume(@NotNull String key, @NotNull RateLimitRule rule);
}
