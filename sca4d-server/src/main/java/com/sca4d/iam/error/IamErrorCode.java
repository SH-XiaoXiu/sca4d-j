package com.sca4d.iam.error;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/**
 * IAM 模块错误码。
 */
@CodeRange(start = 2100, end = 2199)
public enum IamErrorCode implements BizErrorCode {
    /** Subject 不存在。 */
    SUBJECT_NOT_FOUND(2100, "error.iam.subject-not-found", ResponseCode.NOT_FOUND),
    /** Subject 已经绑定同类登录凭证。 */
    CREDENTIAL_ALREADY_BOUND(2101, "error.iam.credential-already-bound", ResponseCode.CONFLICT),
    /** Subject 尚未绑定指定登录凭证。 */
    CREDENTIAL_NOT_BOUND(2102, "error.iam.credential-not-bound", ResponseCode.NOT_FOUND),
    /** Principal 不存在。 */
    PRINCIPAL_NOT_FOUND(2103, "error.iam.principal-not-found", ResponseCode.NOT_FOUND),
    /** Principal 当前不可用。 */
    PRINCIPAL_UNAVAILABLE(2104, "error.iam.principal-unavailable", ResponseCode.FORBIDDEN),
    /** Subject 已经拥有指定类型的 Principal。 */
    PRINCIPAL_EXISTS(2112, "error.iam.principal-exists", ResponseCode.CONFLICT),
    /** 登录账号未填写。 */
    CREDENTIAL_ACCOUNT_REQUIRED(2114, "error.iam.credential-account-required", ResponseCode.BAD_REQUEST),
    /** 登录账号超过允许长度。 */
    CREDENTIAL_ACCOUNT_TOO_LONG(2115, "error.iam.credential-account-too-long", ResponseCode.BAD_REQUEST),
    /** 登录密码未填写。 */
    CREDENTIAL_PASSWORD_REQUIRED(2116, "error.iam.credential-password-required", ResponseCode.BAD_REQUEST),
    /** 登录凭证类型与内容不匹配。 */
    CREDENTIAL_TYPE_MISMATCH(2117, "error.iam.credential-type-mismatch", ResponseCode.BAD_REQUEST),
    /** 登录凭证类型不受支持。 */
    CREDENTIAL_TYPE_UNSUPPORTED(2118, "error.iam.credential-type-unsupported", ResponseCode.BAD_REQUEST),
    /** 登录凭证不存在。 */
    CREDENTIAL_NOT_FOUND(2119, "error.iam.credential-not-found", ResponseCode.NOT_FOUND),
    /** PLATFORM Principal 不属于当前 Subject。 */
    FOREIGN_PLATFORM_PRINCIPAL(2120, "error.iam.foreign-platform-principal", ResponseCode.FORBIDDEN),
    /** 当前接口禁止使用 PLATFORM Principal。 */
    PLATFORM_PRINCIPAL_NOT_ALLOWED(2122, "error.iam.platform-principal-not-allowed", ResponseCode.FORBIDDEN),
    /** 当前 Subject 没有可用的 PLATFORM Principal。 */
    PLATFORM_PRINCIPAL_UNAVAILABLE(2123, "error.iam.platform-principal-unavailable", ResponseCode.FORBIDDEN),
    /** Account 不存在。 */
    ACCOUNT_NOT_FOUND(2124, "error.iam.account-not-found", ResponseCode.NOT_FOUND),
    /** 登录标识未填写。 */
    IDENTIFIER_REQUIRED(2125, "error.iam.identifier-required", ResponseCode.BAD_REQUEST),
    /** 登录标识不符合用户名或邮箱规则。 */
    IDENTIFIER_INVALID(2126, "error.iam.identifier-invalid", ResponseCode.BAD_REQUEST),
    /** 当前身份作用域已经存在相同登录标识。 */
    IDENTIFIER_ALREADY_USED(2127, "error.iam.identifier-already-used", ResponseCode.CONFLICT),
    /** Account 使用邮件验证前必须存在当前邮箱。 */
    ACCOUNT_EMAIL_REQUIRED(2128, "error.iam.account-email-required", ResponseCode.CONFLICT),
    /** 邮箱 Contact 已在验证码签发后变化。 */
    CONTACT_CHANGED(2129, "error.iam.contact-changed", ResponseCode.CONFLICT),
    /** Subject 已被其他请求修改。 */
    SUBJECT_CHANGED(2145, "error.iam.subject-changed", ResponseCode.CONFLICT),
    /** Subject 状态已被其他请求修改。 */
    SUBJECT_STATUS_CHANGED(2146, "error.iam.subject-status-changed", ResponseCode.CONFLICT),
    /** Principal 状态已被其他请求修改。 */
    PRINCIPAL_STATUS_CHANGED(2149, "error.iam.principal-status-changed", ResponseCode.CONFLICT),
    /** 登录凭证已被其他请求修改。 */
    CREDENTIAL_CHANGED(2155, "error.iam.credential-changed", ResponseCode.CONFLICT),
    /** Account 策略已被其他请求修改。 */
    ACCOUNT_CHANGED(2156, "error.iam.account-changed", ResponseCode.PRECONDITION_FAILED),
    /** TOTP enrollment 不存在、已过期或不属于当前浏览器。 */
    TOTP_ENROLLMENT_UNAVAILABLE(2157, "error.iam.totp-enrollment-unavailable", ResponseCode.NOT_FOUND),
    /** TOTP enrollment 已被另一个请求消费。 */
    TOTP_ENROLLMENT_CHANGED(2158, "error.iam.totp-enrollment-changed", ResponseCode.CONFLICT),
    /** Account 启用第二因子前必须存在已验证邮箱或 ACTIVE TOTP。 */
    ACCOUNT_SECOND_FACTOR_REQUIRED(
            2159, "error.iam.account-second-factor-required", ResponseCode.CONFLICT),
    /** Account 未启用第二因子，不能签发恢复码。 */
    ACCOUNT_RECOVERY_CODES_UNAVAILABLE(
            2178, "error.iam.account-recovery-codes-unavailable", ResponseCode.CONFLICT),
    /** 停用或删除账号会让现有授权主体失去登录入口。 */
    ACCOUNT_ACCESS_WOULD_BE_LOST(
            2180, "error.iam.account-access-would-be-lost", ResponseCode.CONFLICT),
    /** 删除 TOTP 会让要求第二因子的 Account 不再具有独立登录因素。 */
    TOTP_REMOVAL_WOULD_LOCK_ACCOUNT(
            2160, "error.iam.totp-removal-would-lock-account", ResponseCode.CONFLICT),
    /** TOTP Credential 的 If-Match 缺失。 */
    TOTP_CREDENTIAL_IF_MATCH_REQUIRED(
            2161, "error.iam.totp-credential-if-match-required", ResponseCode.PRECONDITION_REQUIRED),
    /** TOTP Credential 版本、类型、归属或删除状态已经变化。 */
    TOTP_CREDENTIAL_CHANGED(
            2162, "error.iam.totp-credential-changed", ResponseCode.PRECONDITION_FAILED),
    /** Passkey registration 不存在、已过期或不属于当前浏览器。 */
    PASSKEY_REGISTRATION_UNAVAILABLE(
            2163, "error.iam.passkey-registration-unavailable", ResponseCode.NOT_FOUND),
    /** Passkey registration 已被另一个请求消费。 */
    PASSKEY_REGISTRATION_CHANGED(
            2164, "error.iam.passkey-registration-changed", ResponseCode.CONFLICT),
    /** Passkey Credential 的 If-Match 缺失。 */
    PASSKEY_CREDENTIAL_IF_MATCH_REQUIRED(
            2165, "error.iam.passkey-credential-if-match-required", ResponseCode.PRECONDITION_REQUIRED),
    /** Passkey Credential 版本、类型、归属或删除状态已经变化。 */
    PASSKEY_CREDENTIAL_CHANGED(
            2166, "error.iam.passkey-credential-changed", ResponseCode.PRECONDITION_FAILED),
    /** 删除 Passkey 会让要求第二因子的 Account 不再具有可用认证方式。 */
    PASSKEY_REMOVAL_WOULD_LOCK_ACCOUNT(
            2167, "error.iam.passkey-removal-would-lock-account", ResponseCode.CONFLICT),
    /** WebAuthn credential ID 已经绑定其他 Credential。 */
    PASSKEY_CREDENTIAL_ALREADY_REGISTERED(
            2168, "error.iam.passkey-credential-already-registered", ResponseCode.CONFLICT),
    /** 新生成的 WebAuthn user handle 与其他 Account 冲突。 */
    PASSKEY_USER_HANDLE_CONFLICT(
            2169, "error.iam.passkey-user-handle-conflict", ResponseCode.CONFLICT),
    /** DEFAULT Profile 不存在。 */
    DEFAULT_PROFILE_NOT_FOUND(2170, "error.iam.default-profile-not-found", ResponseCode.NOT_FOUND),
    /** DEFAULT Profile 已被其他请求修改。 */
    DEFAULT_PROFILE_CHANGED(2171, "error.iam.default-profile-changed", ResponseCode.CONFLICT),
    /** DEFAULT Profile 头像文件类型不受支持。 */
    DEFAULT_PROFILE_AVATAR_FILE_TYPE_INVALID(
            2172, "error.iam.default-profile-avatar-file-type-invalid", ResponseCode.BAD_REQUEST),
    /** Subject 默认展示名未填写。 */
    SUBJECT_DISPLAY_NAME_REQUIRED(2173, "error.iam.subject-display-name-required", ResponseCode.BAD_REQUEST),
    /** 默认头像文件和上传授权没有同时提供。 */
    DEFAULT_PROFILE_AVATAR_BINDING_INCOMPLETE(
            2174, "error.iam.default-profile-avatar-binding-incomplete", ResponseCode.BAD_REQUEST),
    /** 设置默认头像时缺少上传文件所属 Principal。 */
    DEFAULT_PROFILE_AVATAR_PRINCIPAL_REQUIRED(
            2175, "error.iam.default-profile-avatar-principal-required", ResponseCode.BAD_REQUEST),
    /** Account 安全写操作缺少 If-Match。 */
    ACCOUNT_IF_MATCH_REQUIRED(
            2176, "error.iam.account-if-match-required", ResponseCode.PRECONDITION_REQUIRED),
    /** Credential 写操作缺少 If-Match。 */
    CREDENTIAL_IF_MATCH_REQUIRED(
            2177, "error.iam.credential-if-match-required", ResponseCode.PRECONDITION_REQUIRED),
    /** DEFAULT Profile 头像资源标识无效。 */
    DEFAULT_PROFILE_RESOURCE_ID_INVALID(
            2179, "error.iam.default-profile-resource-id-invalid", ResponseCode.BAD_REQUEST);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    IamErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    @Override
    public @NotNull ResponseCode getCategory() {
        return category;
    }
}
