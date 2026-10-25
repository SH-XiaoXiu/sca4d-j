package com.sca4d.auth.hmac;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;

/** 表示 MAC 认证和 Key 生命周期产生的固定错误码。 */
@ErrorCodeRange(start = 1200, end = 1299)
public enum HmacErrorCode implements ErrorCode {
    /** Account、请求窗口、nonce、签名或凭据状态不允许认证。 */
    REJECTED("1200", ErrorCategory.AUTHENTICATION, "error.auth-hmac.rejected"),
    /** Account 尚未建立 MAC Credential。 */
    CREDENTIAL_NOT_FOUND("1201", ErrorCategory.NOT_FOUND, "error.auth-hmac.credential-not-found");

    private final String code;

    private final ErrorCategory category;

    private final String messageKey;

    HmacErrorCode(String code, ErrorCategory category, String messageKey) {
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
