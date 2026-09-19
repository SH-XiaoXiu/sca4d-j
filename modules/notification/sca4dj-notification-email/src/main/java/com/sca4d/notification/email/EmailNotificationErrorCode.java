package com.sca4d.notification.email;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.NotNull;

/** Email 通知配置、模板和投递使用的稳定错误码。 */
@ErrorCodeRange(start = 3000, end = 3099)
public enum EmailNotificationErrorCode implements ErrorCode {
    /** SMTP 配置不存在。 */
    SENDER_NOT_FOUND("3000", ErrorCategory.NOT_FOUND, "error.notification-email.sender-not-found"),
    /** SMTP 配置字段或认证组合无效。 */
    SENDER_INVALID("3001", ErrorCategory.VALIDATION, "error.notification-email.sender-invalid"),
    /** SMTP 配置名称或启用状态与已有配置冲突。 */
    SENDER_CONFLICT("3002", ErrorCategory.CONFLICT, "error.notification-email.sender-conflict"),
    /** Email 模板不存在。 */
    TEMPLATE_NOT_FOUND("3003", ErrorCategory.NOT_FOUND, "error.notification-email.template-not-found"),
    /** Email 模板字段、Locale 或表达式无效。 */
    TEMPLATE_INVALID("3004", ErrorCategory.VALIDATION, "error.notification-email.template-invalid"),
    /** Email 模板编码与已有模板冲突。 */
    TEMPLATE_CONFLICT("3005", ErrorCategory.CONFLICT, "error.notification-email.template-conflict"),
    /** 当前通知缺少启用的 SMTP 配置、模板或有效接收地址。 */
    DELIVERY_UNAVAILABLE("3006", ErrorCategory.UNAVAILABLE, "error.notification-email.delivery-unavailable"),
    /** SMTP 连接、认证或发送失败。 */
    SMTP_FAILED("3007", ErrorCategory.UNAVAILABLE, "error.notification-email.smtp-failed");

    private final @NotNull String code;

    private final @NotNull ErrorCategory category;

    private final @NotNull String messageKey;

    EmailNotificationErrorCode(
            @NotNull String code,
            @NotNull ErrorCategory category,
            @NotNull String messageKey) {
        this.code = code;
        this.category = category;
        this.messageKey = messageKey;
    }

    @Override
    public @NotNull String code() {
        return code;
    }

    @Override
    public @NotNull ErrorCategory category() {
        return category;
    }

    @Override
    public @NotNull String messageKey() {
        return messageKey;
    }
}
