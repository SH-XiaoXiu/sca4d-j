package com.sca4d.core.api.error.exception;

import com.sca4d.core.api.error.ErrorCode;

import java.util.Objects;

/** 携带错误码并由外围转换为 HTTP 响应的业务异常。 */
public class BusinessException extends RuntimeException {

    /** 当前异常对应的错误码。 */
    private final ErrorCode errorCode;

    /** 国际化消息解析使用的参数。 */
    private final Object[] messageArguments;

    /**
     * 创建不带根因和消息参数的业务异常。
     *
     * @param errorCode 错误码，不能为 null
     * @throws NullPointerException errorCode 为 null 时抛出
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /**
     * 创建带可选根因的业务异常，并复制消息参数数组。
     *
     * @param errorCode 错误码，不能为 null
     * @param cause 原始异常，可以为 null；不会被包装或改写，仅保存为 cause
     * @param messageArguments 国际化消息参数，不能为 null；传入数组的后续修改不影响本异常
     * @throws NullPointerException errorCode 或 messageArguments 为 null 时抛出
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
