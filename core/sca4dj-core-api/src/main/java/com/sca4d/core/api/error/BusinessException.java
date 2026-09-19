package com.sca4d.core.api.error;

import java.util.Objects;

/** 携带错误码并由外围转换为 HTTP 响应的业务异常。 */
public class BusinessException extends RuntimeException {

    /** 当前异常对应的错误码。 */
    private final ErrorCode errorCode;

    /** 国际化消息解析使用的参数。 */
    private final Object[] messageArguments;

    /**
     * 创建业务异常。
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /**
     * 创建带根因的业务异常。
     *
     * @param errorCode 错误码
     * @param cause 原始异常
     * @param messageArguments 国际化消息参数
     */
    public BusinessException(ErrorCode errorCode, Throwable cause, Object... messageArguments) {
        super(Objects.requireNonNull(errorCode, "错误码不能为空").code(), cause);
        this.errorCode = errorCode;
        this.messageArguments = Objects.requireNonNull(messageArguments, "错误消息参数不能为空").clone();
    }

    /**
     * 返回错误码。
     *
     * @return 错误码
     */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * 返回用于国际化消息解析的参数副本。
     *
     * @return 用于国际化消息解析的参数副本
     */
    public Object[] messageArguments() {
        return messageArguments.clone();
    }
}
