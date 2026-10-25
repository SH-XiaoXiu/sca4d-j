package com.sca4d.gateway.loki.exception;

/** Loki 日志推送的依赖故障，例如日志服务响应异常。 */
public class GatewayLokiException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public GatewayLokiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public GatewayLokiException(String message) {
        super(message);
    }
}
