package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.utils.DurationUtils;

import java.time.Duration;

/** 指定时间窗口内允许的最大调用次数。 */
public final class RateLimit {

    private final long permits;

    private final Duration window;

    /**
     * 创建限流要求。
     *
     * @param permits 窗口内允许次数
     * @param window 时间窗口
     */
    public RateLimit(long permits, Duration window) {
        if (permits < 1) {
            throw new IllegalArgumentException("允许次数必须为正数");
        }
        this.window = DurationUtils.requirePositive(window, "限流窗口");
        this.permits = permits;
    }

    /**
     * 返回窗口内允许次数。
     *
     * @return 窗口内允许次数
     */
    public long permits() {
        return permits;
    }

    /**
     * 返回时间窗口。
     *
     * @return 时间窗口
     */
    public Duration window() {
        return window;
    }
}
