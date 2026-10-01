package com.sca4d.app.server.authentication.view;

import com.sca4d.auth.federation.FederatedProviderConfiguration;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 返回联邦 Provider 协议配置摘要，永远不返回 Client Secret。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 序列化响应。
public class FederatedProviderSettingsView {
    private final String issuer;

    private final String clientId;

    private final boolean clientSecretConfigured;

    private final String authorizationEndpoint;

    private final String tokenEndpoint;

    private final String userInfoEndpoint;

    private final String jwksEndpoint;

    private final String clientAuthentication;

    private final List<String> scopes;

    private final Map<String, String> profileMapping;

    private final Map<String, String> authorizationParameters;

    private final @NotNull Version revision;

    private FederatedProviderSettingsView(
            @NotNull FederatedProviderConfiguration configuration,
            @NotNull Version revision) {
        issuer = configuration.issuer();
        clientId = configuration.clientId();
        clientSecretConfigured = configuration.clientSecretConfigured();
        authorizationEndpoint = configuration.authorizationEndpoint();
        tokenEndpoint = configuration.tokenEndpoint();
        userInfoEndpoint = configuration.userInfoEndpoint();
        jwksEndpoint = configuration.jwksEndpoint();
        clientAuthentication = configuration.clientAuthentication().name();
        scopes = configuration.scopes();
        profileMapping = configuration.profileMapping();
        authorizationParameters = configuration.authorizationParameters();
        this.revision = revision;
    }

    /**
     * @param configuration 模块返回的已脱敏协议配置
     * @param revision 生成该配置的 Provider 版本
     * @return HTTP 安全投影
     */
    public static @NotNull FederatedProviderSettingsView from(
            @NotNull FederatedProviderConfiguration configuration,
            @NotNull Version revision) {
        return new FederatedProviderSettingsView(configuration, revision);
    }

    /** @return issuer */
    public String getIssuer() {
        return issuer;
    }

    /** @return Client ID */
    public String getClientId() {
        return clientId;
    }

    /** @return 是否已经配置 Client Secret */
    public boolean isClientSecretConfigured() {
        return clientSecretConfigured;
    }

    /** @return 授权端点 */
    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    /** @return Token 端点 */
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    /** @return UserInfo 端点 */
    public String getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    /** @return JWKS 端点 */
    public String getJwksEndpoint() {
        return jwksEndpoint;
    }

    /** @return Client 认证方式 */
    public String getClientAuthentication() {
        return clientAuthentication;
    }

    /** @return 作用域 */
    public List<String> getScopes() {
        return new ArrayList<>(scopes);
    }

    /** @return 资料映射 */
    public Map<String, String> getProfileMapping() {
        return new LinkedHashMap<>(profileMapping);
    }

    /** @return 附加授权参数 */
    public Map<String, String> getAuthorizationParameters() {
        return new LinkedHashMap<>(authorizationParameters);
    }

    /** @return 生成当前完整协议配置表示的 Provider 版本 */
    @ETag
    public @NotNull Version revision() {
        return revision;
    }
}
