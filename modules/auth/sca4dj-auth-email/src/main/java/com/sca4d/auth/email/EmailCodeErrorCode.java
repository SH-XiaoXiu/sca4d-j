package com.sca4d.auth.email;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.NotNull;

/** Email Code Challenge 签发、消费和认证使用的稳定错误码。 */
@ErrorCodeRange(start = 2400, end = 2499)
public enum EmailCodeErrorCode implements ErrorCode {
    /** Challenge 不存在、过期、耗尽、已消费或不属于当前账号。 */
    CHALLENGE_UNAVAILABLE("2400", ErrorCategory.NOT_FOUND, "error.auth-email.challenge-unavailable"),
    /** 验证码格式错误、摘要不匹配或并发消费失败。 */
    CODE_REJECTED("2401", ErrorCategory.AUTHENTICATION, "error.auth-email.code-rejected"),
    /** Challenge 在重发间隔内已经签发。 */
    RESEND_TOO_SOON("2402", ErrorCategory.CONFLICT, "error.auth-email.resend-too-soon");

    private final @NotNull String code;

    private final @NotNull ErrorCategory category;

    private final @NotNull String messageKey;

    EmailCodeErrorCode(
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
