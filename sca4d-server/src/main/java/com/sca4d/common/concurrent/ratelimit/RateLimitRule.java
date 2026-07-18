package com.sca4d.common.concurrent.ratelimit;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/**
 * 限流规则。
 */
public class RateLimitRule {

    private final long capacity;
    private final long refillTokens;
    private final Duration window;

    /**
     * 创建限流规则。
     *
     * @param capacity 桶允许积累的最大令牌数
     * @param refillTokens 每个窗口补充的令牌数
     * @param window 贪婪补充算法使用的时间窗口
     */
    public RateLimitRule(long capacity, long refillTokens, @NotNull Duration window) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("限流容量必须大于 0");
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("限流补充令牌数必须大于 0");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("限流窗口必须大于 0");
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.window = window;
    }

    /** @return 桶允许积累的最大令牌数 */
    public long getCapacity() {
        return capacity;
    }

    /** @return 每个窗口补充的令牌数 */
    public long getRefillTokens() {
        return refillTokens;
    }

    /** @return 贪婪补充算法使用的时间窗口 */
    public @NotNull Duration getWindow() {
        return window;
    }

    /**
     * 计算 Bucket4j 配置版本，用于注解规则变化后的隐式配置替换。
     *
     * @return 配置版本
     */
    public long configurationVersion() {
        return Integer.toUnsignedLong(Objects.hash(capacity, refillTokens, window)) + 1;
    }
}
