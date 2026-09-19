package com.sca4d.iam.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.iam.acl.AclService;
import com.sca4d.iam.base.acl.PlatformAclRepository;
import com.sca4d.iam.base.rbac.PlatformRbacService;
import com.sca4d.iam.identity.IamErrorCode;
import com.sca4d.iam.identity.IdentityStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;

/** 验证 Platform Account 删除的保护规则、共享身份判断和 Session 顺序。 */
@ExtendWith(MockitoExtension.class)
class PlatformAccountLifecycleServiceTest {

    private static final long ACCOUNT_ID = 10L;

    private static final long CURRENT_ACCOUNT_ID = 20L;

    private static final long SUBJECT_ID = 30L;

    private static final long PRINCIPAL_ID = 40L;

    @Mock
    private PlatformAccountLifecycleRepository repository;

    @Mock
    private PlatformRbacService rbac;

    @Mock
    private AclService acl;

    @Mock
    private SessionControl sessions;

    private PlatformAccountLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        lifecycle = new PlatformAccountLifecycleService(repository, rbac, acl, sessions);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Rejects Current And Root Accounts Before Session Or Data Cleanup")
    void rejectsCurrentAndRootAccountsBeforeSessionOrDataCleanup() {
        when(repository.find(ACCOUNT_ID))
                .thenReturn(Optional.of(account(false)))
                .thenReturn(Optional.of(account(true)));

        assertBusinessFailure(
                () -> lifecycle.delete(ACCOUNT_ID, ACCOUNT_ID),
                IamErrorCode.CURRENT_ACCOUNT);
        assertBusinessFailure(
                () -> lifecycle.delete(ACCOUNT_ID, CURRENT_ACCOUNT_ID),
                IamErrorCode.PROTECTED_ACCOUNT);

        verify(sessions, never()).terminateAccount(ACCOUNT_ID, SessionTerminationReason.IDENTITY_UNAVAILABLE);
        verify(repository, never()).isolateAccount(ACCOUNT_ID);
    }

    @Test
    @DisplayName("Keeps Shared Subject Principal And Their Application Data")
    void keepsSharedSubjectPrincipalAndTheirApplicationData() {
        when(repository.find(ACCOUNT_ID)).thenReturn(Optional.of(account(false)));
        when(repository.hasOtherSubjectAccount(SUBJECT_ID, ACCOUNT_ID)).thenReturn(true);
        when(repository.hasOtherPrincipalAccount(PRINCIPAL_ID, ACCOUNT_ID)).thenReturn(true);

        PlatformAccountDeletion deletion = lifecycle.delete(ACCOUNT_ID, CURRENT_ACCOUNT_ID);

        assertThat(deletion.subjectDeleted()).isFalse();
        assertThat(deletion.principalDeleted()).isFalse();
        verify(repository).isolateAccount(ACCOUNT_ID);
        verify(repository, never()).isolateSubject(SUBJECT_ID);
        verify(repository, never()).isolatePrincipal(PRINCIPAL_ID);
        verify(rbac, never()).revokeAllRoles(PRINCIPAL_ID);
        verify(acl, never()).revokeByPrincipal(PlatformAclRepository.PLATFORM, PRINCIPAL_ID);
    }

    @Test
    @DisplayName("Deletes Exclusive Identities And Terminates Again Only After Commit")
    void deletesExclusiveIdentitiesAndTerminatesAgainOnlyAfterCommit() {
        when(repository.find(ACCOUNT_ID)).thenReturn(Optional.of(account(false)));
        TransactionSynchronizationManager.initSynchronization();

        PlatformAccountDeletion deletion = lifecycle.delete(ACCOUNT_ID, CURRENT_ACCOUNT_ID);

        assertThat(deletion.subjectDeleted()).isTrue();
        assertThat(deletion.principalDeleted()).isTrue();
        verify(sessions).terminateAccount(ACCOUNT_ID, SessionTerminationReason.IDENTITY_UNAVAILABLE);
        verify(rbac).revokeAllRoles(PRINCIPAL_ID);
        verify(acl).revokeByPrincipal(PlatformAclRepository.PLATFORM, PRINCIPAL_ID);
        verify(repository).isolatePrincipal(PRINCIPAL_ID);
        verify(repository).isolateSubject(SUBJECT_ID);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(sessions, times(2))
                .terminateAccount(ACCOUNT_ID, SessionTerminationReason.IDENTITY_UNAVAILABLE);
    }

    /** 创建指定 ROOT 保护状态的测试目录项。 */
    private static PlatformAccountEntry account(boolean protectedRoot) {
        return new PlatformAccountEntry(
                ACCOUNT_ID,
                SUBJECT_ID,
                PRINCIPAL_ID,
                "target",
                IdentityStatus.ACTIVE,
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"),
                protectedRoot);
    }

    /** 验证生命周期操作按指定业务错误拒绝。 */
    private static void assertBusinessFailure(Callable<?> invocation, IamErrorCode errorCode) {
        assertThatThrownBy(invocation::call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(errorCode));
    }
}
