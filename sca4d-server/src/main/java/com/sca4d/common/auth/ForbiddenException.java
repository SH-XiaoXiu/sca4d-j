package com.sca4d.common.auth;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import org.jetbrains.annotations.NotNull;

/**
 * 无权限异常。
 */
public class ForbiddenException extends BizException {

    /**
     * 创建无权限异常。
     */
    public ForbiddenException() {
        super(CommonErrorCode.FORBIDDEN);
    }

    /**
     * 创建带具体错误码的无权限异常。
     *
     * @param errorCode 已注册业务错误码
     * @param messageArgs 消息模板参数
     */
    public ForbiddenException(
            @NotNull BizErrorCode errorCode, @NotNull Object... messageArgs) {
        super(errorCode, messageArgs);
    }
}
