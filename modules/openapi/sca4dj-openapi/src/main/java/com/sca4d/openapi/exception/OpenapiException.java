package com.sca4d.openapi.exception;

/** OpenAPI 认证端点的运行时故障，例如令牌处理方法注册失败。 */
public class OpenapiException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public OpenapiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public OpenapiException(String message) {
        super(message);
    }
}
