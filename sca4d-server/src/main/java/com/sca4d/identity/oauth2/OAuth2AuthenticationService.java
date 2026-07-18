package com.sca4d.identity.oauth2;

import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.common.auth.AccountScopeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 列出可用 OAuth2 Provider，并创建和完成 Authorization Code + PKCE 请求。 */
public interface OAuth2AuthenticationService {

    /** @return 指定 Account 作用域中全部 ACTIVE OAuth2 Provider */
    @NotNull
    List<OAuth2AvailableProvider> providers(
            @NotNull AccountScopeType scopeType, @Nullable String tenantCode);

    /** @return 浏览器跳转地址和 state 过期时间 */
    @NotNull
    OAuth2AuthorizationStart authorize(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull String providerCode);

    /** @return 已完成 OAuth2 验证的认证；LINKING 会直接返回 completed */
    @NotNull
    AuthenticationProcess callback(
            @NotNull String authorizationId,
            @Nullable String code,
            @Nullable String error,
            @NotNull String browserHash);
}
