package com.sca4d.identity.oauth2;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

/** Redis 短期保存的 OAuth2 state、PKCE verifier 和对应 Provider 版本。 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class OAuth2Authorization {

    private String authorizationId;
    private String authenticationId;
    private Long providerId;
    private Long providerVersion;
    private String clientId;
    private String codeVerifier;
    private String redirectUri;
    private String browserHash;
    private Instant createdAt;
    private Instant expiresAt;
}
