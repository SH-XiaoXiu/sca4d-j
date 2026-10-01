package com.sca4d.core.api.concurrent;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/** 使用共享令牌桶判断一个业务键是否仍有调用额度。 */
public interface RateLimiter {

    /**
     * 尝试消耗一次调用额度。
     *
     * @param key 业务限流键
     * @param limit 限流要求
     * @return 是否取得额度以及拒绝后的等待时间
     */
    @CheckReturnValue
    @NotNull
    RateLimitDecision tryAcquire(@NotNull RateLimitKey key, @NotNull RateLimit limit);
}
