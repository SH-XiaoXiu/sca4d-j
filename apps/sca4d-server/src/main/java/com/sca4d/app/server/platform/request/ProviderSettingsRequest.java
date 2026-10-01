package com.sca4d.app.server.platform.request;

import com.sca4d.auth.federation.FederatedClientAuthentication;
import com.sca4d.auth.federation.FederatedProviderConfiguration;
import com.sca4d.auth.federation.FederatedProviderProtocol;
import com.sca4d.core.spring.validation.Req;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 接收停用联邦 Provider 的 OIDC/OAuth2 协议配置，空 Secret 表示保留原值。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean getter 和 setter 绑定 HTTP 请求字段。
public class ProviderSettingsRequest {
    private String issuer;

    private String clientId;

    private String clientSecret;

    private String authorizationEndpoint;

    private String tokenEndpoint;

    private String userInfoEndpoint;

    private String jwksEndpoint;

    @Req.NotNull
    private FederatedClientAuthentication clientAuthentication = FederatedClientAuthentication.CLIENT_SECRET_POST;

    private List<String> scopes = new ArrayList<>();

    private Map<String, String> profileMapping = new LinkedHashMap<>();

    private Map<String, String> authorizationParameters = new LinkedHashMap<>();

    /** 创建空请求供 JSON 绑定。 */
    public ProviderSettingsRequest() {
    }

    /** @return issuer */
    public String getIssuer() {
        return issuer;
    }

    /** @param value issuer */
    public void setIssuer(String value) {
        issuer = value;
    }

    /** @return Client ID */
    public String getClientId() {
        return clientId;
    }

    /** @param value Client ID */
    public void setClientId(String value) {
        clientId = value;
    }

    /** @return Client Secret；为空时保留原值 */
    public String getClientSecret() {
        return clientSecret;
    }

    /** @param value Client Secret */
    public void setClientSecret(String value) {
        clientSecret = value;
    }

    /** @return 授权端点 */
    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    /** @param value 授权端点 */
    public void setAuthorizationEndpoint(String value) {
        authorizationEndpoint = value;
    }

    /** @return Token 端点 */
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    /** @param value Token 端点 */
    public void setTokenEndpoint(String value) {
        tokenEndpoint = value;
    }

    /** @return UserInfo 端点 */
    public String getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    /** @param value UserInfo 端点 */
    public void setUserInfoEndpoint(String value) {
        userInfoEndpoint = value;
    }

    /** @return JWKS 端点 */
    public String getJwksEndpoint() {
        return jwksEndpoint;
    }

    /** @param value JWKS 端点 */
    public void setJwksEndpoint(String value) {
        jwksEndpoint = value;
    }

    /** @return Client 认证方式 */
    public FederatedClientAuthentication getClientAuthentication() {
        return Objects.requireNonNull(clientAuthentication, "Client 认证方式不能为空");
    }

    /** @param value Client 认证方式 */
    public void setClientAuthentication(FederatedClientAuthentication value) {
        clientAuthentication = value;
    }

    /** @return 作用域 */
    public List<String> getScopes() {
        return new ArrayList<>(scopes);
    }

    /** @param value 作用域 */
    public void setScopes(List<String> value) {
        scopes = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    /** @return 资料映射 */
    public Map<String, String> getProfileMapping() {
        return new LinkedHashMap<>(profileMapping);
    }

    /** @param value 资料映射 */
    public void setProfileMapping(Map<String, String> value) {
        profileMapping = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    /** @return 附加授权参数 */
    public Map<String, String> getAuthorizationParameters() {
        return new LinkedHashMap<>(authorizationParameters);
    }

    /** @param value 附加授权参数 */
    public void setAuthorizationParameters(Map<String, String> value) {
        authorizationParameters = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    /** 转换为模块协议配置。 */
    public FederatedProviderConfiguration toConfiguration(FederatedProviderProtocol protocol) {
        return new FederatedProviderConfiguration(
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
    }
}
