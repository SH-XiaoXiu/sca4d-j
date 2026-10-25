package com.sca4d.auth.hmac.exception;

/** HMAC 认证的依赖或数据故障，例如 Redis 返回的失败计数无效。 */
public class AuthHmacException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public AuthHmacException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public AuthHmacException(String message) {
        super(message);
    }
}
