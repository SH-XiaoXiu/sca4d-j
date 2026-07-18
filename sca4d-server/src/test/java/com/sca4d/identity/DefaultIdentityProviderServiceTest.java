package com.sca4d.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import com.sca4d.identity.repository.IdentityProviderRepository;
import com.sca4d.identity.service.DefaultIdentityProviderService;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证 Provider 通用字段、协议启用检查、乐观锁和删除限制。 */
class DefaultIdentityProviderServiceTest {

    @Test
    void shouldCreateDisabledProviderWithTrimmedCommonFields() {
        IdentityProviderRepository repository = mock(IdentityProviderRepository.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(ids.nextId()).thenReturn(123L);
        DefaultIdentityProviderService service = new DefaultIdentityProviderService(repository, List.of(), ids);
        IdentityProviderScope scope = IdentityProviderScope.tenant(20L);

        assertThat(service.create(
                scope,
                "corp-oidc",
                " 企业身份源 ",
                IdentityProviderProtocol.OIDC,
                IdentityProviderRegistrationPolicy.JIT,
                true))
                .isEqualTo(123L);

        verify(repository).createProvider(
                123L,
                scope,
                "corp-oidc",
                "企业身份源",
                IdentityProviderProtocol.OIDC,
                IdentityProviderRegistrationPolicy.JIT,
                true);
    }

    @Test
    void shouldRejectInvalidCodeAndMissingProtocolConfiguration() {
        IdentityProviderRepository repository = mock(IdentityProviderRepository.class);
        DefaultIdentityProviderService service = new DefaultIdentityProviderService(
                repository, List.of(), mock(IdGenerator.class));

        assertThatThrownBy(() -> service.create(
                IdentityProviderScope.global(),
                "OIDC_1",
                "企业身份源",
                IdentityProviderProtocol.OIDC,
                IdentityProviderRegistrationPolicy.DISABLED,
                false))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.PROVIDER_CONFIGURATION_INVALID));

        IdentityProvider provider = provider(IdentityProviderStatus.DISABLED, 7L);
        when(repository.findProvider(IdentityProviderScope.global(), 123L)).thenReturn(provider);
        assertThatThrownBy(() -> service.changeStatus(
                IdentityProviderScope.global(), 123L, IdentityProviderStatus.ACTIVE, 7L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.PROVIDER_PROTOCOL_CONFIGURATION_REQUIRED));
        verify(repository, never()).updateStatus(any(), any(), any(), any(), eq(7L));
    }

    @Test
    void shouldUseMatchingProtocolCheckBeforeActivation() {
        IdentityProviderRepository repository = mock(IdentityProviderRepository.class);
        IdentityProviderActivationCheck oidc = mock(IdentityProviderActivationCheck.class);
        IdentityProviderActivationCheck ldap = mock(IdentityProviderActivationCheck.class);
        when(oidc.protocol()).thenReturn(IdentityProviderProtocol.OIDC);
        when(oidc.canActivate(123L)).thenReturn(true);
        when(ldap.protocol()).thenReturn(IdentityProviderProtocol.LDAP);
        when(repository.findProvider(IdentityProviderScope.global(), 123L))
                .thenReturn(provider(IdentityProviderStatus.DISABLED, 7L));
        DefaultIdentityProviderService service = new DefaultIdentityProviderService(
                repository, List.of(ldap, oidc), mock(IdGenerator.class));

        service.changeStatus(
                IdentityProviderScope.global(), 123L, IdentityProviderStatus.ACTIVE, 7L);

        verify(oidc).canActivate(123L);
        verify(ldap, never()).canActivate(any());
        verify(repository).updateStatus(
                123L,
                IdentityProviderScope.global(),
                IdentityProviderStatus.DISABLED,
                IdentityProviderStatus.ACTIVE,
                7L);
    }

    @Test
    void shouldRejectStaleVersionAndProvidersThatCannotBeDeleted() {
        IdentityProviderRepository repository = mock(IdentityProviderRepository.class);
        when(repository.findProvider(IdentityProviderScope.global(), 123L))
                .thenReturn(provider(IdentityProviderStatus.DISABLED, 8L));
        DefaultIdentityProviderService service = new DefaultIdentityProviderService(
                repository, List.of(), mock(IdGenerator.class));

        assertThatThrownBy(() -> service.update(
                IdentityProviderScope.global(),
                123L,
                "企业身份源",
                IdentityProviderRegistrationPolicy.DISABLED,
                false,
                7L))
                .isInstanceOf(ConcurrencyConflictException.class);

        when(repository.findProvider(IdentityProviderScope.global(), 123L))
                .thenReturn(provider(IdentityProviderStatus.ACTIVE, 8L));
        assertThatThrownBy(() -> service.delete(IdentityProviderScope.global(), 123L, 8L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.PROVIDER_ACTIVE));

        when(repository.findProvider(IdentityProviderScope.global(), 123L))
                .thenReturn(provider(IdentityProviderStatus.DISABLED, 8L));
        when(repository.hasExternalIdentities(123L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(IdentityProviderScope.global(), 123L, 8L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.PROVIDER_IN_USE));
        verify(repository, never()).deleteProvider(any(), any(), any(), any(Long.class));
    }

    @Test
    void shouldDeleteProtocolConfigurationWithDisabledProvider() {
        IdentityProviderRepository repository = mock(IdentityProviderRepository.class);
        when(repository.findProvider(IdentityProviderScope.global(), 123L))
                .thenReturn(provider(IdentityProviderStatus.DISABLED, 8L));
        DefaultIdentityProviderService service = new DefaultIdentityProviderService(
                repository, List.of(), mock(IdGenerator.class));

        service.delete(IdentityProviderScope.global(), 123L, 8L);

        verify(repository).deleteProvider(
                123L, IdentityProviderScope.global(), IdentityProviderProtocol.OIDC, 8L);
    }

    /** @return GLOBAL OIDC Provider 测试数据 */
    private IdentityProvider provider(IdentityProviderStatus status, long version) {
        return new IdentityProvider(
                123L,
                IdentityProviderScope.global(),
                "corp-oidc",
                "企业身份源",
                IdentityProviderProtocol.OIDC,
                IdentityProviderRegistrationPolicy.DISABLED,
                false,
                status,
                version);
    }
}
