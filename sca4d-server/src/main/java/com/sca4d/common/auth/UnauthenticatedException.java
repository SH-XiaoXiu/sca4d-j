package com.sca4d.common.auth;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import org.jetbrains.annotations.NotNull;

/**
 * 未登录或登录失效异常。
 */
public class UnauthenticatedException extends BizException {

    /**
     * 创建未登录异常。
     */
    public UnauthenticatedException() {
        super(CommonErrorCode.UNAUTHORIZED);
    }

    /**
     * 创建带具体认证错误码的未登录异常。
     *
     * @param errorCode 已注册认证错误码
     * @param messageArgs 消息模板参数
     */
    public UnauthenticatedException(
            @NotNull BizErrorCode errorCode, @NotNull Object... messageArgs) {
        super(errorCode, messageArgs);
    }
}
