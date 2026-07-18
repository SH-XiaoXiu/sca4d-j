package com.sca4d.common.concurrent.idempotency;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;

/**
 * 幂等键冲突异常。
 */
public class IdempotencyKeyConflictException extends BizException {

    /** 创建同一操作和幂等 key 已绑定不同请求指纹的业务冲突。 */
    public IdempotencyKeyConflictException() {
        super(CommonErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }
}
