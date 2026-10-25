package com.sca4d.auth.sms;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.NotNull;

/** SMS Code Challenge 签发、消费和认证使用的稳定错误码。 */
@ErrorCodeRange(start = 1300, end = 1399)
public enum SmsCodeErrorCode implements ErrorCode {
    CHALLENGE_UNAVAILABLE("1300", ErrorCategory.NOT_FOUND, "error.auth-sms.challenge-unavailable"),
    CODE_REJECTED("1301", ErrorCategory.AUTHENTICATION, "error.auth-sms.code-rejected"),
    RESEND_TOO_SOON("1302", ErrorCategory.CONFLICT, "error.auth-sms.resend-too-soon"),
    /** 短信验证码挑战标识发生冲突，请重新获取验证码 */
    CHALLENGE_ID_CONFLICT("1303", ErrorCategory.CONFLICT, "error.auth-sms.challenge-id-conflict"),
    /** 短信验证码已经过期，请重新获取 */
    CHALLENGE_EXPIRED("1304", ErrorCategory.CONFLICT, "error.auth-sms.challenge-expired"),
    /** 短信验证码挑战已变化或不可用，请重新获取 */
    CHALLENGE_CHANGED("1305", ErrorCategory.CONFLICT, "error.auth-sms.challenge-changed");

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
