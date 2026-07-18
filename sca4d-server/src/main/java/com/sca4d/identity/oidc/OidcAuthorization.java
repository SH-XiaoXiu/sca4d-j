package com.sca4d.identity.oidc;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

/** Redis 短期保存的 OIDC state、nonce、PKCE verifier 和本次请求使用的 Provider 配置。 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class OidcAuthorization {

    private String authorizationId;
    private String authenticationId;
    private Long providerId;
    private Long providerVersion;
    private String issuer;
    private String tokenUri;
    private String userInfoUri;
    private String jwksUri;
    private String clientId;
    private OidcClientAuthenticationMethod clientAuthenticationMethod;
    private List<String> scopes;
    private String nonce;
    private String codeVerifier;
    private String redirectUri;
    private String browserHash;
    private Instant createdAt;
    private Instant expiresAt;
}
