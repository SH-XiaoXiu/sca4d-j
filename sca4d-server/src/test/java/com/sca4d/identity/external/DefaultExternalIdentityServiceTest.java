package com.sca4d.identity.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.concurrent.lock.LockedOperation;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.subject.SubjectLock;
import com.sca4d.iam.subject.SubjectService;
import com.sca4d.iam.subject.SubjectStatus;
import com.sca4d.identity.IdentityProvider;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderProtocol;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderStatus;
import com.sca4d.identity.policy.IdentityProviderRegistrationPolicy;
import com.sca4d.identity.repository.IdentityProviderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/** 验证 ExternalIdentity 认证、绑定、作用域和当前状态复核。 */
class DefaultExternalIdentityServiceTest {

    @Test
    void shouldReturnNullWhenExternalSubjectIsNotLinked() {
        IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        when(providers.findProvider(scope, 100L)).thenReturn(provider(scope));
        DefaultExternalIdentityService service = service(providers, identities, mock(AccountService.class), mock(SubjectService.class));

        ExternalIdentityVerification verification = service.authenticate(
                scope, 100L, profile("external-1"));

        assertThat(verification).isNull();
        verify(identities, never()).recordAuthentication(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void shouldRecordAuthenticationAndReturnActiveAccount() {
        IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        IdentityProviderScope scope = IdentityProviderScope.global();
        ExternalIdentityLink link = link(scope, 200L, "external-1", 3L);
        ExternalIdentityLink updated = link(scope, 200L, "external-1", 3L);
        ExternalIdentityProfile profile = profile("external-1");
        when(providers.findProvider(scope, 100L)).thenReturn(provider(scope));
        when(identities.findLink(100L, "external-1")).thenReturn(link);
        when(identities.findLink(200L)).thenReturn(link);
        when(identities.recordAuthentication(
                eq(200L),
                eq(100L),
                eq(3L),
                any(ExternalIdentityProfile.class)))
                .thenReturn(updated);
        DefaultExternalIdentityService service = service(providers, identities, mock(AccountService.class), mock(SubjectService.class));

        ExternalIdentityVerification verification = service.authenticate(
                scope, 100L, profile);

        assertThat(verification.getIdentityId()).isEqualTo(200L);
        assertThat(verification.getIdentityVersion()).isEqualTo(3L);
        assertThat(verification.getSubject().getSubjectId()).isEqualTo(20L);
        assertThat(verification.getAccount().getAccountId()).isEqualTo(30L);
        ArgumentCaptor<ExternalIdentityProfile> profileCaptor = ArgumentCaptor.forClass(ExternalIdentityProfile.class);
        verify(identities).recordAuthentication(eq(200L), eq(100L), eq(3L), profileCaptor.capture());
        assertThat(profileCaptor.getValue())
                .usingRecursiveComparison()
                .isEqualTo(profile);
    }

    @Test
    void shouldCreateLinkForAccountInProviderScope() {
        IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AccountService accounts = mock(AccountService.class);
        SubjectService subjects = mock(SubjectService.class);
        IdentityProviderScope scope = IdentityProviderScope.tenant(9L);
        IamAccountEntity account = account(AccountScopeType.TENANT, 9L);
        ExternalIdentityProfile profile = profile("external-1");
        when(providers.findProvider(scope, 100L)).thenReturn(provider(scope));
        when(accounts.require(30L)).thenReturn(account);
        when(subjects.require(20L)).thenReturn(subject());
        when(identities.create(
                eq(100L),
                eq(30L),
                any(ExternalIdentityProfile.class)))
                .thenReturn(link(scope, 200L, "external-1", 0L));
        DefaultExternalIdentityService service = service(providers, identities, accounts, subjects);

        ExternalIdentityVerification verification = service.link(
                scope, 100L, 30L, profile);

        assertThat(verification.getAccount().getScopeType()).isEqualTo(AccountScopeType.TENANT);
        assertThat(verification.getAccount().getScopeId()).isEqualTo(9L);
        ArgumentCaptor<ExternalIdentityProfile> profileCaptor = ArgumentCaptor.forClass(ExternalIdentityProfile.class);
        verify(identities).create(eq(100L), eq(30L), profileCaptor.capture());
        assertThat(profileCaptor.getValue())
                .usingRecursiveComparison()
                .isEqualTo(profile);
    }

    @Test
    void shouldRejectAccountOutsideProviderScope() {
        IdentityProviderRepository providers = mock(IdentityProviderRepository.class);
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AccountService accounts = mock(AccountService.class);
        SubjectService subjects = mock(SubjectService.class);
        IdentityProviderScope scope = IdentityProviderScope.tenant(9L);
        when(providers.findProvider(scope, 100L)).thenReturn(provider(scope));
        when(accounts.require(30L)).thenReturn(account(AccountScopeType.GLOBAL, null));
        when(subjects.require(20L)).thenReturn(subject());
        DefaultExternalIdentityService service = service(providers, identities, accounts, subjects);

        assertThatThrownBy(() -> service.link(
                scope, 100L, 30L, profile("external-1")))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE));
        verify(identities, never()).create(anyLong(), anyLong(), any());
    }

