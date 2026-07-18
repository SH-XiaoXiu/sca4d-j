package com.sca4d.common.concurrent.ratelimit;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/**
 * 请求限流异常。
 */
public class RateLimitException extends BizException {

    private final long retryAfterSeconds;

    /**
     * 创建限流异常。
     *
     * @param errorCode 固定限流业务错误码
     * @param retryAfterSeconds 建议重试等待秒数
     */
    public RateLimitException(@NotNull BizErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        if (errorCode.getCategory() != ResponseCode.TOO_MANY_REQUESTS) {
            throw new IllegalArgumentException("RateLimitException 只接受 TOO_MANY_REQUESTS 错误码");
        }
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    /**
     * 获取建议重试等待秒数。
     *
     * @return 重试等待秒数
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
