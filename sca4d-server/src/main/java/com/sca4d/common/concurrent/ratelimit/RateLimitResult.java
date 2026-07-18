package com.sca4d.common.concurrent.ratelimit;

import org.jetbrains.annotations.NotNull;

/**
 * 限流消费结果。
 */
public class RateLimitResult {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final boolean allowed;
    private final long remainingTokens;
    private final long retryAfterNanos;

    /**
     * 创建一次 Bucket4j 消费判定快照。
     *
     * @param allowed 当前请求是否获得令牌
     * @param remainingTokens 判定后的剩余令牌数
     * @param retryAfterNanos 被拒绝时距离可再次消费的纳秒数
     */
    private RateLimitResult(boolean allowed, long remainingTokens, long retryAfterNanos) {
        this.allowed = allowed;
        this.remainingTokens = remainingTokens;
        this.retryAfterNanos = retryAfterNanos;
    }

    /**
     * 创建允许结果。
     *
     * @param remainingTokens 剩余令牌数
     * @return 限流结果
     */
    public static @NotNull RateLimitResult allowed(long remainingTokens) {
        return new RateLimitResult(true, remainingTokens, 0);
    }

    /**
     * 创建拒绝结果。
     *
     * @param remainingTokens 剩余令牌数
     * @param retryAfterNanos 建议等待纳秒数
     * @return 限流结果
     */
    public static @NotNull RateLimitResult rejected(long remainingTokens, long retryAfterNanos) {
        return new RateLimitResult(false, remainingTokens, Math.max(1, retryAfterNanos));
    }

    /** @return 当前令牌消费是否成功 */
    public boolean isAllowed() {
        return allowed;
    }

    /** @return 本次消费判定后的剩余令牌数 */
    public long getRemainingTokens() {
        return remainingTokens;
    }

    /** @return Bucket4j 计算的精确建议等待纳秒数，允许结果为零 */
    public long getRetryAfterNanos() {
        return retryAfterNanos;
    }

    /** @return 向上取整后可写入 Retry-After 的等待秒数，允许结果为零 */
    public long getRetryAfterSeconds() {
        if (retryAfterNanos <= 0) {
            return 0;
        }
        return Math.max(1, Math.ceilDiv(retryAfterNanos, NANOS_PER_SECOND));
    }
}
