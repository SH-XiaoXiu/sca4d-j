package com.sca4d.identity.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.AuthenticationPurpose;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.identifier.AccountIdentifierService;
import com.sca4d.iam.identifier.AccountIdentifierStatus;
import com.sca4d.iam.identifier.AccountIdentifierType;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import org.junit.jupiter.api.Test;

/** 验证三种外部协议共用的绑定、可信邮箱关联和 JIT 注册规则。 */
class DefaultExternalIdentityAuthenticationServiceTest {

    @Test
    void shouldLinkExternalIdentityToAuthenticatedFlowAccount() {
        ExternalIdentityService identities = mock(ExternalIdentityService.class);
        ExternalIdentityVerification expected = verification(30L);
        IdentityProvider provider = provider(IdentityProviderRegistrationPolicy.DISABLED, false);
        ExternalIdentityProfile profile = profile(true);
        when(identities.link(provider.getScope(), provider.getProviderId(), 30L, profile))
                .thenReturn(expected);
        DefaultExternalIdentityAuthenticationService service = service(
                identities,
                mock(ExternalIdentityRegistrationService.class),
                mock(AccountIdentifierService.class));

        ExternalIdentityVerification actual = service.authenticate(
                flow(AuthenticationPurpose.LINKING, 30L), provider, profile);

        assertThat(actual).isSameAs(expected);
        verify(identities, never()).authenticate(any(), anyLong(), any());
    }

