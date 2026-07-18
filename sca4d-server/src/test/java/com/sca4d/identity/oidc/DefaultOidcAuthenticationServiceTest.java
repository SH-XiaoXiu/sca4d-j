package com.sca4d.identity.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.authentication.AuthenticationPurpose;
import com.sca4d.authentication.AuthenticationScopeService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.EncryptedSecret;
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
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import com.sca4d.identity.repository.IdentityProviderRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 使用标准 RS256 ID Token、JWKS 和 UserInfo 验证 OIDC 授权请求与 callback。 */
class DefaultOidcAuthenticationServiceTest {

    @Test
    void shouldCreateAuthorizationRequestWithStateNonceAndPkce() {
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        OidcAuthorizationStore authorizations = mock(OidcAuthorizationStore.class);
        IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
        OidcProviderRepository oidcProviders = mock(OidcProviderRepository.class);
        OidcProviderService oidcProviderService = mock(OidcProviderService.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        AuthenticationProcess flow = pendingFlow();
        when(flows.requireProof(flow.getAuthenticationId(), "browser-hash", AuthMethodType.OIDC))
                .thenReturn(flow);
        when(providers.findProvider(scope, "corporate")).thenReturn(provider(scope));
        when(oidcProviders.findClient(100L, 3L)).thenReturn(client(3L));
        when(oidcProviderService.discover(scope, 100L)).thenReturn(discovery("http://localhost:19090"));
        when(authorizations.create(any(OidcAuthorization.class), eq(Duration.ofMinutes(5))))
                .thenReturn(true);
        Sca4djIdentityProviderProperties properties = properties();
        properties.getOidc().setRedirectUri(URI.create("http://localhost:8080/auth/oidc/callback"));
        properties.getOidc().setCompletionUri(URI.create("http://localhost:5173/login"));
        DefaultOidcAuthenticationService service = new DefaultOidcAuthenticationService(
                flows,
                mock(AuthenticationScopeService.class),
                authorizations,
                providers,
                oidcProviders,
                oidcProviderService,
                mock(SecretKeyring.class),
                mock(ExternalIdentityAuthenticationService.class),
                properties,
                RestClient.create());

        OidcAuthorizationStart start = service.authorize(
                flow.getAuthenticationId(), "browser-hash", "corporate");

        Map<String, String> query = Arrays.stream(URI.create(start.getAuthorizationUri())
                .getRawQuery()
                .split("&"))
                .map(value -> value.split("=", 2))
                .collect(Collectors.toMap(
                        value -> URLDecoder.decode(value[0], StandardCharsets.UTF_8),
                        value -> URLDecoder.decode(value[1], StandardCharsets.UTF_8)));
        assertThat(query)
                .containsEntry("response_type", "code")
                .containsEntry("client_id", "client-1")
                .containsEntry("redirect_uri", "http://localhost:8080/auth/oidc/callback")
                .containsEntry("code_challenge_method", "S256");
        assertThat(query.get("scope")).contains("openid");
        assertThat(query.get("state")).isNotBlank();
        assertThat(query.get("nonce")).isNotBlank();
        assertThat(query.get("code_challenge")).isNotBlank();
        ArgumentCaptor<OidcAuthorization> authorizationCaptor = ArgumentCaptor.forClass(OidcAuthorization.class);
        verify(authorizations).create(authorizationCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertThat(authorizationCaptor.getValue().getAuthorizationId()).isEqualTo(query.get("state"));
        assertThat(authorizationCaptor.getValue().getNonce()).isEqualTo(query.get("nonce"));
        assertThat(authorizationCaptor.getValue().getCodeVerifier()).isNotBlank();
    }

    @Test
    void shouldValidateTokenAndUserInfoThenAdvanceExistingLink() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        OidcAuthorization authorization = authorization("nonce-1");
        HttpServer server = oidcServer(rsaKey, authorization, "nonce-1");
        server.start();
        try {
            String baseUri = "http://127.0.0.1:" + server.getAddress().getPort();
            authorization
                    .setIssuer(baseUri)
                    .setTokenUri(baseUri + "/token")
                    .setUserInfoUri(baseUri + "/userinfo")
                    .setJwksUri(baseUri + "/jwks");
            AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
            OidcAuthorizationStore authorizations = mock(OidcAuthorizationStore.class);
            IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
            OidcProviderRepository oidcProviders = mock(OidcProviderRepository.class);
            SecretKeyring keyring = mock(SecretKeyring.class);
            ExternalIdentityAuthenticationService externalIdentityAuthentication = mock(ExternalIdentityAuthenticationService.class);
            IdentityProviderScope scope = IdentityProviderScope.global();
            AuthenticationProcess pending = pendingFlow()
                    .setClaimedMethodType(AuthMethodType.OIDC);
            AuthenticationProcess verified = pendingFlow()
                    .setActivityId("completion")
                    .setSubjectId(20L)
                    .setAccountId(30L)
                    .setPrimaryMethodType(AuthMethodType.OIDC)
                    .setPrimaryProofId("200")
                    .setPrimaryProofVersion(4L);
            ExternalIdentityVerification identity = new ExternalIdentityVerification(
                    new Subject(20L, "Alice"),
                    new Account(30L, 20L, AccountScopeType.GLOBAL, null, false),
                    200L,
                    4L);
            when(authorizations.consume(authorization.getAuthorizationId(), "browser-hash"))
                    .thenReturn(authorization);
            when(flows.requireProof(
                    authorization.getAuthenticationId(), "browser-hash", AuthMethodType.OIDC))
                    .thenReturn(pendingFlow());
            when(flows.claimPrimary(
                    pending.getAuthenticationId(),
                    "browser-hash",
                    AuthMethodType.OIDC))
                    .thenReturn(pending);
            when(providers.findProvider(scope, 100L)).thenReturn(provider(scope));
            when(oidcProviders.findClient(100L, 3L)).thenReturn(client(3L).withIssuer(baseUri));
            when(keyring.decrypt(eq("identity-provider.oidc.100.client-secret"), any(EncryptedSecret.class)))
                    .thenReturn("client-secret");
            when(externalIdentityAuthentication.authenticate(eq(pending), any(), any()))
                    .thenReturn(identity);
            when(flows.proof(
                    eq(pending.getAuthenticationId()),
                    eq("browser-hash"),
                    eq(AuthMethodType.OIDC),
                    eq(ExternalIdentityAuthenticationProof.class),
                    any(ExternalIdentityAuthenticationProof.class)))
                    .thenReturn(verified);
            DefaultOidcAuthenticationService service = new DefaultOidcAuthenticationService(
                    flows,
                    mock(AuthenticationScopeService.class),
                    authorizations,
                    providers,
                    oidcProviders,
                    mock(OidcProviderService.class),
                    keyring,
                    externalIdentityAuthentication,
                    properties(),
                    RestClient.create());

            AuthenticationProcess result = service.callback(
                    authorization.getAuthorizationId(), "authorization-code", null, "browser-hash");

            assertThat(result).isSameAs(verified);
            ArgumentCaptor<ExternalIdentityProfile> profileCaptor = ArgumentCaptor
                    .forClass(ExternalIdentityProfile.class);
            verify(externalIdentityAuthentication).authenticate(
                    eq(pending), any(), profileCaptor.capture());
            assertThat(profileCaptor.getValue().getExternalSubject()).isEqualTo("external-1");
            assertThat(profileCaptor.getValue().getDisplayName()).isEqualTo("Alice UserInfo");
            assertThat(profileCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
            assertThat(profileCaptor.getValue().getEmailVerified()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectIdTokenWithDifferentNonce() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        OidcAuthorization authorization = authorization("expected-nonce");
        HttpServer server = oidcServer(rsaKey, authorization, "other-nonce");
        server.start();
        try {
            String baseUri = "http://127.0.0.1:" + server.getAddress().getPort();
            authorization
                    .setIssuer(baseUri)
                    .setTokenUri(baseUri + "/token")
                    .setUserInfoUri(null)
                    .setJwksUri(baseUri + "/jwks");
            AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
            OidcAuthorizationStore authorizations = mock(OidcAuthorizationStore.class);
            IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
            OidcProviderRepository oidcProviders = mock(OidcProviderRepository.class);
            SecretKeyring keyring = mock(SecretKeyring.class);
            ExternalIdentityAuthenticationService externalIdentityAuthentication = mock(ExternalIdentityAuthenticationService.class);
            when(authorizations.consume(authorization.getAuthorizationId(), "browser-hash"))
                    .thenReturn(authorization);
            when(flows.requireProof(
                    authorization.getAuthenticationId(), "browser-hash", AuthMethodType.OIDC))
                    .thenReturn(pendingFlow());
            when(providers.findProvider(IdentityProviderScope.global(), 100L))
                    .thenReturn(provider(IdentityProviderScope.global()));
            when(oidcProviders.findClient(100L, 3L)).thenReturn(client(3L).withIssuer(baseUri));
            when(keyring.decrypt(any(), any())).thenReturn("client-secret");
            DefaultOidcAuthenticationService service = new DefaultOidcAuthenticationService(
                    flows,
                    mock(AuthenticationScopeService.class),
                    authorizations,
                    providers,
                    oidcProviders,
                    mock(OidcProviderService.class),
                    keyring,
                    externalIdentityAuthentication,
                    properties(),
                    RestClient.create());

            assertThatThrownBy(() -> service.callback(
                    authorization.getAuthorizationId(),
                    "authorization-code",
                    null,
                    "browser-hash"))
                    .isInstanceOfSatisfying(
                            BizException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(IdentityProviderErrorCode.OIDC_TOKEN_INVALID));
            verify(externalIdentityAuthentication, never()).authenticate(any(), any(), any());
            verify(flows, never()).claimPrimary(any(), any(), any());
        } finally {
            server.stop(0);
        }
    }

    /** @return 接收 Token、JWKS 和 UserInfo 请求的本机标准 OIDC 服务 */
    private HttpServer oidcServer(
            RSAKey rsaKey, OidcAuthorization authorization, String tokenNonce) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        SignedJWT idToken = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject("external-1")
                        .audience("client-1")
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .claim("nonce", tokenNonce)
                        .claim("name", "Alice Token")
                        .build());
        idToken.sign(new RSASSASigner(rsaKey));
        String tokenBody = new OIDCTokenResponse(
                new OIDCTokens(idToken, new BearerAccessToken("access-token"), null))
                .toJSONObject()
                .toJSONString();
        server.createContext("/token", exchange -> respond(exchange, tokenBody));
        String jwksBody = JSONObjectUtils.toJSONString(
                new JWKSet(rsaKey.toPublicJWK()).toJSONObject());
        server.createContext("/jwks", exchange -> respond(exchange, jwksBody));
        server.createContext("/userinfo", exchange -> respond(
                exchange,
                "{\"sub\":\"external-1\",\"name\":\"Alice UserInfo\","
                        + "\"email\":\"alice@example.com\",\"email_verified\":true}"));
        return server;
    }

    /** 写入不缓存的 JSON 响应。 */
    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** @return 等待提交主要认证方式的 GLOBAL LOGIN 认证 */
    private AuthenticationProcess pendingFlow() {
        return new AuthenticationProcess()
                .setAuthenticationId("1db75f90-3378-4dc9-aa0a-198ad1a7a014")
                .setPurpose(AuthenticationPurpose.LOGIN)
                .setActivityId("primary-proof")
                .setScopeType(AccountScopeType.GLOBAL)
                .setBrowserHash("browser-hash")
                .setCreatedAt(Instant.now())
                .setExpiresAt(Instant.now().plusSeconds(300));
    }

    /** @return 当前测试使用的 ACTIVE OIDC Provider */
    private IdentityProvider provider(IdentityProviderScope scope) {
        return new IdentityProvider(
                100L,
                scope,
                "corporate",
                "Corporate",
                IdentityProviderProtocol.OIDC,
                IdentityProviderRegistrationPolicy.JIT,
                false,
                IdentityProviderStatus.ACTIVE,
                3L);
    }

    /** @return 包含测试 Client Secret 密文占位值的 OIDC Client */
    private TestOidcClient client(long version) {
        return new TestOidcClient(
                100L,
                "http://localhost:19090",
                "client-1",
                new EncryptedSecret("local", new byte[12], new byte[16]),
                List.of("openid", "profile", "email"),
                version);
    }

    /** @return 支持 Authorization Code、RS256、PKCE 和 Client Secret Basic 的 Discovery */
    private OidcDiscovery discovery(String baseUri) {
        return new OidcDiscovery(
                100L,
                baseUri,
                baseUri + "/authorize",
                baseUri + "/token",
                baseUri + "/userinfo",
                baseUri + "/jwks",
                List.of("openid", "profile", "email"),
                List.of("code"),
                List.of("public"),
                List.of("RS256"),
                List.of("client_secret_basic"),
                List.of("S256"));
    }

    /** @return callback 消费的 OIDC state、nonce、PKCE 和 Provider 快照 */
    private OidcAuthorization authorization(String nonce) {
        return new OidcAuthorization()
                .setAuthorizationId("m5N5cT8rrJzv1rY4xLELcwQ9Oukz8dB9")
                .setAuthenticationId(pendingFlow().getAuthenticationId())
                .setProviderId(100L)
                .setProviderVersion(3L)
                .setIssuer("http://localhost:19090")
                .setTokenUri("http://localhost:19090/token")
                .setUserInfoUri("http://localhost:19090/userinfo")
                .setJwksUri("http://localhost:19090/jwks")
                .setClientId("client-1")
                .setClientAuthenticationMethod(OidcClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .setScopes(List.of("openid", "profile", "email"))
                .setNonce(nonce)
                .setCodeVerifier("6F8N6E0v2JG5mHC4LwX0fTh9AH0P8SMjGW1Dxu3K5HY")
                .setRedirectUri("http://localhost:8080/auth/oidc/callback")
                .setBrowserHash("browser-hash")
                .setCreatedAt(Instant.now())
                .setExpiresAt(Instant.now().plusSeconds(300));
    }

    /** @return OIDC 请求使用的测试超时和授权请求 TTL */
    private Sca4djIdentityProviderProperties properties() {
        Sca4djIdentityProviderProperties properties = new Sca4djIdentityProviderProperties();
        properties.setConnectionTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return properties;
    }

    /** 测试中只替换 Issuer，其他 OIDC Client 字段保持不变。 */
    private static class TestOidcClient extends OidcClient {

        TestOidcClient(
                Long providerId,
                String issuer,
                String clientId,
                EncryptedSecret encryptedClientSecret,
                List<String> scopes,
                Long providerVersion) {
            super(providerId, issuer, clientId, encryptedClientSecret, scopes, providerVersion);
        }

        /** @return 使用指定 Issuer 的新测试 OIDC Client */
        TestOidcClient withIssuer(String issuer) {
            return new TestOidcClient(
                    getProviderId(),
                    issuer,
                    getClientId(),
                    getEncryptedClientSecret(),
                    getScopes(),
                    getProviderVersion());
        }
    }
}
