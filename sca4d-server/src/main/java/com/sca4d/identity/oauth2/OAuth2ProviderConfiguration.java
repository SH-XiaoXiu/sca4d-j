package com.sca4d.identity.oauth2;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 管理端可读取且不包含 Client Secret 密文的 OAuth2 Provider 配置。 */
@Getter
@AllArgsConstructor
public class OAuth2ProviderConfiguration {

    private final Long providerId;
    private final String authorizationUri;
    private final String tokenUri;
    private final String profileUri;
    private final String auxiliaryProfileUri;
    private final String clientId;
    private final Boolean clientSecretConfigured;
    private final List<String> scopes;
    private final OAuth2ProfileMapping profileMapping;
    private final Long version;
}
