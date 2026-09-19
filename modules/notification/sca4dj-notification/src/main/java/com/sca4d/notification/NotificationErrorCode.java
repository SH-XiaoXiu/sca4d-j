package com.sca4d.notification;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.NotNull;

/** 统一通知模板和渠道设置使用的稳定错误码。 */
@ErrorCodeRange(start = 3100, end = 3199)
public enum NotificationErrorCode implements ErrorCode {
    /** 统一通知模板不存在。 */
    TEMPLATE_NOT_FOUND("3100", ErrorCategory.NOT_FOUND, "error.notification.template-not-found"),
    /** 统一通知模板结构、Locale 或变量无效。 */
    TEMPLATE_INVALID("3101", ErrorCategory.VALIDATION, "error.notification.template-invalid"),
    /** 通知类型已经存在未删除模板。 */
    TEMPLATE_CONFLICT("3102", ErrorCategory.CONFLICT, "error.notification.template-conflict");

    private final @NotNull String code;

    private final @NotNull ErrorCategory category;

    private final @NotNull String messageKey;

    NotificationErrorCode(@NotNull String code, @NotNull ErrorCategory category, @NotNull String messageKey) {
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
