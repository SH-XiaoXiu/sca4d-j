package com.sca4d.auth.federation;

import com.sca4d.core.api.error.ErrorCategory;
import com.sca4d.core.api.error.ErrorCode;
import com.sca4d.core.api.error.ErrorCodeRange;
import org.jetbrains.annotations.NotNull;

/** 联邦 Provider 配置和登录流程使用的错误码。 */
@ErrorCodeRange(start = 2600, end = 2699)
public enum FederatedErrorCode implements ErrorCode {
    /** 授权状态不存在、已消费或已过期。 */
    AUTHORIZATION_UNAVAILABLE("2600", ErrorCategory.AUTHENTICATION, "error.auth-federation.authorization-unavailable"),
    /** 上游拒绝了用户授权。 */
    AUTHORIZATION_REJECTED("2601", ErrorCategory.AUTHENTICATION, "error.auth-federation.authorization-rejected"),
    /** Provider 当前没有可用配置。 */
    PROVIDER_UNAVAILABLE("2602", ErrorCategory.NOT_FOUND, "error.auth-federation.provider-unavailable"),
    /** OIDC 身份断言或 OAuth2 用户资料无效。 */
    PROFILE_INVALID("2603", ErrorCategory.AUTHENTICATION, "error.auth-federation.profile-invalid"),
    /** 上游网络或响应失败。 */
    UPSTREAM_FAILED("2604", ErrorCategory.UNAVAILABLE, "error.auth-federation.upstream-failed"),
    /** 外部身份没有本地账号且 Provider 禁止自动创建。 */
    UNLINKED_IDENTITY("2605", ErrorCategory.AUTHENTICATION, "error.auth-federation.unlinked-identity"),
    /** Provider 配置不能用于启用或登录。 */
    CONFIGURATION_INVALID("2606", ErrorCategory.VALIDATION, "error.auth-federation.configuration-invalid"),
    /** Provider 编码已被其他未删除 Provider 使用。 */
    PROVIDER_CONFLICT("2607", ErrorCategory.CONFLICT, "error.auth-federation.provider-conflict"),
    /** 外部身份已绑定其他账号或违反唯一约束。 */
    IDENTITY_CONFLICT("2608", ErrorCategory.CONFLICT, "error.auth-federation.identity-conflict");

    private final @NotNull String code;

    private final @NotNull ErrorCategory category;

    private final @NotNull String messageKey;

    FederatedErrorCode(@NotNull String code, @NotNull ErrorCategory category, @NotNull String messageKey) {
        this.code = code;
        this.category = category;
        this.messageKey = messageKey;
    }

    @Override
    public @NotNull String code() {
        return code;
    }

    @Override
    public @NotNull ErrorCategory category() {
        return category;
    }

    @Override
    public @NotNull String messageKey() {
        return messageKey;
    }
}
