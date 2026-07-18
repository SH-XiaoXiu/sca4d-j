package com.sca4d.identity;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/** IdentityProvider 配置、作用域和启停操作使用的错误码。 */
@CodeRange(start = 2300, end = 2399)
public enum IdentityProviderErrorCode implements BizErrorCode {
    /** 指定作用域中不存在该 IdentityProvider。 */
    PROVIDER_NOT_FOUND(2300, "error.identity-provider.not-found", ResponseCode.NOT_FOUND),
    /** IdentityProvider 已被其他请求修改。 */
    PROVIDER_CHANGED(2301, "error.identity-provider.changed", ResponseCode.PRECONDITION_FAILED),
    /** 同一作用域内已经存在相同 Provider code。 */
    PROVIDER_CODE_EXISTS(2302, "error.identity-provider.code-exists", ResponseCode.CONFLICT),
    /** Provider code 或显示名称不符合配置要求。 */
    PROVIDER_CONFIGURATION_INVALID(2303, "error.identity-provider.configuration-invalid", ResponseCode.BAD_REQUEST),
    /** 当前协议子表尚未包含启用所需的全部配置。 */
    PROVIDER_PROTOCOL_CONFIGURATION_REQUIRED(2304, "error.identity-provider.protocol-configuration-required", ResponseCode.CONFLICT),
    /** 启用中的 IdentityProvider 必须先停用才能删除。 */
    PROVIDER_ACTIVE(2305, "error.identity-provider.active", ResponseCode.CONFLICT),
    /** 已经关联 Account 的 IdentityProvider 不能删除。 */
    PROVIDER_IN_USE(2306, "error.identity-provider.in-use", ResponseCode.CONFLICT),
    /** 修改或删除 IdentityProvider 时没有提供 If-Match。 */
    PROVIDER_IF_MATCH_REQUIRED(2307, "error.identity-provider.if-match-required", ResponseCode.PRECONDITION_REQUIRED),
    /** 请求的协议子资源与 Provider 创建时固定的协议不一致。 */
    PROVIDER_PROTOCOL_MISMATCH(2308, "error.identity-provider.protocol-mismatch", ResponseCode.BAD_REQUEST),
    /** 指定 Provider 尚未保存对应协议配置。 */
    PROVIDER_PROTOCOL_CONFIGURATION_NOT_FOUND(2309, "error.identity-provider.protocol-configuration-not-found", ResponseCode.NOT_FOUND),
    /** 首次保存协议配置时没有提供必需的 Client Secret 或服务账号密码。 */
    PROVIDER_SECRET_REQUIRED(2310, "error.identity-provider.secret-required", ResponseCode.BAD_REQUEST),
    /** 无法从已保存 Issuer 读取 OIDC Discovery 文档。 */
    PROVIDER_DISCOVERY_FAILED(2311, "error.identity-provider.discovery-failed", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** OIDC Discovery 文档缺少必填字段、包含无效端点或 issuer 不匹配。 */
    PROVIDER_DISCOVERY_INVALID(2312, "error.identity-provider.discovery-invalid", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** Provider 中的 external subject 已绑定其他 Account。 */
    EXTERNAL_IDENTITY_ALREADY_LINKED(2313, "error.identity-provider.external-identity-already-linked", ResponseCode.CONFLICT),
    /** Account 已绑定当前 Provider 的另一个 external subject。 */
    EXTERNAL_ACCOUNT_ALREADY_LINKED(2314, "error.identity-provider.external-account-already-linked", ResponseCode.CONFLICT),
    /** ExternalIdentity、Provider、Account 或 Subject 当前不能参与认证。 */
    EXTERNAL_IDENTITY_UNAVAILABLE(2315, "error.identity-provider.external-identity-unavailable", ResponseCode.UNAUTHORIZED),
    /** 上游 external subject 或资料字段缺失、过长或组合无效。 */
    EXTERNAL_IDENTITY_PROFILE_INVALID(2316, "error.identity-provider.external-identity-profile-invalid", ResponseCode.BAD_REQUEST),
    /** OIDC state 不存在、过期、已消费或不属于当前浏览器。 */
    OIDC_AUTHORIZATION_UNAVAILABLE(2317, "error.identity-provider.oidc-authorization-unavailable", ResponseCode.BAD_REQUEST),
    /** 当前作用域中没有使用指定 code 的 ACTIVE OIDC Provider。 */
    OIDC_PROVIDER_UNAVAILABLE(2318, "error.identity-provider.oidc-provider-unavailable", ResponseCode.NOT_FOUND),
    /** OIDC Token Endpoint 或 UserInfo Endpoint 请求失败。 */
    OIDC_UPSTREAM_FAILED(2319, "error.identity-provider.oidc-upstream-failed", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** ID Token、nonce、签名、Issuer、Audience 或 UserInfo subject 无效。 */
    OIDC_TOKEN_INVALID(2320, "error.identity-provider.oidc-token-invalid", ResponseCode.UNAUTHORIZED),
    /** 未关联 external subject 且 Provider 禁止 JIT 注册。 */
    EXTERNAL_REGISTRATION_DISABLED(2321, "error.identity-provider.external-registration-disabled", ResponseCode.FORBIDDEN),
    /** OIDC callback 缺少授权码或上游返回拒绝结果。 */
    OIDC_AUTHORIZATION_REJECTED(2322, "error.identity-provider.oidc-authorization-rejected", ResponseCode.UNAUTHORIZED),
    /** OAuth2 state 不存在、过期、已消费或不属于当前浏览器。 */
    OAUTH2_AUTHORIZATION_UNAVAILABLE(2323, "error.identity-provider.oauth2-authorization-unavailable", ResponseCode.BAD_REQUEST),
    /** 当前作用域中没有使用指定 code 的 ACTIVE OAuth2 Provider。 */
    OAUTH2_PROVIDER_UNAVAILABLE(2324, "error.identity-provider.oauth2-provider-unavailable", ResponseCode.NOT_FOUND),
    /** OAuth2 Token 或 Profile 端点请求失败。 */
    OAUTH2_UPSTREAM_FAILED(2325, "error.identity-provider.oauth2-upstream-failed", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** OAuth2 Profile 无法按当前 JSONPath 配置得到有效 external subject。 */
    OAUTH2_PROFILE_INVALID(2326, "error.identity-provider.oauth2-profile-invalid", ResponseCode.UNAUTHORIZED),
    /** OAuth2 callback 缺少授权码或上游返回拒绝结果。 */
    OAUTH2_AUTHORIZATION_REJECTED(2327, "error.identity-provider.oauth2-authorization-rejected", ResponseCode.UNAUTHORIZED),
    /** 当前作用域中没有使用指定 code 的 ACTIVE LDAP Provider。 */
    LDAP_PROVIDER_UNAVAILABLE(2328, "error.identity-provider.ldap-provider-unavailable", ResponseCode.NOT_FOUND),
    /** LDAP 目录连接、搜索或服务账号 bind 失败。 */
    LDAP_UPSTREAM_FAILED(2329, "error.identity-provider.ldap-upstream-failed", ResponseCode.EXTERNAL_SERVICE_ERROR),
    /** LDAP 用户不存在、密码错误或目录记录无法映射。 */
    LDAP_CREDENTIAL_INVALID(2330, "error.identity-provider.ldap-credential-invalid", ResponseCode.UNAUTHORIZED),
    /** 解除 ExternalIdentity 关联时没有提供 If-Match。 */
    EXTERNAL_IDENTITY_IF_MATCH_REQUIRED(2331, "error.identity-provider.external-identity-if-match-required", ResponseCode.PRECONDITION_REQUIRED),
    /** ExternalIdentity 已被其他请求修改。 */
    EXTERNAL_IDENTITY_CHANGED(2332, "error.identity-provider.external-identity-changed", ResponseCode.PRECONDITION_FAILED),
    /** 解除 ExternalIdentity 会使 Account 不再具备主登录方式。 */
    EXTERNAL_IDENTITY_REMOVAL_WOULD_LOCK_ACCOUNT(2333, "error.identity-provider.external-identity-removal-would-lock-account", ResponseCode.CONFLICT);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    IdentityProviderErrorCode(int code, @NotNull String messageKey, @NotNull ResponseCode category) {
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
