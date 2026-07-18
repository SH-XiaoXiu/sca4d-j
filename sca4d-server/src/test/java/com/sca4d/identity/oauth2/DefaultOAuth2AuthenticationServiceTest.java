package com.sca4d.identity.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 使用本机标准 Token 和 Profile 端点验证 OAuth2 Authorization Code + PKCE。 */
class DefaultOAuth2AuthenticationServiceTest {

    @Test
    void shouldCreateAuthorizationRequestWithStateAndPkce() {
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        OAuth2AuthorizationStore authorizations = mock(OAuth2AuthorizationStore.class);
        IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
        OAuth2ProviderRepository oauth2Providers = mock(OAuth2ProviderRepository.class);
        AuthenticationProcess flow = pendingFlow();
        when(flows.requireProof(flow.getAuthenticationId(), "browser-hash", AuthMethodType.OAUTH2))
                .thenReturn(flow);
        when(providers.findProvider(IdentityProviderScope.global(), "standard"))
                .thenReturn(provider());
        when(oauth2Providers.findClient(100L, 3L)).thenReturn(client("http://localhost:19091"));
        when(authorizations.create(any(), eq(Duration.ofMinutes(5)))).thenReturn(true);
        DefaultOAuth2AuthenticationService service = service(
                flows,
                authorizations,
                providers,
                oauth2Providers,
                mock(SecretKeyring.class),
                mock(ExternalIdentityAuthenticationService.class));

        OAuth2AuthorizationStart start = service.authorize(
                flow.getAuthenticationId(), "browser-hash", "standard");

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
                .containsEntry("redirect_uri", "http://localhost:8080/auth/oauth2/callback")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("scope", "profile email");
        assertThat(query.get("state")).isNotBlank();
        assertThat(query.get("code_challenge")).isNotBlank();
        ArgumentCaptor<OAuth2Authorization> authorizationCaptor = ArgumentCaptor.forClass(OAuth2Authorization.class);
        verify(authorizations).create(authorizationCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertThat(authorizationCaptor.getValue().getAuthorizationId()).isEqualTo(query.get("state"));
        assertThat(authorizationCaptor.getValue().getCodeVerifier()).isNotBlank();
    }

    @Test
    void shouldExchangeCodeMapPrimaryAndAuxiliaryProfilesThenAdvanceFlow() throws Exception {
        HttpServer server = oauth2Server("{\"id\":\"external-1\",\"name\":\"Alice\"}");
        server.start();
        try {
            String baseUri = "http://127.0.0.1:" + server.getAddress().getPort();
            AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
            OAuth2AuthorizationStore authorizations = mock(OAuth2AuthorizationStore.class);
            IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
            OAuth2ProviderRepository oauth2Providers = mock(OAuth2ProviderRepository.class);
            SecretKeyring keyring = mock(SecretKeyring.class);
            ExternalIdentityAuthenticationService externalIdentityAuthentication = mock(ExternalIdentityAuthenticationService.class);
            OAuth2Authorization authorization = authorization();
            AuthenticationProcess pending = pendingFlow()
                    .setClaimedMethodType(AuthMethodType.OAUTH2);
            AuthenticationProcess verified = pendingFlow()
                    .setActivityId("completion")
                    .setSubjectId(20L)
                    .setAccountId(30L)
                    .setPrimaryMethodType(AuthMethodType.OAUTH2);
            ExternalIdentityVerification identity = new ExternalIdentityVerification(
                    new Subject(20L, "Alice"),
                    new Account(30L, 20L, AccountScopeType.GLOBAL, null, false),
                    200L,
                    4L);
            when(authorizations.consume(authorization.getAuthorizationId(), "browser-hash"))
                    .thenReturn(authorization);
            when(flows.requireProof(
                    authorization.getAuthenticationId(), "browser-hash", AuthMethodType.OAUTH2))
                    .thenReturn(pendingFlow());
            when(flows.claimPrimary(authorization.getAuthenticationId(), "browser-hash", AuthMethodType.OAUTH2))
                    .thenReturn(pending);
            when(providers.findProvider(IdentityProviderScope.global(), 100L)).thenReturn(provider());
            when(oauth2Providers.findClient(100L, 3L)).thenReturn(client(baseUri));
            when(keyring.decrypt(eq("identity-provider.oauth2.100.client-secret"), any()))
                    .thenReturn("client-secret");
            when(externalIdentityAuthentication.authenticate(eq(pending), any(), any()))
                    .thenReturn(identity);
            when(flows.proof(
                    eq(pending.getAuthenticationId()),
                    eq("browser-hash"),
                    eq(AuthMethodType.OAUTH2),
                    eq(ExternalIdentityAuthenticationProof.class),
                    any(ExternalIdentityAuthenticationProof.class)))
                    .thenReturn(verified);
            DefaultOAuth2AuthenticationService service = service(
                    flows,
                    authorizations,
                    providers,
                    oauth2Providers,
                    keyring,
                    externalIdentityAuthentication);

            AuthenticationProcess result = service.callback(
                    authorization.getAuthorizationId(), "authorization-code", null, "browser-hash");

            assertThat(result).isSameAs(verified);
            ArgumentCaptor<ExternalIdentityProfile> profileCaptor = ArgumentCaptor.forClass(ExternalIdentityProfile.class);
            ArgumentCaptor<IdentityProvider> providerCaptor = ArgumentCaptor.forClass(IdentityProvider.class);
            verify(externalIdentityAuthentication).authenticate(
                    eq(pending), providerCaptor.capture(), profileCaptor.capture());
            assertThat(providerCaptor.getValue().getProviderId()).isEqualTo(100L);
            assertThat(providerCaptor.getValue().getProtocol()).isEqualTo(IdentityProviderProtocol.OAUTH2);
            assertThat(profileCaptor.getValue().getExternalSubject()).isEqualTo("external-1");
            assertThat(profileCaptor.getValue().getDisplayName()).isEqualTo("Alice");
            assertThat(profileCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
            assertThat(profileCaptor.getValue().getEmailVerified()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldNotClaimFlowWhenProfileDoesNotContainSubject() throws Exception {
        HttpServer server = oauth2Server("{\"name\":\"Alice\"}");
        server.start();
        try {
            String baseUri = "http://127.0.0.1:" + server.getAddress().getPort();
            AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
            OAuth2AuthorizationStore authorizations = mock(OAuth2AuthorizationStore.class);
            IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
            OAuth2ProviderRepository oauth2Providers = mock(OAuth2ProviderRepository.class);
            SecretKeyring keyring = mock(SecretKeyring.class);
            ExternalIdentityAuthenticationService externalIdentityAuthentication = mock(ExternalIdentityAuthenticationService.class);
            OAuth2Authorization authorization = authorization();
            when(authorizations.consume(authorization.getAuthorizationId(), "browser-hash"))
                    .thenReturn(authorization);
            when(flows.requireProof(
                    authorization.getAuthenticationId(), "browser-hash", AuthMethodType.OAUTH2))
                    .thenReturn(pendingFlow());
            when(providers.findProvider(IdentityProviderScope.global(), 100L)).thenReturn(provider());
            when(oauth2Providers.findClient(100L, 3L)).thenReturn(client(baseUri));
            when(keyring.decrypt(eq("identity-provider.oauth2.100.client-secret"), any()))
                    .thenReturn("client-secret");
            DefaultOAuth2AuthenticationService service = service(
                    flows,
                    authorizations,
                    providers,
                    oauth2Providers,
                    keyring,
                    externalIdentityAuthentication);

            assertThatThrownBy(() -> service.callback(
                    authorization.getAuthorizationId(), "authorization-code", null, "browser-hash"))
                    .isInstanceOfSatisfying(
                            BizException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(IdentityProviderErrorCode.OAUTH2_PROFILE_INVALID));
            verify(flows, never()).claimPrimary(any(), any(), any());
            verify(externalIdentityAuthentication, never()).authenticate(any(), any(), any());
        } finally {
            server.stop(0);
        }
    }

    /** @return 注入测试替身和本机回调配置的 OAuth2 认证 Service。 */
    private DefaultOAuth2AuthenticationService service(
            AuthenticationProcessService flows,
            OAuth2AuthorizationStore authorizations,
            IdentityProviderRepository providers,
            OAuth2ProviderRepository oauth2Providers,
            SecretKeyring keyring,
            ExternalIdentityAuthenticationService externalIdentityAuthentication) {
        return new DefaultOAuth2AuthenticationService(
                flows,
                mock(AuthenticationScopeService.class),
                authorizations,
                providers,
                oauth2Providers,
                keyring,
                new OAuth2ProfileMapper(),
                externalIdentityAuthentication,
                properties(),
                RestClient.create(),
                new ObjectMapper());
    }

    /** @return 接收 Token、主 Profile 和辅助 Profile 请求的本机标准 OAuth2 服务。 */
    private HttpServer oauth2Server(String profileBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> respond(
                exchange,
                200,
                "{\"access_token\":\"access-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"));
        server.createContext("/profile", exchange -> respond(exchange, 200, profileBody));
        server.createContext("/emails", exchange -> respond(
                exchange, 200, "{\"email\":\"alice@example.com\",\"verified\":true}"));
        return server;
    }

    /** 写入测试 OAuth2 JSON 响应。 */
    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** @return 等待 OAuth2 验证的 GLOBAL 登录认证 */
    private AuthenticationProcess pendingFlow() {
        return new AuthenticationProcess()
                .setAuthenticationId("11111111-1111-1111-1111-111111111111")
                .setPurpose(AuthenticationPurpose.LOGIN)
                .setActivityId("primary-proof")
                .setScopeType(AccountScopeType.GLOBAL)
                .setCreatedAt(Instant.now())
                .setExpiresAt(Instant.now().plusSeconds(300));
    }

    /** @return ACTIVE GLOBAL OAuth2 Provider。 */
    private IdentityProvider provider() {
        return new IdentityProvider(
                100L,
                IdentityProviderScope.global(),
                "standard",
                "Standard OAuth2",
                IdentityProviderProtocol.OAUTH2,
                IdentityProviderRegistrationPolicy.JIT,
                false,
                IdentityProviderStatus.ACTIVE,
                3L);
    }

    /** @return 指向给定标准服务的 OAuth2 Client 配置。 */
    private OAuth2Client client(String baseUri) {
        return new OAuth2Client(
                100L,
                baseUri + "/authorize",
                baseUri + "/token",
                baseUri + "/profile",
                baseUri + "/emails",
                "client-1",
                new EncryptedSecret("current", new byte[12], new byte[] {1}),
                List.of("profile", "email"),
                new OAuth2ProfileMapping("$.id", "$.name", "$.email", "$.verified", null),
                3L);
    }

    /** @return callback 单次消费的 OAuth2 state 和 PKCE 快照。 */
    private OAuth2Authorization authorization() {
        return new OAuth2Authorization()
                .setAuthorizationId("m5N5cT8rrJzv1rY4xLELcwQ9Oukz8dB9")
                .setAuthenticationId(pendingFlow().getAuthenticationId())
                .setProviderId(100L)
                .setProviderVersion(3L)
                .setClientId("client-1")
                .setCodeVerifier("4yYhN5Pq8HnF2gV7sJ3rQ6mW9kL1xB0cD8eT5uI7oP2")
                .setRedirectUri("http://localhost:8080/auth/oauth2/callback")
                .setBrowserHash("browser-hash")
                .setCreatedAt(Instant.now())
                .setExpiresAt(Instant.now().plusSeconds(300));
    }

    /** @return 本机 OAuth2 callback 和完成页配置。 */
    private Sca4djIdentityProviderProperties properties() {
        Sca4djIdentityProviderProperties properties = new Sca4djIdentityProviderProperties();
        properties.setConnectionTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.getOauth2().setAuthorizationTtl(Duration.ofMinutes(5));
        properties.getOauth2().setRedirectUri(URI.create("http://localhost:8080/auth/oauth2/callback"));
        properties.getOauth2().setCompletionUri(URI.create("http://localhost:5173/login"));
        return properties;
    }
}
