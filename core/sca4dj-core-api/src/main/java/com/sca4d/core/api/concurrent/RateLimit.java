package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.utils.DurationUtils;

import java.time.Duration;

/** 指定时间窗口内允许的最大调用次数。 */
public class RateLimit {

    /** 声明限流键使用的稳定维度。 */
    public enum Dim {
        GLOBAL,
        IP,
        UA,
        SUBJECT,
        ACCOUNT;

        /** 返回该维度在限流键中的稳定名称。 */
        public TreeCode code() {
            return TreeCode.ofSegment(name());
        }
    }

    private final Duration window;

    private final long permits;

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
     * 窗口内允许次数。
     *
     * @return 窗口内允许次数
     */
    public long permits() {
        return permits;
    }

    /**
     * 时间窗口。
     *
     * @return 时间窗口
     */
    public Duration window() {
        return window;
    }

}
