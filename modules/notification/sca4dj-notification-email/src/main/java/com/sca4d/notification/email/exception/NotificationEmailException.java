package com.sca4d.notification.email.exception;

/** 邮件通知的依赖或数据故障，例如 SMTP 配置或发送结果异常。 */
public class NotificationEmailException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public NotificationEmailException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public NotificationEmailException(String message) {
        super(message);
    }
}
