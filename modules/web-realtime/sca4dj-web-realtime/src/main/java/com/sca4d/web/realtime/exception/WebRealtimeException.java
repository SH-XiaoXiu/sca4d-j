package com.sca4d.web.realtime.exception;

/** 实时推送模块的运行时故障，例如连接状态或路径配置异常。 */
public class WebRealtimeException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public WebRealtimeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public WebRealtimeException(String message) {
        super(message);
    }
}
