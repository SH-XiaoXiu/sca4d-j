package com.sca4d.common.concurrent.idempotency;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;

/**
 * 缺少幂等键异常。
 */
public class IdempotencyKeyRequiredException extends BizException {

    /** 创建受保护操作未提供客户端提交意图 key 的请求错误。 */
    public IdempotencyKeyRequiredException() {
        super(CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }
}
