package com.sca4d.notification.sms.aliyun.exception;

/** 阿里云短信通道的依赖故障，例如 SDK 客户端创建或查询结果异常。 */
public class NotificationSmsAliyunException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public NotificationSmsAliyunException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public NotificationSmsAliyunException(String message) {
        super(message);
    }
}
