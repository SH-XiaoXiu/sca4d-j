package com.sca4d.common.concurrent.idempotency;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * 幂等请求正在处理中异常。
 */
public class IdempotencyInProgressException extends BizException {

    private final long retryAfterSeconds;

    /**
     * 创建同一提交意图仍由其他节点处理的冲突。
     *
     * @param retryAfter 服务端配置的建议等待时间，小于一秒时响应仍使用一秒
     */
    public IdempotencyInProgressException(@NotNull Duration retryAfter) {
        super(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
        this.retryAfterSeconds = Math.max(1, retryAfter.toSeconds());
    }

    /** @return 写入 HTTP Retry-After 响应头的正整数秒数 */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
