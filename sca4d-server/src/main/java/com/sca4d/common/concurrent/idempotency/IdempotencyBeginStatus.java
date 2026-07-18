package com.sca4d.common.concurrent.idempotency;

/**
 * 幂等开始处理状态。
 */
public enum IdempotencyBeginStatus {
    /** 已获得本次请求的处理权。 */
    ACQUIRED,
    /** 已存在可重放的成功结果。 */
    REPLAY,
    /** 同一请求仍在处理中。 */
    IN_PROGRESS,
    /** 幂等键已被不同请求使用。 */
    CONFLICT
}
