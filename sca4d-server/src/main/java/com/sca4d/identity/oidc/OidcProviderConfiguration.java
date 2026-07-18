package com.sca4d.identity.oidc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 管理端可读取且不包含 Client Secret 密文的 OIDC Provider 配置。 */
@Getter
@AllArgsConstructor
public class OidcProviderConfiguration {

    private final Long providerId;
    private final String issuer;
    private final String clientId;
    private final Boolean clientSecretConfigured;
    private final List<String> scopes;
    private final Long version;
}
