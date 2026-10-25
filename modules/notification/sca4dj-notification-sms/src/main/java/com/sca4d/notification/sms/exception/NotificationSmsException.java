package com.sca4d.notification.sms.exception;

/** 短信通知的依赖或数据故障，例如短信服务商绑定缺失。 */
public class NotificationSmsException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public NotificationSmsException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public NotificationSmsException(String message) {
        super(message);
    }
}
