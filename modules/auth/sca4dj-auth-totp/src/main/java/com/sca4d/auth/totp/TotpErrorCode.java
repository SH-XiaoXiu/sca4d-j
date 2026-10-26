package com.sca4d.auth.totp;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;

/** TOTP 方法和 enrollment 生命周期使用的稳定错误码。 */
@ErrorCodeRange(start = 2300, end = 2399)
public enum TotpErrorCode implements ErrorCode {
    /** 验证码格式错误、无效或时间步早于已接受下限。 */
    CODE_REJECTED("2300", ErrorCategory.AUTHENTICATION, "error.auth-totp.code-rejected"),
    /** Enrollment 不存在、已过期、已消费或不属于目标账号。 */
    ENROLLMENT_UNAVAILABLE("2301", ErrorCategory.NOT_FOUND, "error.auth-totp.enrollment-unavailable"),
    /** Account 已经存在 TOTP 凭据。 */
    CREDENTIAL_EXISTS("2302", ErrorCategory.CONFLICT, "error.auth-totp.credential-exists"),
    /** Account 尚未设置 TOTP 凭据。 */
    CREDENTIAL_UNAVAILABLE("2303", ErrorCategory.NOT_FOUND, "error.auth-totp.credential-unavailable"),
    /** TOTP 绑定已经完成并被消费，不能重复使用。 */
    ENROLLMENT_CONSUMED("2304", ErrorCategory.CONFLICT, "error.auth-totp.enrollment-consumed");

    private final String code;

    private final ErrorCategory category;

    private final String messageKey;

    TotpErrorCode(String code, ErrorCategory category, String messageKey) {
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
