package com.sca4d.auth.password;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;

/** 表示 Password 认证和 Credential 生命周期产生的固定错误码。 */
@ErrorCodeRange(start = 2200, end = 2299)
public enum PasswordErrorCode implements ErrorCode {
    /** 用户名、账号状态或密码材料不允许认证。 */
    REJECTED("2200", ErrorCategory.AUTHENTICATION, "error.auth-password.rejected"),
    /** 账号已经存在密码凭据。 */
    CREDENTIAL_EXISTS("2201", ErrorCategory.CONFLICT, "error.auth-password.credential-exists"),
    /** 账号没有密码凭据。 */
    CREDENTIAL_NOT_FOUND("2202", ErrorCategory.NOT_FOUND, "error.auth-password.credential-not-found");

    private final String code;

    private final ErrorCategory category;

    private final String messageKey;

    PasswordErrorCode(String code, ErrorCategory category, String messageKey) {
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
