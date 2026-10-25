package com.sca4d.messaging.exception;

/** 消息模块的运行时故障，例如出箱记录写入后无法读取。 */
public class MessagingException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public MessagingException(String message) {
        super(message);
    }
}
