package com.sca4d.notification.inbox.exception;

/** 站内信通知的依赖或数据故障，例如模板或渠道配置缺失。 */
public class NotificationInboxException extends RuntimeException {

    /**
     * 创建带失败说明和原始原因的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     * @param cause 原始异常
     */
    public NotificationInboxException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建只带失败说明的技术异常。
     *
     * @param message 失败说明，不包含密钥、令牌或完整外部响应
     */
    public NotificationInboxException(String message) {
        super(message);
    }
}
