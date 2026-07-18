package com.sca4d.identity.oidc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.ClientSecretPost;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.AuthenticationScope;
import com.sca4d.authentication.AuthenticationScopeService;
import com.sca4d.authentication.audit.AuthenticationAuditOperations;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.InternalServiceException;
import com.sca4d.common.net.HttpEndpoints;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.config.properties.Sca4djIdentityProviderProperties;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.external.ExternalIdentityAuthenticationProof;
import com.sca4d.identity.external.ExternalIdentityAuthenticationService;
import com.sca4d.identity.external.ExternalIdentityProfile;
import com.sca4d.identity.external.ExternalIdentityVerification;
import com.sca4d.identity.repository.IdentityProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 使用 Nimbus 验证 OIDC Authorization Code 和 ID Token，并处理对应的外部身份。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultOidcAuthenticationService implements OidcAuthenticationService {

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final AuthenticationProcessService authentications;
    private final AuthenticationScopeService authenticationScopes;
    private final OidcAuthorizationStore authorizations;
    private final IdentityProviderRepository providers;
    private final OidcProviderRepository oidcProviders;
    private final OidcProviderService oidcProviderService;
    private final SecretKeyring keyring;
    private final ExternalIdentityAuthenticationService externalIdentityAuthentication;
    private final Sca4djIdentityProviderProperties properties;
    private final RestClient restClient;

    @Override
    public @NotNull List<OidcAvailableProvider> providers(
            @NotNull AccountScopeType scopeType, @Nullable String tenantCode) {
        AuthenticationScope authenticationScope = authenticationScopes.resolve(scopeType, tenantCode);
        IdentityProviderScope scope = new IdentityProviderScope(
                authenticationScope.getScopeType(), authenticationScope.getScopeId());
        return providers.findProviders(scope).stream()
                .filter(provider -> provider.getProtocol() == IdentityProviderProtocol.OIDC)
                .filter(provider -> provider.getStatus() == IdentityProviderStatus.ACTIVE)
                .map(provider -> new OidcAvailableProvider(provider.getCode(), provider.getName()))
                .toList();
    }

    @Override
    public @NotNull OidcAuthorizationStart authorize(
            @NotNull String authenticationId,
            @NotNull String browserHash,
            @NotNull String providerCode) {
        AuthenticationProcess authentication = authentications.requireProof(
                authenticationId, browserHash, AuthMethodType.OIDC);
        IdentityProviderScope scope = new IdentityProviderScope(authentication.getScopeType(), authentication.getScopeId());
        IdentityProvider provider = providers.findProvider(scope, providerCode);
        if (provider == null
                || provider.getProtocol() != IdentityProviderProtocol.OIDC
                || provider.getStatus() != IdentityProviderStatus.ACTIVE) {
            throw new BizException(IdentityProviderErrorCode.OIDC_PROVIDER_UNAVAILABLE);
        }
        OidcClient client = oidcProviders.findClient(provider.getProviderId(), provider.getVersion());
        if (client == null) {
            throw new BizException(IdentityProviderErrorCode.OIDC_PROVIDER_UNAVAILABLE);
        }
        OidcDiscovery discovery = oidcProviderService.discover(scope, provider.getProviderId());
        if (!discovery.getResponseTypesSupported().contains("code")
                || !discovery.getIdTokenSigningAlgorithmsSupported().contains(JWSAlgorithm.RS256.getName())
                || !discovery.getCodeChallengeMethodsSupported().contains(CodeChallengeMethod.S256.getValue())) {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_DISCOVERY_INVALID);
        }
        List<String> tokenAuthenticationMethods = discovery.getTokenEndpointAuthenticationMethodsSupported();
        OidcClientAuthenticationMethod clientAuthenticationMethod;
        if (tokenAuthenticationMethods.isEmpty()
                || tokenAuthenticationMethods.contains(OidcClientAuthenticationMethod.CLIENT_SECRET_BASIC.value())) {
            clientAuthenticationMethod = OidcClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        } else if (tokenAuthenticationMethods.contains(OidcClientAuthenticationMethod.CLIENT_SECRET_POST.value())) {
            clientAuthenticationMethod = OidcClientAuthenticationMethod.CLIENT_SECRET_POST;
        } else {
            throw new BizException(IdentityProviderErrorCode.PROVIDER_DISCOVERY_INVALID);
        }

        Sca4djIdentityProviderProperties.Oidc oidc = properties.getOidc();
        URI redirectUri = oidc.getRedirectUri();
        URI completionUri = oidc.getCompletionUri();
        if (!HttpEndpoints.isSecure(redirectUri) || !HttpEndpoints.isSecure(completionUri)) {
            throw new IllegalStateException("OIDC redirect-uri 和 completion-uri 必须是精确 HTTPS URI；本机开发允许 HTTP");
        }
        Duration ttl = oidc.getAuthorizationTtl();
        if (ttl == null
                || ttl.isZero()
                || ttl.isNegative()
                || ttl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalStateException("OIDC authorization-ttl 必须大于 0 且不超过 15 分钟");
        }

        State state = new State();
        Nonce nonce = new Nonce();
        CodeVerifier codeVerifier = new CodeVerifier();
        AuthenticationRequest request = new AuthenticationRequest.Builder(
                new ResponseType(ResponseType.Value.CODE),
                new Scope(client.getScopes().toArray(String[]::new)),
                new ClientID(client.getClientId()),
                redirectUri)
                .endpointURI(URI.create(discovery.getAuthorizationUri()))
                .state(state)
                .nonce(nonce)
                .codeChallenge(codeVerifier, CodeChallengeMethod.S256)
                .build();
        Instant now = Instant.now();
        OidcAuthorization authorization = new OidcAuthorization()
                .setAuthorizationId(state.getValue())
                .setAuthenticationId(authentication.getAuthenticationId())
                .setProviderId(provider.getProviderId())
                .setProviderVersion(provider.getVersion())
                .setIssuer(client.getIssuer())
                .setTokenUri(discovery.getTokenUri())
                .setUserInfoUri(discovery.getUserInfoUri())
                .setJwksUri(discovery.getJwksUri())
                .setClientId(client.getClientId())
                .setClientAuthenticationMethod(clientAuthenticationMethod)
                .setScopes(client.getScopes())
                .setNonce(nonce.getValue())
                .setCodeVerifier(codeVerifier.getValue())
                .setRedirectUri(redirectUri.toString())
                .setBrowserHash(browserHash)
                .setCreatedAt(now)
                .setExpiresAt(now.plus(ttl));
        if (!authorizations.create(authorization, ttl)) {
            throw new InternalServiceException("OIDC state 已存在");
        }
        log.info(
                "OIDC 授权请求已创建 | authenticationId={} providerId={} purpose={} expiresAt={}",
                authentication.getAuthenticationId(),
                provider.getProviderId(),
                authentication.getPurpose(),
                authorization.getExpiresAt());
        return new OidcAuthorizationStart(request.toURI().toString(), authorization.getExpiresAt());
    }

    @Override
    @Audit(module = AuthenticationAuditOperations.LOGIN, action = AuthenticationAuditOperations.OIDC)
    public @NotNull AuthenticationProcess callback(
            @NotNull String authorizationId,
            @Nullable String code,
            @Nullable String error,
            @NotNull String browserHash) {
        if (!authorizationId.matches("[A-Za-z0-9_-]{20,128}")) {
            throw new BizException(IdentityProviderErrorCode.OIDC_AUTHORIZATION_UNAVAILABLE);
        }
        OidcAuthorization authorization = authorizations.consume(authorizationId, browserHash);
        if (authorization == null
                || authorization.getExpiresAt().isBefore(Instant.now())) {
            throw new BizException(IdentityProviderErrorCode.OIDC_AUTHORIZATION_UNAVAILABLE);
        }
        if (error != null || code == null || code.isBlank()) {
            throw new BizException(IdentityProviderErrorCode.OIDC_AUTHORIZATION_REJECTED);
        }

        AuthenticationProcess authentication = authentications.requireProof(
                authorization.getAuthenticationId(), browserHash, AuthMethodType.OIDC);
        IdentityProviderScope scope = new IdentityProviderScope(authentication.getScopeType(), authentication.getScopeId());
        IdentityProvider provider = providers.findProvider(scope, authorization.getProviderId());
        if (provider == null
                || provider.getProtocol() != IdentityProviderProtocol.OIDC
                || provider.getStatus() != IdentityProviderStatus.ACTIVE
                || !provider.getVersion().equals(authorization.getProviderVersion())) {
            throw new BizException(IdentityProviderErrorCode.OIDC_PROVIDER_UNAVAILABLE);
        }
        OidcClient client = oidcProviders.findClient(provider.getProviderId(), provider.getVersion());
        if (client == null
                || !client.getIssuer().equals(authorization.getIssuer())
                || !client.getClientId().equals(authorization.getClientId())) {
            throw new BizException(IdentityProviderErrorCode.OIDC_PROVIDER_UNAVAILABLE);
        }

        String clientSecret = keyring.decrypt(
                "identity-provider.oidc." + provider.getProviderId() + ".client-secret",
                client.getEncryptedClientSecret());
        ClientAuthentication clientAuthentication = switch (authorization.getClientAuthenticationMethod()) {
            case CLIENT_SECRET_BASIC -> new ClientSecretBasic(
                    new ClientID(client.getClientId()), new Secret(clientSecret));
            case CLIENT_SECRET_POST -> new ClientSecretPost(
                    new ClientID(client.getClientId()), new Secret(clientSecret));
        };
        AuthorizationCodeGrant grant = new AuthorizationCodeGrant(
                new AuthorizationCode(code),
                URI.create(authorization.getRedirectUri()),
                new CodeVerifier(authorization.getCodeVerifier()));
        long tokenStartedAt = System.nanoTime();
        log.debug(
                "OIDC Token 交换开始 | authenticationId={} providerId={}",
                authentication.getAuthenticationId(),
                provider.getProviderId());
        TokenResponse tokenResponse;
        try {
            tokenResponse = OIDCTokenResponseParser.parse(send(new TokenRequest.Builder(
                    URI.create(authorization.getTokenUri()), clientAuthentication, grant)
                    .build()
                    .toHTTPRequest()));
        } catch (ParseException exception) {
            log.debug(
                    "OIDC Token 响应无法解析 | authenticationId={} providerId={} durationMs={}",
                    authentication.getAuthenticationId(),
                    provider.getProviderId(),
                    (System.nanoTime() - tokenStartedAt) / 1_000_000);
            throw new BizException(IdentityProviderErrorCode.OIDC_TOKEN_INVALID, exception);
        }
        if (!tokenResponse.indicatesSuccess()) {
            log.debug(
                    "OIDC Token 交换被上游拒绝 | authenticationId={} providerId={} durationMs={}",
                    authentication.getAuthenticationId(),
                    provider.getProviderId(),
                    (System.nanoTime() - tokenStartedAt) / 1_000_000);
            throw new BizException(IdentityProviderErrorCode.OIDC_AUTHORIZATION_REJECTED);
        }
        log.debug(
                "OIDC Token 交换完成 | authenticationId={} providerId={} durationMs={}",
                authentication.getAuthenticationId(),
                provider.getProviderId(),
                (System.nanoTime() - tokenStartedAt) / 1_000_000);
        OIDCTokens tokens = ((OIDCTokenResponse) tokenResponse.toSuccessResponse()).getOIDCTokens();
        JWT idToken = tokens.getIDToken();
        if (!(idToken instanceof SignedJWT signedIdToken)
                || !JWSAlgorithm.RS256.equals(signedIdToken.getHeader().getAlgorithm())) {
            throw new BizException(IdentityProviderErrorCode.OIDC_TOKEN_INVALID);
        }
        IDTokenClaimsSet claims = validateIdToken(authorization, signedIdToken);

        UserInfo userInfo = null;
        if (authorization.getUserInfoUri() != null) {
            long userInfoStartedAt = System.nanoTime();
            log.debug(
                    "OIDC UserInfo 读取开始 | authenticationId={} providerId={}",
                    authentication.getAuthenticationId(),
                    provider.getProviderId());
            try {
                UserInfoResponse userInfoResponse = UserInfoResponse.parse(send(new UserInfoRequest(
                        URI.create(authorization.getUserInfoUri()), tokens.getAccessToken())
                        .toHTTPRequest()));
                if (!userInfoResponse.indicatesSuccess()
                        || userInfoResponse.toSuccessResponse().getUserInfo() == null) {
                    log.debug(
                            "OIDC UserInfo 读取被上游拒绝 | authenticationId={} providerId={} durationMs={}",
                            authentication.getAuthenticationId(),
                            provider.getProviderId(),
                            (System.nanoTime() - userInfoStartedAt) / 1_000_000);
                    throw new BizException(IdentityProviderErrorCode.OIDC_TOKEN_INVALID);
                }
                userInfo = userInfoResponse.toSuccessResponse().getUserInfo();
            } catch (ParseException exception) {
                log.debug(
                        "OIDC UserInfo 响应无法解析 | authenticationId={} providerId={} durationMs={}",
                        authentication.getAuthenticationId(),
                        provider.getProviderId(),
                        (System.nanoTime() - userInfoStartedAt) / 1_000_000);
                throw new BizException(IdentityProviderErrorCode.OIDC_TOKEN_INVALID, exception);
            }
            if (!claims.getSubject().equals(userInfo.getSubject())) {
                throw new BizException(IdentityProviderErrorCode.OIDC_TOKEN_INVALID);
            }
            log.debug(
                    "OIDC UserInfo 读取完成 | authenticationId={} providerId={} durationMs={}",
                    authentication.getAuthenticationId(),
                    provider.getProviderId(),
                    (System.nanoTime() - userInfoStartedAt) / 1_000_000);
        }

        UserInfo idTokenProfile = new UserInfo(claims.toJSONObject());
        String displayName = userInfo != null && userInfo.getName() != null
                ? userInfo.getName()
                : idTokenProfile.getName();
        if (displayName == null) {
            displayName = userInfo != null && userInfo.getPreferredUsername() != null
                    ? userInfo.getPreferredUsername()
                    : idTokenProfile.getPreferredUsername();
        }
        String email = userInfo != null && userInfo.getEmailAddress() != null
                ? userInfo.getEmailAddress()
                : idTokenProfile.getEmailAddress();
        Boolean emailVerified = userInfo != null && userInfo.getEmailVerified() != null
                ? userInfo.getEmailVerified()
                : idTokenProfile.getEmailVerified();
        URI picture = userInfo != null && userInfo.getPicture() != null
                ? userInfo.getPicture()
                : idTokenProfile.getPicture();
        ExternalIdentityProfile profile = new ExternalIdentityProfile(
                claims.getSubject().getValue(),
                displayName,
                email,
                Boolean.TRUE.equals(emailVerified),
                picture == null ? null : picture.toString());

        // ID Token 签名、nonce 和 UserInfo 全部通过后才选定 OIDC；上游失败时可以重新发起授权。
        authentication = authentications.claimPrimary(
                authorization.getAuthenticationId(), browserHash, AuthMethodType.OIDC);
        ExternalIdentityVerification verification = externalIdentityAuthentication.authenticate(
                authentication, provider, profile);
        AuthenticationProcess verified = authentications.proof(
                authentication.getAuthenticationId(),
                browserHash,
                AuthMethodType.OIDC,
                ExternalIdentityAuthenticationProof.class,
                new ExternalIdentityAuthenticationProof(
                        verification.getIdentityId(), verification.getIdentityVersion()));
        log.info(
                "OIDC 身份验证通过 | authenticationId={} providerId={} identityId={} subjectId={} accountId={} activityId={}",
                verified.getAuthenticationId(),
                provider.getProviderId(),
                verification.getIdentityId(),
                verification.getSubject().getSubjectId(),
                verification.getAccount().getAccountId(),
                verified.getActivityId());
        return verified;
    }

    /** @return 校验签名、Issuer、Audience、时间和 nonce 后的 ID Token claims */
    private @NotNull IDTokenClaimsSet validateIdToken(
            @NotNull OidcAuthorization authorization, @NotNull SignedJWT idToken) {
        long jwksStartedAt = System.nanoTime();
        log.debug(
                "OIDC JWKS 读取开始 | authenticationId={} providerId={}",
                authorization.getAuthenticationId(),
                authorization.getProviderId());
        HTTPRequest jwksRequest = new HTTPRequest(HTTPRequest.Method.GET, URI.create(authorization.getJwksUri()));
        HTTPResponse jwksResponse = send(jwksRequest);
        int jwksBytes = jwksResponse.getBody() == null
                ? 0
                : jwksResponse.getBody().getBytes(StandardCharsets.UTF_8).length;
        if (jwksResponse.getStatusCode() != HTTPResponse.SC_OK
                || jwksResponse.getBody() == null) {
            log.debug(
                    "OIDC JWKS 读取未通过 | authenticationId={} providerId={} status={} responseBytes={} durationMs={}",
                    authorization.getAuthenticationId(),
                    authorization.getProviderId(),
                    jwksResponse.getStatusCode(),
                    jwksBytes,
                    (System.nanoTime() - jwksStartedAt) / 1_000_000);
            throw new BizException(IdentityProviderErrorCode.OIDC_UPSTREAM_FAILED);
        }
        log.debug(
                "OIDC JWKS 读取完成 | authenticationId={} providerId={} responseBytes={} durationMs={}",
                authorization.getAuthenticationId(),
                authorization.getProviderId(),
                jwksBytes,
                (System.nanoTime() - jwksStartedAt) / 1_000_000);
        try {
            IDTokenValidator validator = new IDTokenValidator(
                    new Issuer(authorization.getIssuer()),
                    new ClientID(authorization.getClientId()),
                    JWSAlgorithm.RS256,
                    JWKSet.parse(jwksResponse.getBody()));
            validator.setMaxClockSkew(60);
            return validator.validate(idToken, new Nonce(authorization.getNonce()));
        } catch (java.text.ParseException | BadJOSEException | JOSEException exception) {
            throw new BizException(IdentityProviderErrorCode.OIDC_TOKEN_INVALID, exception);
        }
    }

    /** @return 使用共享超时、禁止重定向并限制响应大小的 Nimbus HTTP 响应 */
    private @NotNull HTTPResponse send(@NotNull HTTPRequest request) {
        try {
            RestClient.RequestBodySpec outbound = restClient
                    .method(switch (request.getMethod()) {
                        case GET -> HttpMethod.GET;
                        case POST -> HttpMethod.POST;
                        case PUT -> HttpMethod.PUT;
                        case DELETE -> HttpMethod.DELETE;
                    })
                    .uri(request.getURI());
            request.getHeaderMap().forEach((name, values) -> {
                if (name != null && values != null) {
                    outbound.header(name, values.toArray(String[]::new));
                }
            });
            if (request.getBody() != null) {
                outbound.body(request.getBody());
            }
            HTTPResponse response = outbound.exchange((clientRequest, clientResponse) -> {
                byte[] body = clientResponse.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) {
                    throw new BizException(IdentityProviderErrorCode.OIDC_UPSTREAM_FAILED);
                }
                HTTPResponse result = new HTTPResponse(clientResponse.getStatusCode().value());
                result.setStatusMessage(clientResponse.getStatusText());
                clientResponse.getHeaders().forEach(
                        (name, values) -> result.setHeader(name, values.toArray(String[]::new)));
                if (body.length > 0) {
                    result.setBody(new String(body, StandardCharsets.UTF_8));
                }
                return result;
            });
            if (response == null) {
                throw new BizException(IdentityProviderErrorCode.OIDC_UPSTREAM_FAILED);
            }
            return response;
        } catch (RestClientException exception) {
            throw new BizException(IdentityProviderErrorCode.OIDC_UPSTREAM_FAILED, exception);
        }
    }

}
