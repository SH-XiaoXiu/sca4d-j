package com.sca4d.core.api.concurrent;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.error.CoreErrorCode;

/** 表示同一幂等键已经用于不同的方法参数。 */
public final class IdempotencyKeyConflictException extends BusinessException {

    /** 创建不允许继续执行当前业务调用的幂等键冲突。 */
    public IdempotencyKeyConflictException() {
        super(CoreErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }
}
