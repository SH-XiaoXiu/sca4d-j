package com.sca4d.authentication.error;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/**
 * 认证模块错误码。
 */
@CodeRange(start = 2000, end = 2099)
public enum AuthenticationErrorCode implements BizErrorCode {
    /** 账号或密码错误。 */
    BAD_CREDENTIAL(2000, "error.authentication.bad-credential", ResponseCode.UNAUTHORIZED),
    /** 登录会话不存在或不属于当前用户。 */
    SESSION_NOT_FOUND(2001, "error.authentication.session-not-found", ResponseCode.NOT_FOUND),
    /** 认证不存在、已过期或不属于当前浏览器。 */
    AUTHENTICATION_UNAVAILABLE(2002, "error.authentication.unavailable", ResponseCode.NOT_FOUND),
    /** 认证当前等待的输入不接受该操作。 */
    AUTHENTICATION_ACTIVITY_INVALID(2003, "error.authentication.activity-invalid", ResponseCode.CONFLICT),
    /** 认证已被另一个请求更新。 */
    AUTHENTICATION_CHANGED(2004, "error.authentication.changed", ResponseCode.CONFLICT),
    /** 当前认证目的不允许使用该认证方式或操作。 */
    AUTHENTICATION_PURPOSE_UNAVAILABLE(2005, "error.authentication.purpose-unavailable", ResponseCode.BAD_REQUEST),
    /** GLOBAL/TENANT 与 tenantCode 组合无效。 */
    AUTHENTICATION_SCOPE_INVALID(2006, "error.authentication.scope-invalid", ResponseCode.BAD_REQUEST),
    /** 当前部署或 Account 没有可用的第二认证因素。 */
    SECOND_FACTOR_UNAVAILABLE(2007, "error.authentication.second-factor-unavailable", ResponseCode.CONFLICT),
    /** 邮件验证码错误或已经失效。 */
    EMAIL_CODE_INVALID(2008, "error.authentication.email-code-invalid", ResponseCode.UNAUTHORIZED),
    /** 邮件验证码 Challenge 不存在、已过期或不属于当前认证。 */
    EMAIL_CODE_CHALLENGE_UNAVAILABLE(2009, "error.authentication.email-code-challenge-unavailable", ResponseCode.NOT_FOUND),
    /** 邮件验证码 Challenge 已被另一个请求推进。 */
    EMAIL_CODE_CHALLENGE_CHANGED(2010, "error.authentication.email-code-challenge-changed", ResponseCode.CONFLICT),
    /** 当前 Account 无法建立会话。 */
    ACCOUNT_SESSION_UNAVAILABLE(2011, "error.authentication.account-session-unavailable", ResponseCode.UNAUTHORIZED),
    /** 会话授权数据不完整。 */
    SESSION_AUTHORIZATION_INCOMPLETE(
            2012, "error.authentication.session-authorization-incomplete", ResponseCode.UNAUTHORIZED),
    /** Sa-Token 未能创建会话。 */
    SESSION_CREATE_FAILED(2013, "error.authentication.session-create-failed", ResponseCode.UNAUTHORIZED),
    /** 会话缺少 sessionId。 */
    SESSION_ID_MISSING(2014, "error.authentication.session-id-missing", ResponseCode.UNAUTHORIZED),
    /** 会话 Account 与 token 不匹配。 */
    SESSION_ACCOUNT_MISMATCH(2015, "error.authentication.session-account-mismatch", ResponseCode.UNAUTHORIZED),
    /** DEFAULT Principal 不接受 tenantId。 */
    ME_DEFAULT_TENANT_ID_FORBIDDEN(2016, "error.me.default-tenant-id-forbidden", ResponseCode.FORBIDDEN),
    /** 当前用户接口不接受外部 Principal ID。 */
    ME_EXTERNAL_PRINCIPAL_ID_FORBIDDEN(
            2017, "error.me.external-principal-id-forbidden", ResponseCode.FORBIDDEN),
    /** PLATFORM Principal 不接受 tenantId。 */
    ME_PLATFORM_TENANT_ID_FORBIDDEN(2018, "error.me.platform-tenant-id-forbidden", ResponseCode.FORBIDDEN),
    /** 当前 Subject 没有请求类型的 Principal。 */
    ME_PRINCIPAL_UNAVAILABLE(2019, "error.me.principal-unavailable", ResponseCode.FORBIDDEN),
    /** 当前用户接口不允许 SYSTEM Principal。 */
    ME_SYSTEM_PRINCIPAL_FORBIDDEN(2020, "error.me.system-principal-forbidden", ResponseCode.FORBIDDEN),
    /** TENANT Principal 选择缺少 tenantId。 */
    ME_TENANT_ID_REQUIRED(2021, "error.me.tenant-id-required", ResponseCode.FORBIDDEN),
    /** 同一次认证在重发间隔内重复请求邮件验证码。 */
    EMAIL_CODE_RESEND_LATER(
            2022, "error.authentication.email-code-resend-later", ResponseCode.TOO_MANY_REQUESTS),
    /** 恢复码格式错误、无效或已经使用。 */
    RECOVERY_CODE_INVALID(
            2023, "error.authentication.recovery-code-invalid", ResponseCode.UNAUTHORIZED),
    /** TOTP 验证码格式错误、无效或当前时间步已经使用。 */
    TOTP_CODE_INVALID(
            2024, "error.authentication.totp-code-invalid", ResponseCode.UNAUTHORIZED),
    /** Passkey assertion 不存在、已过期或不属于当前认证和浏览器。 */
    PASSKEY_ASSERTION_UNAVAILABLE(
            2025, "error.authentication.passkey-assertion-unavailable", ResponseCode.NOT_FOUND),
    /** Passkey assertion 已被另一个请求消费。 */
    PASSKEY_ASSERTION_CHANGED(
            2026, "error.authentication.passkey-assertion-changed", ResponseCode.CONFLICT),
    /** WebAuthn assertion 签名、RP、Origin、UV、计数或备份标志无效。 */
    PASSKEY_ASSERTION_INVALID(
            2027, "error.authentication.passkey-assertion-invalid", ResponseCode.UNAUTHORIZED);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    AuthenticationErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
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
