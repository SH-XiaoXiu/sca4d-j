package com.sca4d.auth.x509.exception;

/** X.509 证书认证的依赖或数据故障，例如证书解析失败。 */
public class AuthX509Exception extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public AuthX509Exception(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public AuthX509Exception(String message) {
        super(message);
    }
}
