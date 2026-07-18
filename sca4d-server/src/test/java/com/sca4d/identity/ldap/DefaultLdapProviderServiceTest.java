package com.sca4d.identity.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

/** 验证 LDAP URI、DN、搜索过滤器、服务账号加密和父版本并发条件。 */
class DefaultLdapProviderServiceTest {

    @Test
    void shouldSaveAnonymousStartTlsConfigurationWithoutPassword() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        LdapProviderRepository repository = mock(LdapProviderRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L)).thenReturn(provider(7L));
        DefaultLdapProviderService service = new DefaultLdapProviderService(
                providerRepository,
                repository,
                mock(SecretKeyring.class),
                mock(LdapDirectoryClient.class));

        LdapProviderConfiguration result = service.replace(
                scope,
                123L,
                "ldap://directory.example.com:389",
                "dc=example,dc=com",
                null,
                null,
                "ou=people",
                "(&(objectClass=person)(uid={0}))",
                "entryUUID",
                "displayName",
                "mail",
                LdapTransportSecurity.STARTTLS,
                7L);

        assertThat(result.getServiceAccountDn()).isNull();
        assertThat(result.getServiceAccountPasswordConfigured()).isFalse();
        assertThat(result.getVersion()).isEqualTo(8L);
        verify(providerRepository)
                .updateConfigurationVersion(123L, scope, IdentityProviderProtocol.LDAP, 7L);
        verify(repository).saveConfiguration(result, null, false, false);
    }

    @Test
    void shouldEncryptNewServiceAccountPasswordAndRetainUnchangedPassword() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        LdapProviderRepository repository = mock(LdapProviderRepository.class);
        SecretKeyring keyring = mock(SecretKeyring.class);
        EncryptedSecret encrypted = new EncryptedSecret("current", new byte[12], new byte[] {1});
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L)).thenReturn(provider(7L));
        when(keyring.encrypt(
                "identity-provider.ldap.123.service-account-password", "password"))
                .thenReturn(encrypted);
        DefaultLdapProviderService service = new DefaultLdapProviderService(
                providerRepository, repository, keyring, mock(LdapDirectoryClient.class));

        LdapProviderConfiguration created = service.replace(
                scope,
                123L,
                "ldaps://directory.example.com:636",
                "dc=example,dc=com",
                "cn=reader,dc=example,dc=com",
                "password",
                "ou=people",
                "(uid={0})",
                "entryUUID",
                null,
                null,
                LdapTransportSecurity.LDAPS,
                7L);
        verify(repository).saveConfiguration(created, encrypted, true, false);

        when(repository.findConfiguration(123L, 7L)).thenReturn(configuration(7L));
        LdapProviderConfiguration updated = service.replace(
                scope,
                123L,
                "ldaps://directory.example.com:636",
                "dc=example,dc=com",
                "cn=reader,dc=example,dc=com",
                null,
                "ou=people",
                "(uid={0})",
                "entryUUID",
                null,
                null,
                LdapTransportSecurity.LDAPS,
                7L);
        verify(repository).saveConfiguration(updated, null, false, true);
    }

    @Test
    void shouldRejectMismatchedTransportFilterAndServiceAccountWithoutPassword() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        LdapProviderRepository repository = mock(LdapProviderRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L)).thenReturn(provider(7L));
        DefaultLdapProviderService service = new DefaultLdapProviderService(
                providerRepository,
                repository,
                mock(SecretKeyring.class),
                mock(LdapDirectoryClient.class));

        assertError(
                () -> replace(service, "ldap://directory.example.com", "(uid={0})", null, null),
                IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        assertError(
                () -> replace(
                        service,
                        "ldaps://directory.example.com",
                        "(|(uid={0})(mail={1}))",
                        null,
                        null),
                IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID);
        assertError(
                () -> replace(
                        service,
                        "ldaps://directory.example.com",
                        "(uid={0})",
                        "cn=reader,dc=example,dc=com",
                        null),
                IdentityProviderErrorCode.PROVIDER_SECRET_REQUIRED);
        verify(providerRepository, never()).updateConfigurationVersion(any(), any(), any(), anyLong());
    }

    @Test
    void shouldRequireNewPasswordWhenServiceAccountDnChangesAndDelegateActivationCheck() {
        IdentityProviderRepository providerRepository = mock(IdentityProviderRepository.class);
        LdapProviderRepository repository = mock(LdapProviderRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providerRepository.findProvider(scope, 123L)).thenReturn(provider(7L));
        when(repository.findConfiguration(123L, 7L)).thenReturn(configuration(7L));
        when(repository.isConfigured(123L)).thenReturn(true);
        DefaultLdapProviderService service = new DefaultLdapProviderService(
                providerRepository,
                repository,
                mock(SecretKeyring.class),
                mock(LdapDirectoryClient.class));

        assertError(
                () -> replace(
                        service,
                        "ldaps://directory.example.com",
                        "(uid={0})",
                        "cn=other,dc=example,dc=com",
                        null),
                IdentityProviderErrorCode.PROVIDER_SECRET_REQUIRED);
        assertThat(service.protocol()).isEqualTo(IdentityProviderProtocol.LDAP);
        assertThat(service.canActivate(123L)).isTrue();
    }

    /** 调用固定测试数据的 LDAP 配置替换。 */
    private void replace(
            DefaultLdapProviderService service,
            String url,
            String filter,
            String serviceAccountDn,
            String password) {
        replace(service, url, filter, serviceAccountDn, password, LdapTransportSecurity.LDAPS);
    }

    /** 调用固定测试数据和指定传输方式的 LDAP 配置替换。 */
    private void replace(
            DefaultLdapProviderService service,
            String url,
            String filter,
            String serviceAccountDn,
            String password,
            LdapTransportSecurity transportSecurity) {
        service.replace(
                IdentityProviderScope.global(),
                123L,
                url,
                "dc=example,dc=com",
                serviceAccountDn,
                password,
                "ou=people",
                filter,
                "entryUUID",
                null,
                null,
                transportSecurity,
                7L);
    }

    /** 验证业务异常使用指定 IdentityProvider 错误码。 */
    private void assertError(Runnable operation, IdentityProviderErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    /** @return 停用 GLOBAL LDAP Provider 测试数据 */
    private IdentityProvider provider(long version) {
        return new IdentityProvider(
                123L,
                IdentityProviderScope.global(),
                "ldap",
                "LDAP",
                IdentityProviderProtocol.LDAP,
                IdentityProviderRegistrationPolicy.DISABLED,
                false,
                IdentityProviderStatus.DISABLED,
                version);
    }

    /** @return 已配置服务账号的 LDAP 测试数据 */
    private LdapProviderConfiguration configuration(long version) {
        return new LdapProviderConfiguration(
                123L,
                "ldaps://directory.example.com:636",
                "dc=example,dc=com",
                "cn=reader,dc=example,dc=com",
                true,
                "ou=people",
                "(uid={0})",
                "entryUUID",
                null,
                null,
                LdapTransportSecurity.LDAPS,
                version);
    }
}
