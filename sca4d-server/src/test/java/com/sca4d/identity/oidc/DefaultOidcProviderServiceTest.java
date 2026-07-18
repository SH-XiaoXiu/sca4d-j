package com.sca4d.identity.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.common.security.SecretKeyring;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import com.sca4d.identity.repository.IdentityProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

/** 验证 OIDC 配置加密、父版本并发条件、协议限制和启用检查。 */
class DefaultOidcProviderServiceTest {

    @Test
    void shouldEncryptSecretAndAdvanceParentVersionWhenCreatingConfiguration() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        OidcProviderRepository repository = mock(OidcProviderRepository.class);
        SecretKeyring keyring = mock(SecretKeyring.class);
        EncryptedSecret encrypted = new EncryptedSecret("current", new byte[12], new byte[] {1});
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L))
                .thenReturn(provider(IdentityProviderProtocol.OIDC, 7L));
        when(keyring.encrypt("identity-provider.oidc.123.client-secret", "secret"))
                .thenReturn(encrypted);
        DefaultOidcProviderService service = new DefaultOidcProviderService(
                providerRepository, repository, keyring, RestClient.create(), new ObjectMapper());

        OidcProviderConfiguration result = service.replace(
                scope,
                123L,
                " https://issuer.example.com/tenant ",
                " client-id ",
                "secret",
                List.of("openid", "profile", "profile"),
                7L);

        assertThat(result.getIssuer()).isEqualTo("https://issuer.example.com/tenant");
        assertThat(result.getClientId()).isEqualTo("client-id");
        assertThat(result.getClientSecretConfigured()).isTrue();
        assertThat(result.getScopes()).containsExactly("openid", "profile");
        assertThat(result.getVersion()).isEqualTo(8L);
        verify(providerRepository)
                .updateConfigurationVersion(123L, scope, IdentityProviderProtocol.OIDC, 7L);
        verify(repository).saveConfiguration(
                123L,
                "https://issuer.example.com/tenant",
                "client-id",
                encrypted,
                "openid profile",
                false);
    }

    @Test
    void shouldRetainEncryptedSecretWhenReplacingExistingConfiguration() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        OidcProviderRepository repository = mock(OidcProviderRepository.class);
        SecretKeyring keyring = mock(SecretKeyring.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L))
                .thenReturn(provider(IdentityProviderProtocol.OIDC, 7L));
        when(repository.findConfiguration(123L, 7L))
                .thenReturn(new OidcProviderConfiguration(
                        123L,
                        "https://old.example.com",
                        "old-client",
                        true,
                        List.of("openid"),
                        7L));
        DefaultOidcProviderService service = new DefaultOidcProviderService(
                providerRepository, repository, keyring, RestClient.create(), new ObjectMapper());

        service.replace(
                scope,
                123L,
                "https://issuer.example.com",
                "client-id",
                null,
                List.of("openid"),
                7L);

        verify(keyring, never()).encrypt(any(), any());
        verify(repository).saveConfiguration(
                123L,
                "https://issuer.example.com",
                "client-id",
                null,
                "openid",
                true);
    }

    @Test
    void shouldRejectMissingInitialSecretAndInvalidIssuerOrScopes() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        OidcProviderRepository repository = mock(OidcProviderRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L))
                .thenReturn(provider(IdentityProviderProtocol.OIDC, 7L));
        DefaultOidcProviderService service = new DefaultOidcProviderService(
                providerRepository,
                repository,
                mock(SecretKeyring.class),
                RestClient.create(),
                new ObjectMapper());

        assertError(
                () -> service.replace(
                        scope,
                        123L,
                        "https://issuer.example.com",
                        "client-id",
                        null,
                        List.of("openid"),
                        7L),
                IdentityProviderErrorCode.PROVIDER_SECRET_REQUIRED);
        assertError(
                () -> service.replace(
                        scope,
                        123L,
                        "http://issuer.example.com",
                        "client-id",
                        "secret",
                        List.of("openid"),
                        7L),
                IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        assertError(
                () -> service.replace(
                        scope,
                        123L,
                        "https://issuer.example.com",
                        "client-id",
                        "secret",
                        List.of("profile"),
                        7L),
                IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        verify(providerRepository, never())
                .updateConfigurationVersion(any(), any(), any(), eq(7L));
    }

    @Test
    void shouldRejectProtocolMismatchAndDelegateActivationCheck() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        OidcProviderRepository repository = mock(OidcProviderRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L))
                .thenReturn(provider(IdentityProviderProtocol.LDAP, 7L));
        when(repository.isConfigured(123L)).thenReturn(true);
        DefaultOidcProviderService service = new DefaultOidcProviderService(
                providerRepository,
                repository,
                mock(SecretKeyring.class),
                RestClient.create(),
                new ObjectMapper());

        assertError(
                () -> service.configuration(scope, 123L),
                IdentityProviderErrorCode.PROVIDER_PROTOCOL_MISMATCH);
        assertThat(service.protocol()).isEqualTo(IdentityProviderProtocol.OIDC);
        assertThat(service.canActivate(123L)).isTrue();
    }

    @Test
    void shouldReadAndValidateDiscoveryMetadata() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        OidcProviderRepository repository = mock(OidcProviderRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        String issuer = "http://127.0.0.1:18080/tenant";
        when(providerRepository.findProvider(scope, 123L))
                .thenReturn(provider(IdentityProviderProtocol.OIDC, 7L));
        when(repository.findConfiguration(123L, 7L))
                .thenReturn(new OidcProviderConfiguration(
                        123L,
                        issuer,
                        "client-id",
                        true,
                        List.of("openid"),
                        7L));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(issuer + "/.well-known/openid-configuration"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                                {
                                  "issuer": "http://127.0.0.1:18080/tenant",
                                  "authorization_endpoint": "http://127.0.0.1:18080/authorize",
                                  "token_endpoint": "http://127.0.0.1:18080/token",
                                  "userinfo_endpoint": "http://127.0.0.1:18080/userinfo",
                                  "jwks_uri": "http://127.0.0.1:18080/jwks",
                                  "scopes_supported": ["openid", "profile"],
                                  "response_types_supported": ["code"],
                                  "subject_types_supported": ["public"],
                                  "id_token_signing_alg_values_supported": ["RS256"],
                                  "code_challenge_methods_supported": ["S256"]
                                }
                                """,
                        MediaType.APPLICATION_JSON));
        DefaultOidcProviderService service = new DefaultOidcProviderService(
                providerRepository,
                repository,
                mock(SecretKeyring.class),
                builder.build(),
                new ObjectMapper());

        OidcDiscovery discovery = service.discover(scope, 123L);

        assertThat(discovery.getIssuer()).isEqualTo(issuer);
        assertThat(discovery.getAuthorizationUri()).isEqualTo("http://127.0.0.1:18080/authorize");
        assertThat(discovery.getTokenUri()).isEqualTo("http://127.0.0.1:18080/token");
        assertThat(discovery.getJwksUri()).isEqualTo("http://127.0.0.1:18080/jwks");
        assertThat(discovery.getScopesSupported()).containsExactly("openid", "profile");
        assertThat(discovery.getCodeChallengeMethodsSupported()).containsExactly("S256");
        server.verify();
    }

    @Test
    void shouldRejectDiscoveryResponseLargerThanConfiguredSafetyBoundary() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        OidcProviderRepository repository = mock(OidcProviderRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        String issuer = "http://127.0.0.1:18080/tenant";
        when(providerRepository.findProvider(scope, 123L))
                .thenReturn(provider(IdentityProviderProtocol.OIDC, 7L));
        when(repository.findConfiguration(123L, 7L))
                .thenReturn(new OidcProviderConfiguration(
                        123L,
                        issuer,
                        "client-id",
                        true,
                        List.of("openid"),
                        7L));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(issuer + "/.well-known/openid-configuration"))
                .andRespond(withSuccess(
                        "{\"padding\":\"" + "x".repeat(1024 * 1024) + "\"}",
                        MediaType.APPLICATION_JSON));
        DefaultOidcProviderService service = new DefaultOidcProviderService(
                providerRepository,
                repository,
                mock(SecretKeyring.class),
                builder.build(),
                new ObjectMapper());

        assertError(
                () -> service.discover(scope, 123L),
                IdentityProviderErrorCode.PROVIDER_DISCOVERY_INVALID);
        server.verify();
    }

    /** 验证业务异常使用指定 IdentityProvider 错误码。 */
    private void assertError(Runnable operation, IdentityProviderErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    /** @return 指定协议和版本的停用 GLOBAL Provider */
    private IdentityProvider provider(IdentityProviderProtocol protocol, long version) {
        return new IdentityProvider(
                123L,
                IdentityProviderScope.global(),
                "provider",
                "Provider",
                protocol,
                IdentityProviderRegistrationPolicy.DISABLED,
                false,
                IdentityProviderStatus.DISABLED,
                version);
    }

}
