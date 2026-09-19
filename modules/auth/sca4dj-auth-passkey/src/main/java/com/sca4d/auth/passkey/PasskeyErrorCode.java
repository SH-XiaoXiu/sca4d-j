package com.sca4d.auth.passkey;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;

/** 表示 Passkey ceremony、认证和 Credential 生命周期产生的固定错误码。 */
@ErrorCodeRange(start = 2500, end = 2599)
public enum PasskeyErrorCode implements ErrorCode {
    /** WebAuthn assertion 无法验证。 */
    ASSERTION_REJECTED("2500", ErrorCategory.AUTHENTICATION, "error.auth-passkey.assertion-rejected"),
    /** Registration 或 assertion ceremony 已过期或已经消费。 */
    CEREMONY_UNAVAILABLE("2501", ErrorCategory.NOT_FOUND, "error.auth-passkey.ceremony-unavailable"),
    /** 账号已经达到 App 配置的 Passkey Credential 上限。 */
    CREDENTIAL_LIMIT_REACHED("2502", ErrorCategory.CONFLICT, "error.auth-passkey.credential-limit-reached"),
    /** 指定 Passkey Credential 不存在或已经删除。 */
    CREDENTIAL_UNAVAILABLE("2503", ErrorCategory.NOT_FOUND, "error.auth-passkey.credential-unavailable"),
    /** WebAuthn registration 无法验证或没有满足 discoverable 与 UV 要求。 */
    REGISTRATION_REJECTED("2504", ErrorCategory.VALIDATION, "error.auth-passkey.registration-rejected");

    private final String code;

    private final ErrorCategory category;

    private final String messageKey;

    PasskeyErrorCode(String code, ErrorCategory category, String messageKey) {
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
