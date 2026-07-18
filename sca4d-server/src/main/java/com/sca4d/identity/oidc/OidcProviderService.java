package com.sca4d.identity.oidc;

import com.sca4d.identity.IdentityProviderScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 读取和替换 OIDC Discovery、Client、scope 与加密 Client Secret 配置。 */
public interface OidcProviderService {

    /** @return 指定 OIDC Provider 的安全配置；尚未配置时抛出业务异常 */
    @NotNull
    OidcProviderConfiguration configuration(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId);

    /**
     * 从已保存 Issuer 读取 Discovery 文档，并校验返回 issuer 和认证所需端点。
     *
     * @return 当前上游返回的端点和协议支持范围
     */
    @NotNull
    OidcDiscovery discover(@NotNull IdentityProviderScope scope, @NotNull Long providerId);

    /**
     * 创建或完整替换停用 Provider 的 OIDC 配置。
     *
     * @param clientSecret 新 Client Secret；更新时为 null 表示保留现有密文
     * @return 已保存配置及推进后的父 Provider 版本
     */
    @NotNull
    OidcProviderConfiguration replace(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull String issuer,
            @NotNull String clientId,
            @Nullable String clientSecret,
            @NotNull List<String> scopes,
            long version);
}
