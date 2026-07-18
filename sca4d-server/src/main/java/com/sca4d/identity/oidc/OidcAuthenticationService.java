package com.sca4d.identity.oidc;

import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.common.auth.AccountScopeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 列出可用 OIDC Provider，并创建和完成 Authorization Code + PKCE 请求。 */
public interface OidcAuthenticationService {

    /** @return 指定 Account 作用域中全部 ACTIVE OIDC Provider */
    @NotNull
    List<OidcAvailableProvider> providers(
            @NotNull AccountScopeType scopeType, @Nullable String tenantCode);

    /**
     * 为等待主要认证方式的认证创建一次 OIDC Authorization Request。
     *
     * @param authenticationId 当前 AuthenticationProcess UUID
     * @param browserHash 当前浏览器 Cookie 摘要
     * @param providerCode 当前作用域内不可变的 Provider code
     * @return 浏览器跳转地址和 state 过期时间
     */
    @NotNull
    OidcAuthorizationStart authorize(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull String providerCode);

    /**
     * 单次消费 state，交换授权码，验证 ID Token，并推进对应 AuthenticationProcess。
     *
     * @param authorizationId OpenID Provider 原样返回的 state
     * @param code Authorization Code；上游拒绝时为 null
     * @param error OpenID Provider 返回的错误编码；成功时为 null
     * @param browserHash 当前浏览器 Cookie 摘要
     * @return 已完成 OIDC 验证的认证；LINKING 会直接返回 completed
     */
    @NotNull
    AuthenticationProcess callback(
            @NotNull String authorizationId,
            @Nullable String code,
            @Nullable String error,
            @NotNull String browserHash);
}
