package com.sca4d.auth.federation;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 保存 OIDC 或 OAuth2 Provider 的协议端点、作用域和资料映射。 */
public final class FederatedProviderConfiguration {

    private final FederatedProviderProtocol protocol;

    private final String issuer;

    private final String clientId;

    private final String clientSecret;

    private final String authorizationEndpoint;

    private final String tokenEndpoint;

    private final String userInfoEndpoint;

    private final String jwksEndpoint;

    private final FederatedClientAuthentication clientAuthentication;

    private final List<String> scopes;

    private final Map<String, String> profileMapping;

    private final Map<String, String> authorizationParameters;

    private boolean clientSecretConfigured;

    /** 创建协议配置；Secret 只在保存或发送到上游时短暂存在内存中。 */
    public FederatedProviderConfiguration(
            FederatedProviderProtocol protocol,
            @Nullable String issuer,
            @Nullable String clientId,
            @Nullable String clientSecret,
            @Nullable String authorizationEndpoint,
            @Nullable String tokenEndpoint,
            @Nullable String userInfoEndpoint,
            @Nullable String jwksEndpoint,
            FederatedClientAuthentication clientAuthentication,
            @Nullable List<String> scopes,
            @Nullable Map<String, String> profileMapping,
            @Nullable Map<String, String> authorizationParameters) {
        this.protocol = Objects.requireNonNull(protocol, "Provider 协议不能为空");
        this.issuer = clean(issuer);
        this.clientId = clean(clientId);
        this.clientSecret = clean(clientSecret);
        this.authorizationEndpoint = clean(authorizationEndpoint);
        this.tokenEndpoint = clean(tokenEndpoint);
        this.userInfoEndpoint = clean(userInfoEndpoint);
        this.jwksEndpoint = clean(jwksEndpoint);
        this.clientAuthentication = Objects.requireNonNull(clientAuthentication, "Client 认证方式不能为空");
        this.scopes = scopes == null
                ? List.of()
                : scopes.stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        this.profileMapping = profileMapping == null ? Map.of() : Map.copyOf(profileMapping);
        this.authorizationParameters = authorizationParameters == null ? Map.of() : Map.copyOf(authorizationParameters);
        this.clientSecretConfigured = this.clientSecret != null;
    }

    /** 创建可以脱敏返回但仍保留 Secret 配置状态的协议配置。 */
    public FederatedProviderConfiguration(
            FederatedProviderProtocol protocol,
            @Nullable String issuer,
            @Nullable String clientId,
            @Nullable String clientSecret,
            @Nullable String authorizationEndpoint,
            @Nullable String tokenEndpoint,
            @Nullable String userInfoEndpoint,
            @Nullable String jwksEndpoint,
            FederatedClientAuthentication clientAuthentication,
            @Nullable List<String> scopes,
            @Nullable Map<String, String> profileMapping,
            @Nullable Map<String, String> authorizationParameters,
            boolean clientSecretConfigured) {
        this(
                protocol,
                issuer,
                clientId,
                clientSecret,
                authorizationEndpoint,
                tokenEndpoint,
                userInfoEndpoint,
                jwksEndpoint,
                clientAuthentication,
                scopes,
                profileMapping,
                authorizationParameters);
        this.clientSecretConfigured = clientSecretConfigured || this.clientSecret != null;
    }

    /** @return 协议 */
    public FederatedProviderProtocol protocol() {
        return protocol;
    }

    /** @return OIDC Issuer */
    public String issuer() {
        return issuer;
    }

    /** @return Client ID */
    public String clientId() {
        return clientId;
    }

    /** @return Client Secret；读取数据库时为空 */
    public String clientSecret() {
        return clientSecret;
    }

    /** @return 授权端点 */
    public String authorizationEndpoint() {
        return authorizationEndpoint;
    }

    /** @return Token 端点 */
    public String tokenEndpoint() {
        return tokenEndpoint;
    }

    /** @return UserInfo 或资料端点 */
    public String userInfoEndpoint() {
        return userInfoEndpoint;
    }

    /** @return JWKS 端点 */
    public String jwksEndpoint() {
        return jwksEndpoint;
    }

    /** @return Token 端点 Client 认证方式 */
    public FederatedClientAuthentication clientAuthentication() {
        return clientAuthentication;
    }

    /** @return 请求作用域 */
    public List<String> scopes() {
        return scopes;
    }

    /** @return OAuth2 Profile 字段到 JSONPath 的映射 */
    public Map<String, String> profileMapping() {
        return profileMapping;
    }

    /** @return 经过白名单校验的附加授权参数 */
    public Map<String, String> authorizationParameters() {
        return authorizationParameters;
    }

    /** @return 是否提供了 Client Secret */
    public boolean clientSecretConfigured() {
        return clientSecretConfigured;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
