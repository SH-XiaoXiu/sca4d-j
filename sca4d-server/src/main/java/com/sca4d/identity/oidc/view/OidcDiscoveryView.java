package com.sca4d.identity.oidc.view;

import com.sca4d.identity.oidc.OidcDiscovery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 管理端查看的 OIDC Discovery 端点和协议支持范围。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OidcDiscoveryView {

    private Long providerId;
    private String issuer;
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;
    private String jwksUri;
    private List<String> scopesSupported;
    private List<String> responseTypesSupported;
    private List<String> subjectTypesSupported;
    private List<String> idTokenSigningAlgorithmsSupported;
    private List<String> tokenEndpointAuthenticationMethodsSupported;
    private List<String> codeChallengeMethodsSupported;

    /** @return 不包含 Client Secret 和上游响应扩展字段的 Discovery 响应 */
    public static @NotNull OidcDiscoveryView from(@NotNull OidcDiscovery discovery) {
        return new OidcDiscoveryView(
                discovery.getProviderId(),
                discovery.getIssuer(),
                discovery.getAuthorizationUri(),
                discovery.getTokenUri(),
                discovery.getUserInfoUri(),
                discovery.getJwksUri(),
                discovery.getScopesSupported(),
                discovery.getResponseTypesSupported(),
                discovery.getSubjectTypesSupported(),
                discovery.getIdTokenSigningAlgorithmsSupported(),
                discovery.getTokenEndpointAuthenticationMethodsSupported(),
                discovery.getCodeChallengeMethodsSupported());
    }
}
