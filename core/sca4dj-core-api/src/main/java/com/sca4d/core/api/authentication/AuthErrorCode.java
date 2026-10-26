package com.sca4d.core.api.authentication;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;

/** 表示认证与会话操作产生的固定错误码。 */
@ErrorCodeRange(start = 2100, end = 2199)
public enum AuthErrorCode implements ErrorCode {
    /** 认证材料或账号状态不允许建立登录会话。 */
    AUTHENTICATION_FAILED("2100", ErrorCategory.AUTHENTICATION, "error.auth.authentication-failed"),
    /** 认证尝试标识发生冲突，请重新发起认证。 */
    AUTH_ATTEMPT_ID_CONFLICT("2101", ErrorCategory.CONFLICT, "error.auth.attempt-id-conflict"),
    /** 认证尝试不存在或已过期，请重新发起认证。 */
    AUTH_ATTEMPT_EXPIRED("2102", ErrorCategory.CONFLICT, "error.auth.attempt-expired"),
    /** 认证尝试已经满足，不能继续提交。 */
    AUTH_ATTEMPT_SATISFIED("2103", ErrorCategory.CONFLICT, "error.auth.attempt-satisfied"),
    /** 认证尝试尚未满足当前动作策略。 */
    AUTH_ATTEMPT_INCOMPLETE("2104", ErrorCategory.CONFLICT, "error.auth.attempt-incomplete"),
    /** 认证方法当前不可用。 */
    AUTH_METHOD_UNAVAILABLE("2105", ErrorCategory.UNAVAILABLE, "error.auth.method-unavailable");

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
