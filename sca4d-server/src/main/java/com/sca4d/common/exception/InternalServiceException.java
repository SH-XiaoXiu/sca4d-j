package com.sca4d.common.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 内部服务异常。
 *
 * <p>用于数据库、缓存、配置、内部状态等服务端问题。该异常的原始消息不应返回前端。
 */
public class InternalServiceException extends RuntimeException {

    private final ResponseCode responseCode;

    public InternalServiceException(@NotNull String message) {
        this(message, null);
    }

    public InternalServiceException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.responseCode = ResponseCode.INTERNAL_SERVICE_ERROR;
    }

    public @NotNull ResponseCode getResponseCode() {
        return responseCode;
    }

    public int getCode() {
        return responseCode.getCode();
    }
}
