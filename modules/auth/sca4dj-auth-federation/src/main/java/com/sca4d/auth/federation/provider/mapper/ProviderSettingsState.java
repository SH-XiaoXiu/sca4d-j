package com.sca4d.auth.federation.provider.mapper;

import com.sca4d.auth.federation.FederatedClientAuthentication;
import com.sca4d.auth.federation.FederatedProviderProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 保存 MyBatis 从 Provider 与配置表连接结果读取的持久化状态。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class ProviderSettingsState {

    private final @NotNull FederatedProviderProtocol protocol;

    private final @Nullable String issuer;

    private final @Nullable String clientId;

    private final @Nullable String secretKeyId;

    private final byte @Nullable [] secretCiphertext;

    private final @Nullable String authorizationEndpoint;

    private final @Nullable String tokenEndpoint;

    private final @Nullable String userInfoEndpoint;

    private final @Nullable String jwksEndpoint;

    private final @NotNull FederatedClientAuthentication clientAuthentication;

    private final @NotNull String scopes;

    private final @NotNull String profileMapping;

    private final @NotNull String authorizationParameters;

    /** 创建 Provider 配置持久化状态。 */
    public ProviderSettingsState(
            @NotNull FederatedProviderProtocol protocol,
            @Nullable String issuer,
            @Nullable String clientId,
            @Nullable String secretKeyId,
            byte @Nullable [] secretCiphertext,
            @Nullable String authorizationEndpoint,
            @Nullable String tokenEndpoint,
            @Nullable String userInfoEndpoint,
            @Nullable String jwksEndpoint,
            @NotNull FederatedClientAuthentication clientAuthentication,
            @NotNull String scopes,
            @NotNull String profileMapping,
            @NotNull String authorizationParameters) {
        this.protocol = protocol;
        this.issuer = issuer;
        this.clientId = clientId;
        this.secretKeyId = secretKeyId;
        this.secretCiphertext = secretCiphertext == null ? null : secretCiphertext.clone();
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.userInfoEndpoint = userInfoEndpoint;
        this.jwksEndpoint = jwksEndpoint;
        this.clientAuthentication = clientAuthentication;
        this.scopes = scopes;
        this.profileMapping = profileMapping;
        this.authorizationParameters = authorizationParameters;
    }

    /** @return 协议类型 */
    public @NotNull FederatedProviderProtocol protocol() {
        return protocol;
    }

    /** @return OIDC issuer */
    public @Nullable String issuer() {
        return issuer;
    }

    /** @return Client ID */
    public @Nullable String clientId() {
        return clientId;
    }

    /** @return 加密 Secret 的密钥标识 */
    public @Nullable String secretKeyId() {
        return secretKeyId;
    }

    /** @return 加密 Secret 密文副本 */
    public byte @Nullable [] secretCiphertext() {
        return secretCiphertext == null ? null : secretCiphertext.clone();
    }

    /** @return 授权端点 */
    public @Nullable String authorizationEndpoint() {
        return authorizationEndpoint;
    }

    /** @return Token 端点 */
    public @Nullable String tokenEndpoint() {
        return tokenEndpoint;
    }

    /** @return UserInfo 端点 */
    public @Nullable String userInfoEndpoint() {
        return userInfoEndpoint;
    }

    /** @return JWKS 端点 */
    public @Nullable String jwksEndpoint() {
        return jwksEndpoint;
    }

    /** @return Client 认证方式 */
    public @NotNull FederatedClientAuthentication clientAuthentication() {
        return clientAuthentication;
    }

    /** @return JSONB 作用域数组 */
    public @NotNull String scopes() {
        return scopes;
    }

    /** @return JSONB 资料映射对象 */
    public @NotNull String profileMapping() {
        return profileMapping;
    }

    /** @return JSONB 授权参数对象 */
    public @NotNull String authorizationParameters() {
        return authorizationParameters;
    }
}
