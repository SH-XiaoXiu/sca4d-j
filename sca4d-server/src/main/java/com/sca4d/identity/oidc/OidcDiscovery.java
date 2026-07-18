package com.sca4d.identity.oidc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 从已保存 Issuer 读取并校验的 OIDC Discovery 元数据。 */
@Getter
@AllArgsConstructor
public class OidcDiscovery {

    private final Long providerId;
    private final String issuer;
    private final String authorizationUri;
    private final String tokenUri;
    private final String userInfoUri;
    private final String jwksUri;
    private final List<String> scopesSupported;
    private final List<String> responseTypesSupported;
    private final List<String> subjectTypesSupported;
    private final List<String> idTokenSigningAlgorithmsSupported;
    private final List<String> tokenEndpointAuthenticationMethodsSupported;
    private final List<String> codeChallengeMethodsSupported;
}
