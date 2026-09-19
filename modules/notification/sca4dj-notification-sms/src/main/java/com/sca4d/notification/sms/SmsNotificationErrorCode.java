package com.sca4d.notification.sms;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.NotNull;

/** SMS Provider 与传输绑定管理使用的稳定错误码。 */
@ErrorCodeRange(start = 3200, end = 3299)
public enum SmsNotificationErrorCode implements ErrorCode {
    /** SMS Provider 不存在。 */
    PROVIDER_NOT_FOUND("3200", ErrorCategory.NOT_FOUND, "error.notification-sms.provider-not-found"),
    /** SMS Provider 字段、类型或凭据引用无效。 */
    PROVIDER_INVALID("3201", ErrorCategory.VALIDATION, "error.notification-sms.provider-invalid"),
    /** SMS Provider 编码、启用状态或绑定关系发生冲突。 */
    PROVIDER_CONFLICT("3202", ErrorCategory.CONFLICT, "error.notification-sms.provider-conflict"),
    /** SMS Provider 模板绑定不存在。 */
    BINDING_NOT_FOUND("3203", ErrorCategory.NOT_FOUND, "error.notification-sms.binding-not-found"),
    /** SMS Provider 模板绑定的路由、Locale 或参数映射无效。 */
    BINDING_INVALID("3204", ErrorCategory.VALIDATION, "error.notification-sms.binding-invalid"),
    /** SMS Provider 模板绑定与已有绑定或当前状态冲突。 */
    BINDING_CONFLICT("3205", ErrorCategory.CONFLICT, "error.notification-sms.binding-conflict");

    private final @NotNull String code;

    private final @NotNull ErrorCategory category;

    private final @NotNull String messageKey;

    SmsNotificationErrorCode(
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
