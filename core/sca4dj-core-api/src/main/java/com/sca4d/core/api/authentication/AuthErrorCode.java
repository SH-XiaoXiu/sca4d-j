package com.sca4d.core.api.authentication;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;

/** 表示认证与会话操作产生的固定错误码。 */
@ErrorCodeRange(start = 2100, end = 2199)
public enum AuthErrorCode implements ErrorCode {
    /** 认证材料或账号状态不允许建立登录会话。 */
    AUTHENTICATION_FAILED("2100", ErrorCategory.AUTHENTICATION, "error.auth.authentication-failed");

    private final String code;

    private final ErrorCategory category;

    private final String messageKey;

    AuthErrorCode(String code, ErrorCategory category, String messageKey) {
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
