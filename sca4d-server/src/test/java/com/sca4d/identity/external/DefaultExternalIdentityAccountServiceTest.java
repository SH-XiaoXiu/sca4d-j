package com.sca4d.identity.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthenticationProcessService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.CredentialType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.concurrent.lock.DistributedLock;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.credential.CredentialService;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.subject.SubjectStatus;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import com.sca4d.identity.repository.IdentityProviderRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** 验证 ExternalIdentity 自助解除关联的归属、锁定保护、STEP_UP 和 Session 失效。 */
class DefaultExternalIdentityAccountServiceTest {

    @Test
    void shouldListOnlyActiveProvidersForOwnedAccountScope() {
        AccountService accounts = mock(AccountService.class);
        IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
        IamAccountEntity account = new IamAccountEntity().setId(30L).setSubjectId(20L).setScopeType(AccountScopeType.TENANT).setScopeId(40L)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(2L);
        IdentityProviderScope scope = IdentityProviderScope.tenant(40L);
        when(accounts.require(30L)).thenReturn(account);
        when(providers.findProviders(scope))
                .thenReturn(List.of(
                        new IdentityProvider(
                                100L,
                                scope,
                                "active",
                                "Active",
                                IdentityProviderProtocol.OIDC,
                                IdentityProviderRegistrationPolicy.DISABLED,
                                false,
                                IdentityProviderStatus.ACTIVE,
                                3L),
                        new IdentityProvider(
                                101L,
                                scope,
                                "disabled",
                                "Disabled",
                                IdentityProviderProtocol.LDAP,
                                IdentityProviderRegistrationPolicy.DISABLED,
                                false,
                                IdentityProviderStatus.DISABLED,
                                2L)));
        DefaultExternalIdentityAccountService service = new DefaultExternalIdentityAccountService(
                accounts,
                mock(CredentialService.class),
                mock(ExternalIdentityRepository.class),
                providers,
                mock(AuthenticationProcessService.class),
                mock(Authentication.class));

        assertThat(service.providers(20L, 30L))
                .extracting(IdentityProvider::getCode)
                .containsExactly("active");
        verify(providers).findProviders(scope);
    }

