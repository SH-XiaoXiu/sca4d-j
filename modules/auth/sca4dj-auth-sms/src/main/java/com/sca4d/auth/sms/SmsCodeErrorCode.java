package com.sca4d.auth.sms;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.NotNull;

/** SMS Code Challenge 签发、消费和认证使用的稳定错误码。 */
@ErrorCodeRange(start = 3400, end = 3499)
public enum SmsCodeErrorCode implements ErrorCode {
    CHALLENGE_UNAVAILABLE("3400", ErrorCategory.NOT_FOUND, "error.auth-sms.challenge-unavailable"),
    CODE_REJECTED("3401", ErrorCategory.AUTHENTICATION, "error.auth-sms.code-rejected"),
    RESEND_TOO_SOON("3402", ErrorCategory.CONFLICT, "error.auth-sms.resend-too-soon");

    private final @NotNull String code;

    private final @NotNull ErrorCategory category;

    private final @NotNull String messageKey;

    SmsCodeErrorCode(String code, ErrorCategory category, String messageKey) {
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
