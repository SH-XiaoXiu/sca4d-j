package com.sca4d.identity.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import java.util.List;

/** 验证 OAuth2 配置加密、JSONPath 校验、父版本并发条件和启用检查。 */
class DefaultOAuth2ProviderServiceTest {

    @Test
    void shouldValidateMappingEncryptSecretAndAdvanceParentVersion() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        OAuth2ProviderRepository repository = mock(OAuth2ProviderRepository.class);
        SecretKeyring keyring = mock(SecretKeyring.class);
        EncryptedSecret encrypted = new EncryptedSecret("current", new byte[12], new byte[] {1});
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L)).thenReturn(provider(7L));
        when(keyring.encrypt("identity-provider.oauth2.123.client-secret", "secret"))
                .thenReturn(encrypted);
        DefaultOAuth2ProviderService service = new DefaultOAuth2ProviderService(
                providerRepository, repository, keyring, new OAuth2ProfileMapper());

        OAuth2ProviderConfiguration result = service.replace(
                scope,
                123L,
                "https://id.example.com/authorize",
                "https://id.example.com/token",
                "https://api.example.com/profile",
                "https://api.example.com/emails",
                "client-id",
                "secret",
                List.of("profile", "email", "email"),
                new OAuth2ProfileMapping(
                        "$.id", "$.name", "$.emails[0].value", "$.emails[0].verified", "$.avatar"),
                7L);

        assertThat(result.getScopes()).containsExactly("profile", "email");
        assertThat(result.getProfileMapping().getSubjectPath()).isEqualTo("$.id");
        assertThat(result.getVersion()).isEqualTo(8L);
        verify(providerRepository)
                .updateConfigurationVersion(123L, scope, IdentityProviderProtocol.OAUTH2, 7L);
        verify(repository).saveConfiguration(result, encrypted, false);
    }

    @Test
    void shouldRetainSecretAndRejectInvalidJsonPath() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        OAuth2ProviderRepository repository = mock(OAuth2ProviderRepository.class);
        SecretKeyring keyring = mock(SecretKeyring.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L)).thenReturn(provider(7L));
        when(repository.findConfiguration(123L, 7L)).thenReturn(configuration(7L));
        DefaultOAuth2ProviderService service = new DefaultOAuth2ProviderService(
                providerRepository, repository, keyring, new OAuth2ProfileMapper());

        OAuth2ProviderConfiguration result = service.replace(
                scope,
                123L,
                "https://id.example.com/authorize",
                "https://id.example.com/token",
                "https://api.example.com/profile",
                null,
                "client-id",
                null,
                List.of("profile"),
                new OAuth2ProfileMapping("$.id", null, null, null, null),
                7L);

        verify(keyring, never()).encrypt(any(), any());
        verify(repository).saveConfiguration(result, null, true);

        assertError(
                () -> service.replace(
                        scope,
                        123L,
                        "https://id.example.com/authorize",
                        "https://id.example.com/token",
                        "https://api.example.com/profile",
                        null,
                        "client-id",
                        null,
                        List.of("profile"),
                        new OAuth2ProfileMapping("id", null, null, null, null),
                        7L),
                IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
    }

    @Test
    void shouldRequireInitialSecretAndDelegateActivationCheck() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        OAuth2ProviderRepository repository = mock(OAuth2ProviderRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L)).thenReturn(provider(7L));
        when(repository.isConfigured(123L)).thenReturn(true);
        DefaultOAuth2ProviderService service = new DefaultOAuth2ProviderService(
                providerRepository,
                repository,
                mock(SecretKeyring.class),
                new OAuth2ProfileMapper());

        assertError(
                () -> service.replace(
                        scope,
                        123L,
                        "https://id.example.com/authorize",
                        "https://id.example.com/token",
                        "https://api.example.com/profile",
                        null,
                        "client-id",
                        null,
                        List.of("profile"),
                        new OAuth2ProfileMapping("$.id", null, null, null, null),
                        7L),
                IdentityProviderErrorCode.PROVIDER_SECRET_REQUIRED);
        assertThat(service.protocol()).isEqualTo(IdentityProviderProtocol.OAUTH2);
        assertThat(service.canActivate(123L)).isTrue();
    }

    /** 验证业务异常使用指定 IdentityProvider 错误码。 */
    private void assertError(Runnable operation, IdentityProviderErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    /** @return 停用 GLOBAL OAuth2 Provider 测试数据 */
    private IdentityProvider provider(long version) {
        return new IdentityProvider(
                123L,
                IdentityProviderScope.global(),
                "oauth2",
                "OAuth2",
                IdentityProviderProtocol.OAUTH2,
                IdentityProviderRegistrationPolicy.DISABLED,
                false,
                IdentityProviderStatus.DISABLED,
                version);
    }

    /** @return 已保存且不包含秘密的 OAuth2 配置测试数据 */
    private OAuth2ProviderConfiguration configuration(long version) {
        return new OAuth2ProviderConfiguration(
                123L,
                "https://old.example.com/authorize",
                "https://old.example.com/token",
                "https://old.example.com/profile",
                null,
                "old-client",
                true,
                List.of("profile"),
                new OAuth2ProfileMapping("$.id", null, null, null, null),
                version);
    }
}
