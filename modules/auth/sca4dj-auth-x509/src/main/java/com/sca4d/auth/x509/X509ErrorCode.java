package com.sca4d.auth.x509;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;

/** 表示 X.509 认证和凭据生命周期产生的固定错误码。 */
@ErrorCodeRange(start = 3300, end = 3399)
public enum X509ErrorCode implements ErrorCode {
    /** Account、证书状态或传输验证结果不允许认证。 */
    REJECTED("3300", ErrorCategory.AUTHENTICATION, "error.auth-x509.rejected"),
    /** X.509 Credential 不存在或不属于目标 Account。 */
    CREDENTIAL_NOT_FOUND("3301", ErrorCategory.NOT_FOUND, "error.auth-x509.credential-not-found"),
    /** 公钥证书无法解析、已经过期或有效期不正确。 */
    INVALID_CERTIFICATE("3302", ErrorCategory.VALIDATION, "error.auth-x509.invalid-certificate"),
    /** 公钥证书已经由另一个 Credential 登记。 */
    CREDENTIAL_CONFLICT("3303", ErrorCategory.CONFLICT, "error.auth-x509.credential-conflict");

    private final String code;

    private final ErrorCategory category;

    private final String messageKey;

    X509ErrorCode(String code, ErrorCategory category, String messageKey) {
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