    @Test
    void shouldHoldSubjectLockOutsideDeletionTransaction() throws Exception {
        var method = DefaultExternalIdentityAccountService.class.getMethod(
                "delete",
                Subject.class,
                Account.class,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class);

        assertThat(method.getAnnotation(DistributedLock.class).key())
                .isEqualTo("'subject:' + #subject.subjectId");
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void shouldRejectRemovingLastPrimaryLoginMethod() {
        AccountService accounts = mock(AccountService.class);
        CredentialService credentials = mock(CredentialService.class);
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        when(accounts.require(30L)).thenReturn(account());
        when(identities.findLink(200L)).thenReturn(link());
        when(credentials.findBySubject(20L)).thenReturn(List.of());
        when(identities.hasOtherActiveLoginIdentity(30L, 200L)).thenReturn(false);
        DefaultExternalIdentityAccountService service = service(
                accounts,
                credentials,
                identities,
                mock(AuthenticationProcessService.class),
                mock(Authentication.class));

        assertThatThrownBy(() -> service.delete(
                subject(),
                authenticatedAccount(),
                30L,
                200L,
                4L,
                "step-up-flow",
                "browser-hash"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.EXTERNAL_IDENTITY_REMOVAL_WOULD_LOCK_ACCOUNT));
        verify(identities, never()).delete(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldAllowRemovingDisabledProviderIdentityWithoutAnotherLoginMethod() {
        AccountService accounts = mock(AccountService.class);
        CredentialService credentials = mock(CredentialService.class);
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        when(accounts.require(30L)).thenReturn(account());
        when(identities.findLink(200L)).thenReturn(link(IdentityProviderStatus.DISABLED));
        when(credentials.findBySubject(20L)).thenReturn(List.of());
        DefaultExternalIdentityAccountService service = service(
                accounts, credentials, identities, flows, authentication);
        Subject subject = subject();
        Account authenticatedAccount = authenticatedAccount();

        service.delete(
                subject,
                authenticatedAccount,
                30L,
                200L,
                4L,
                "step-up-flow",
                "browser-hash");

        verify(identities, never()).hasOtherActiveLoginIdentity(anyLong(), anyLong());
        verify(flows).authorizeStepUp(
                "step-up-flow", "browser-hash", subject, authenticatedAccount);
        verify(identities).delete(200L, 30L, 4L);
        verify(authentication).endSessions(30L);
    }

    @Test
    void shouldAllowRemovingActiveProviderIdentityWhenAnotherActiveProviderRemains() {
        AccountService accounts = mock(AccountService.class);
        CredentialService credentials = mock(CredentialService.class);
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        when(accounts.require(30L)).thenReturn(account());
        when(identities.findLink(200L)).thenReturn(link());
        when(credentials.findBySubject(20L)).thenReturn(List.of());
        when(identities.hasOtherActiveLoginIdentity(30L, 200L)).thenReturn(true);
        DefaultExternalIdentityAccountService service = service(
                accounts, credentials, identities, flows, authentication);
        Subject subject = subject();
        Account authenticatedAccount = authenticatedAccount();

        service.delete(
                subject,
                authenticatedAccount,
                30L,
                200L,
                4L,
                "step-up-flow",
                "browser-hash");

        verify(flows).authorizeStepUp(
                "step-up-flow", "browser-hash", subject, authenticatedAccount);
        verify(identities).delete(200L, 30L, 4L);
        verify(authentication).endSessions(30L);
    }

    @Test
    void shouldConsumeStepUpDeleteLinkAndEndAccountSessions() {
        AccountService accounts = mock(AccountService.class);
        CredentialService credentials = mock(CredentialService.class);
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AuthenticationProcessService flows = mock(AuthenticationProcessService.class);
        Authentication authentication = mock(Authentication.class);
        when(accounts.require(30L)).thenReturn(account());
        when(identities.findLink(200L)).thenReturn(link());
        when(credentials.findBySubject(20L)).thenReturn(List.of(new IamCredentialEntity()
                .setId(300L)
                .setAccountId(30L)
                .setCredentialType(CredentialType.PASSWORD)
                .setStatus(CredentialStatus.ACTIVE)
                .setVersion(1L)));
        DefaultExternalIdentityAccountService service = service(
                accounts, credentials, identities, flows, authentication);
        Subject subject = subject();
        Account authenticatedAccount = authenticatedAccount();

        service.delete(
                subject,
                authenticatedAccount,
                30L,
                200L,
                4L,
                "step-up-flow",
                "browser-hash");

        verify(flows).authorizeStepUp(
                "step-up-flow", "browser-hash", subject, authenticatedAccount);
        verify(identities).delete(200L, 30L, 4L);
        verify(authentication).endSessions(30L);
    }

    /** @return 使用指定依赖执行解除关联规则的测试 Service。 */
    private DefaultExternalIdentityAccountService service(
            AccountService accounts,
            CredentialService credentials,
            ExternalIdentityRepository identities,
            AuthenticationProcessService flows,
            Authentication authentication) {
        return new DefaultExternalIdentityAccountService(
                accounts,
                credentials,
                identities,
                mock(IdentityProviderRepository.class),
                flows,
                authentication);
    }

    /** @return 当前测试中发起自助安全操作的 Subject */
    private Subject subject() {
        return new Subject(20L, "Alice");
    }

    /** @return 当前测试中已经建立 Session 的 GLOBAL Account */
    private Account authenticatedAccount() {
        return new Account(30L, 20L, AccountScopeType.GLOBAL, null, false);
    }

    /** @return 当前 Subject 名下可认证的 GLOBAL Account 持久化模型 */
    private IamAccountEntity account() {
        return new IamAccountEntity().setId(30L).setSubjectId(20L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null).setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false).setVersion(2L);
    }

    /** @return 关联 ACTIVE Provider 的测试绑定 */
    private ExternalIdentityLink link() {
        return link(IdentityProviderStatus.ACTIVE);
    }

    /** @return 使用指定 Provider 状态的测试绑定。 */
    private ExternalIdentityLink link(IdentityProviderStatus providerStatus) {
        return new ExternalIdentityLink(
                new ExternalIdentity(
                        200L,
                        100L,
                        30L,
                        new ExternalIdentityProfile(
                                "external-1", "Alice", "alice@example.com", true, null),
                        OffsetDateTime.now(),
                        4L),
                IdentityProviderScope.global(),
                providerStatus,
                new IamSubjectEntity().setId(20L).setDisplayName("Alice").setStatus(SubjectStatus.ACTIVE).setVersion(1L),
                account());
    }
}