    @Test
    void shouldRejectChangedIdentityDuringFinalReview() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        when(identities.findLink(200L))
                .thenReturn(link(IdentityProviderScope.global(), 200L, "external-1", 4L));
        DefaultExternalIdentityService service = service(
                mock(IdentityProviderRepository.class),
                identities,
                mock(AccountService.class),
                mock(SubjectService.class));

        assertThatThrownBy(() -> service.requireCurrent(200L, 3L))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IdentityProviderErrorCode.EXTERNAL_IDENTITY_UNAVAILABLE));
    }

    /** 创建测试 Service，并校验绑定事务始终位于 Subject 锁内。 */
    private DefaultExternalIdentityService service(
            IdentityProviderRepository providers,
            ExternalIdentityRepository identities,
            AccountService accounts,
            SubjectService subjects) {
        SubjectLock subjectLock = mock(SubjectLock.class);
        TransactionOperations transactions = mock(TransactionOperations.class);
        AtomicBoolean subjectLocked = new AtomicBoolean();
        try {
            when(subjectLock.execute(anyLong(), any())).thenAnswer(invocation -> {
                subjectLocked.set(true);
                try {
                    LockedOperation<?> operation = invocation.getArgument(1);
                    return operation.execute();
                } finally {
                    subjectLocked.set(false);
                }
            });
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
        when(transactions.execute(any())).thenAnswer(invocation -> {
            assertThat(subjectLocked).isTrue();
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return new DefaultExternalIdentityService(
                providers, identities, accounts, subjects, subjectLock, transactions);
    }

    /** @return 指定作用域的 ACTIVE OIDC Provider。 */
    private IdentityProvider provider(IdentityProviderScope scope) {
        return new IdentityProvider(
                100L,
                scope,
                "oidc",
                "OIDC",
                IdentityProviderProtocol.OIDC,
                IdentityProviderRegistrationPolicy.JIT,
                false,
                IdentityProviderStatus.ACTIVE,
                1L);
    }

    /** @return 固定 external subject 的上游资料。 */
    private ExternalIdentityProfile profile(String externalSubject) {
        return new ExternalIdentityProfile(
                externalSubject, "Alice", "alice@example.com", true, "https://example.com/avatar.png");
    }

    /** @return 带 ACTIVE Subject、Account 和 Provider 状态的 ExternalIdentity 绑定。 */
    private ExternalIdentityLink link(
            IdentityProviderScope scope, long identityId, String externalSubject, long version) {
        return new ExternalIdentityLink(
                new ExternalIdentity(
                        identityId,
                        100L,
                        30L,
                        profile(externalSubject),
                        OffsetDateTime.now(),
                        version),
                scope,
                IdentityProviderStatus.ACTIVE,
                subject(),
                account(scope.getScopeType(), scope.getScopeId()));
    }

    /** @return ACTIVE Subject 测试数据。 */
    private IamSubjectEntity subject() {
        return new IamSubjectEntity().setId(20L).setDisplayName("Alice").setStatus(SubjectStatus.ACTIVE).setVersion(2L);
    }

    /** @return ACTIVE Account 测试数据。 */
    private IamAccountEntity account(
            AccountScopeType scopeType, Long scopeId) {
        return new IamAccountEntity().setId(30L).setSubjectId(20L).setScopeType(scopeType).setScopeId(scopeId).setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false).setVersion(2L);
    }
}
