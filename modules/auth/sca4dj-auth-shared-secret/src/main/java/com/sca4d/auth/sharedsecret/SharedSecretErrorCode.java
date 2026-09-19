package com.sca4d.auth.sharedsecret;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;

/** 表示 Shared Secret 认证和凭据生命周期产生的固定错误码。 */
@ErrorCodeRange(start = 2800, end = 2899)
public enum SharedSecretErrorCode implements ErrorCode {
    /** Account、凭据状态或提交的 Secret 不允许认证。 */
    REJECTED("2800", ErrorCategory.AUTHENTICATION, "error.auth-shared-secret.rejected"),
    /** Account 尚未建立 Shared Secret 凭据。 */
    CREDENTIAL_NOT_FOUND("2801", ErrorCategory.NOT_FOUND, "error.auth-shared-secret.credential-not-found");

    private final String code;

    private final ErrorCategory category;

    private final String messageKey;

    SharedSecretErrorCode(String code, ErrorCategory category, String messageKey) {
        this.code = code;
        this.category = category;
        this.messageKey = messageKey;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }
}
