package com.sca4d.auth.passkey.exception;

/** Passkey 认证的依赖或数据故障，例如凭据存储返回结果不符合预期。 */
public class AuthPasskeyException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public AuthPasskeyException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public AuthPasskeyException(String message) {
        super(message);
    }
}
