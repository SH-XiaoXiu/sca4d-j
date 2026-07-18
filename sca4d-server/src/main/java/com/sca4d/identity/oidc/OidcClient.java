package com.sca4d.identity.oidc;

import com.sca4d.common.security.EncryptedSecret;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** OIDC 认证请求使用的 Issuer、Client、scope、父 Provider 版本和加密 Client Secret。 */
@Getter
@AllArgsConstructor
public class OidcClient {

    private final Long providerId;
    private final String issuer;
    private final String clientId;
    private final EncryptedSecret encryptedClientSecret;
    private final List<String> scopes;
    private final Long providerVersion;
}
