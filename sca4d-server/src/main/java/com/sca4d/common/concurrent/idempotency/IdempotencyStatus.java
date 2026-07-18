package com.sca4d.common.concurrent.idempotency;

/**
 * 幂等记录状态。
 */
public enum IdempotencyStatus {
    /** 请求正在处理中。 */
    PROCESSING,
    /** 请求已成功完成，可重放响应。 */
    SUCCEEDED
}
