package com.sca4d.identity.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.identity.IdentityProviderScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 读取和替换标准 OAuth2 端点、Client、scope 与 Profile JSONPath 配置。 */
public interface OAuth2ProviderService {

    /** @return 指定 OAuth2 Provider 的安全配置；尚未配置时抛出业务异常 */
    @NotNull
    OAuth2ProviderConfiguration configuration(
            @NotNull IdentityProviderScope scope, @NotNull Long providerId);

    /**
     * 创建或完整替换停用 Provider 的 OAuth2 配置。
     *
     * @param clientSecret 新 Client Secret；更新时为 null 表示保留现有密文
     * @return 已保存配置及推进后的父 Provider 版本
     */
    @NotNull
    OAuth2ProviderConfiguration replace(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull String authorizationUri,
            @NotNull String tokenUri,
            @NotNull String profileUri,
            @Nullable String auxiliaryProfileUri,
            @NotNull String clientId,
            @Nullable String clientSecret,
            @NotNull List<String> scopes,
            @NotNull OAuth2ProfileMapping profileMapping,
            long version);

    /** @return 使用当前已保存 JSONPath 映射给定主 Profile 和辅助 Profile 的预览结果 */
    @NotNull
    OAuth2ProfilePreview preview(
            @NotNull IdentityProviderScope scope,
            @NotNull Long providerId,
            @NotNull JsonNode profile,
            @Nullable JsonNode auxiliaryProfile);
}
