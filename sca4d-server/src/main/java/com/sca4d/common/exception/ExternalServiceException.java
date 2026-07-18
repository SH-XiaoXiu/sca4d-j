package com.sca4d.common.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 外部服务异常。
 *
 * <p>用于第三方接口、中间件外部依赖、云服务等调用失败。该异常的原始消息不应返回前端。
 */
public class ExternalServiceException extends RuntimeException {

    /** 外部服务注册名；由接入方动态扩展，不是框架内置有限集合。 */
    private final String provider;
    private final ResponseCode responseCode;

    public ExternalServiceException(@NotNull String provider, @NotNull String message) {
        this(provider, message, null);
    }

    public ExternalServiceException(@NotNull String provider, @NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.responseCode = ResponseCode.EXTERNAL_SERVICE_ERROR;
    }

    public @NotNull String getProvider() {
        return provider;
    }

    public @NotNull ResponseCode getResponseCode() {
        return responseCode;
    }

    public int getCode() {
        return responseCode.getCode();
    }
}
