package com.sca4d.notification.error;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/**
 * 通知模块错误码。
 */
@CodeRange(start = 3000, end = 3099)
public enum NotificationErrorCode implements BizErrorCode {
    /** 通知不存在或不属于当前用户。 */
    NOTIFICATION_NOT_FOUND(3000, "error.notification.not-found", ResponseCode.NOT_FOUND),
    /** 通知正文为空。 */
    NOTIFICATION_CONTENT_REQUIRED(3001, "error.notification.content-required", ResponseCode.BAD_REQUEST),
    /** 接收人的 Subject 和 Principal 只提供了其中一个。 */
    NOTIFICATION_RECIPIENT_INCOMPLETE(3002, "error.notification.recipient-incomplete", ResponseCode.BAD_REQUEST),
    /** 单个通知没有提供接收人。 */
    NOTIFICATION_RECIPIENT_REQUIRED(3003, "error.notification.recipient-required", ResponseCode.BAD_REQUEST),
    /** 批量通知的接收人集合为空。 */
    NOTIFICATION_RECIPIENTS_REQUIRED(3004, "error.notification.recipients-required", ResponseCode.BAD_REQUEST),
    /** 发送人的 Subject 和 Principal 只提供了其中一个。 */
    NOTIFICATION_SENDER_INCOMPLETE(3005, "error.notification.sender-incomplete", ResponseCode.BAD_REQUEST),
    /** 通知标题为空。 */
    NOTIFICATION_TITLE_REQUIRED(3006, "error.notification.title-required", ResponseCode.BAD_REQUEST);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    NotificationErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    @Override
    public @NotNull ResponseCode getCategory() {
        return category;
    }
}
