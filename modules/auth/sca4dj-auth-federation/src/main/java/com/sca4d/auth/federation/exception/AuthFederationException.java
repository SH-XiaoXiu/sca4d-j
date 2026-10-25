package com.sca4d.auth.federation.exception;

/** 外部身份联合认证的依赖或数据故障，例如外部服务响应或加密数据异常。 */
public class AuthFederationException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public AuthFederationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public AuthFederationException(String message) {
        super(message);
    }
}
