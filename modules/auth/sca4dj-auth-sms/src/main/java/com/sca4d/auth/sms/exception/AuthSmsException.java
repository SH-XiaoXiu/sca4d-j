package com.sca4d.auth.sms.exception;

/** 短信验证码认证的依赖或数据故障，例如 Redis 中挑战数据损坏或编码失败。 */
public class AuthSmsException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public AuthSmsException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public AuthSmsException(String message) {
        super(message);
    }
}