    @Test
    void shouldRejectLinkingFlowWithoutAuthenticatedAccount() {
        ExternalIdentityService identities = mock(ExternalIdentityService.class);
        DefaultExternalIdentityAuthenticationService service = service(
                identities,
                mock(ExternalIdentityRegistrationService.class),
                mock(AccountIdentifierService.class));

        assertThatThrownBy(() -> service.authenticate(
                flow(AuthenticationPurpose.LINKING, null),
                provider(IdentityProviderRegistrationPolicy.DISABLED, false),
                profile(true)))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE));
        verify(identities, never()).link(any(), anyLong(), anyLong(), any());
    }

    @Test
    void shouldReturnExistingExternalIdentityWithoutEmailLookupOrRegistration() {
        ExternalIdentityService identities = mock(ExternalIdentityService.class);
        ExternalIdentityRegistrationService registrations = mock(ExternalIdentityRegistrationService.class);
        AccountIdentifierService identifiers = mock(AccountIdentifierService.class);
        IdentityProvider provider = provider(IdentityProviderRegistrationPolicy.JIT, true);
        ExternalIdentityProfile profile = profile(true);
        ExternalIdentityVerification expected = verification(30L);
        when(identities.authenticate(provider.getScope(), provider.getProviderId(), profile))
                .thenReturn(expected);
        DefaultExternalIdentityAuthenticationService service = service(identities, registrations, identifiers);

        ExternalIdentityVerification actual = service.authenticate(
                flow(AuthenticationPurpose.LOGIN, null), provider, profile);

        assertThat(actual).isSameAs(expected);
        verify(identifiers, never()).findActive(any(), any(), any(), any());
        verify(registrations, never()).register(any(), anyLong(), any());
    }

    @Test
    void shouldLinkVerifiedEmailToUniqueActiveAccount() {
        ExternalIdentityService identities = mock(ExternalIdentityService.class);
        AccountIdentifierService identifiers = mock(AccountIdentifierService.class);
        IdentityProvider provider = provider(IdentityProviderRegistrationPolicy.DISABLED, true);
        ExternalIdentityProfile profile = profile(true);
        IamAccountIdentifierEntity identifier = new IamAccountIdentifierEntity().setId(40L).setAccountId(30L).setScopeType(AccountScopeType.GLOBAL)
                .setScopeId(null).setIdentifierType(AccountIdentifierType.EMAIL).setValue("alice@example.com").setContactId(50L)
                .setStatus(AccountIdentifierStatus.ACTIVE).setVersion(2L);
        ExternalIdentityVerification expected = verification(30L);
        when(identifiers.findActive(
                AccountScopeType.GLOBAL,
                null,
                AccountIdentifierType.EMAIL,
                "alice@example.com"))
                .thenReturn(identifier);
        when(identities.link(provider.getScope(), provider.getProviderId(), 30L, profile))
                .thenReturn(expected);
        DefaultExternalIdentityAuthenticationService service = service(
                identities, mock(ExternalIdentityRegistrationService.class), identifiers);

        ExternalIdentityVerification actual = service.authenticate(
                flow(AuthenticationPurpose.LOGIN, null), provider, profile);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void shouldNotAutoLinkUnverifiedEmail() {
        ExternalIdentityService identities = mock(ExternalIdentityService.class);
        AccountIdentifierService identifiers = mock(AccountIdentifierService.class);
        DefaultExternalIdentityAuthenticationService service = service(
                identities, mock(ExternalIdentityRegistrationService.class), identifiers);

        assertThatThrownBy(() -> service.authenticate(
                flow(AuthenticationPurpose.LOGIN, null),
                provider(IdentityProviderRegistrationPolicy.DISABLED, true),
                profile(false)))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.EXTERNAL_REGISTRATION_DISABLED));
        verify(identifiers, never()).findActive(any(), any(), any(), any());
    }

    @Test
    void shouldRegisterNewExternalIdentityWhenJitIsEnabled() {
        ExternalIdentityService identities = mock(ExternalIdentityService.class);
        ExternalIdentityRegistrationService registrations = mock(ExternalIdentityRegistrationService.class);
        IdentityProvider provider = provider(IdentityProviderRegistrationPolicy.JIT, false);
        ExternalIdentityProfile profile = profile(true);
        ExternalIdentityVerification expected = verification(30L);
        when(registrations.register(provider.getScope(), provider.getProviderId(), profile))
                .thenReturn(expected);
        DefaultExternalIdentityAuthenticationService service = service(
                identities, registrations, mock(AccountIdentifierService.class));

        ExternalIdentityVerification actual = service.authenticate(
                flow(AuthenticationPurpose.REGISTRATION, null), provider, profile);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void shouldReadConcurrentJitWinnerAfterExternalSubjectConflict() {
        ExternalIdentityService identities = mock(ExternalIdentityService.class);
        ExternalIdentityRegistrationService registrations = mock(ExternalIdentityRegistrationService.class);
        IdentityProvider provider = provider(IdentityProviderRegistrationPolicy.JIT, false);
        ExternalIdentityProfile profile = profile(true);
        ExternalIdentityVerification concurrent = verification(31L);
        when(identities.authenticate(provider.getScope(), provider.getProviderId(), profile))
                .thenReturn(null, concurrent);
        when(registrations.register(provider.getScope(), provider.getProviderId(), profile))
                .thenThrow(new BizException(IdentityProviderErrorCode.EXTERNAL_IDENTITY_ALREADY_LINKED));
        DefaultExternalIdentityAuthenticationService service = service(
                identities, registrations, mock(AccountIdentifierService.class));

        ExternalIdentityVerification actual = service.authenticate(
                flow(AuthenticationPurpose.LOGIN, null), provider, profile);

        assertThat(actual).isSameAs(concurrent);
        verify(identities, times(2)).authenticate(provider.getScope(), provider.getProviderId(), profile);
    }

    /** @return 注入测试替身的共享外部身份认证 Service。 */
    private DefaultExternalIdentityAuthenticationService service(
            ExternalIdentityService identities,
            ExternalIdentityRegistrationService registrations,
            AccountIdentifierService identifiers) {
        return new DefaultExternalIdentityAuthenticationService(identities, registrations, identifiers);
    }

    /** @return 指定目的和 Account 的 GLOBAL 认证 */
    private AuthenticationProcess flow(AuthenticationPurpose purpose, Long accountId) {
        return new AuthenticationProcess()
                .setPurpose(purpose)
                .setScopeType(AccountScopeType.GLOBAL)
                .setAccountId(accountId);
    }

    /** @return 指定注册策略和邮箱关联开关的 ACTIVE OIDC Provider。 */
    private IdentityProvider provider(
            IdentityProviderRegistrationPolicy registrationPolicy, boolean emailAutoLink) {
        return new IdentityProvider(
                100L,
                IdentityProviderScope.global(),
                "external",
                "External Provider",
                IdentityProviderProtocol.OIDC,
                registrationPolicy,
                emailAutoLink,
                IdentityProviderStatus.ACTIVE,
                3L);
    }

    /** @return 固定 external subject 的上游资料。 */
    private ExternalIdentityProfile profile(boolean emailVerified) {
        return new ExternalIdentityProfile(
                "external-1",
                "Alice",
                "alice@example.com",
                emailVerified,
                "https://example.com/avatar.png");
    }

    /** @return 固定 Subject 和 Account 的 ExternalIdentity 验证结果。 */
    private ExternalIdentityVerification verification(long accountId) {
        return new ExternalIdentityVerification(
                new Subject(20L, "Alice"),
                new Account(accountId, 20L, AccountScopeType.GLOBAL, null, false),
                200L,
                4L);
    }
}
