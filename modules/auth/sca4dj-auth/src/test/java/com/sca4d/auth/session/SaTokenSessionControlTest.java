package com.sca4d.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpLogic;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.SessionTerminationReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class SaTokenSessionControlTest {

    @Mock
    private RedisSessionGeneration generations;

    @Mock
    private StpLogic stpLogic;

    @Mock
    private RedisSessionTokenIndex tokenIndex;

    private SaTokenSessionControl sessions;

    @BeforeEach
    void setUp() {
        sessions = new SaTokenSessionControl(generations, stpLogic, tokenIndex);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("Marks Every Account Session For Identity Refresh")
    void marksEveryAccountSessionForIdentityRefresh() {
        long accountId = 42;

        sessions.invalidateAccount(accountId);

        verify(generations).invalidate(accountId);
        verify(stpLogic, never()).logout(accountId);
    }

    @Test
    @DisplayName("Terminates Other Account Sessions And Retains Current Session")
    void terminatesOtherSessionsAndRetainsCurrent() {
        SessionId retained = new SessionId("session-current");
        when(tokenIndex.find(retained)).thenReturn(Optional.of("token-current"));
        when(stpLogic.getTokenValueListByLoginId(42L))
                .thenReturn(List.of("token-current", "token-other"));

        sessions.terminateOtherSessions(42L, retained, SessionTerminationReason.AUTHENTICATION_CHANGED);

        verify(stpLogic).logoutByTokenValue("token-other");
        verify(stpLogic, never()).logoutByTokenValue("token-current");
        verify(generations).invalidate(42L);
    }

    @Test
    @DisplayName("Marks Multiple Account Sessions For Identity Refresh In One Batch")
    void marksMultipleAccountSessionsForIdentityRefreshInOneBatch() {
        List<Long> accountIds = List.of(42L, 43L);

        sessions.invalidateAccounts(accountIds);

        verify(generations).invalidate(accountIds);
        verifyNoInteractions(stpLogic);
    }

    @Test
    @DisplayName("Invalidates Account Sessions Only After Transaction Commit")
    void invalidatesAccountSessionsOnlyAfterTransactionCommit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        sessions.invalidateAccounts(List.of(42L, 43L, 42L));

        verifyNoInteractions(generations);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.getFirst().afterCommit();
        synchronizations.getFirst().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(generations).invalidate(List.of(42L, 43L));
    }

    @Test
    @DisplayName("Invalidates Account Sessions At Completion When Registered During Commit Callback")
    void invalidatesAccountSessionsAtCompletionWhenRegisteredDuringCommitCallback() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        sessions.invalidateAccount(42L);
        TransactionSynchronization synchronization = TransactionSynchronizationManager
                .getSynchronizations()
                .getFirst();

        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(generations).invalidate(42L);
    }

    @Test
    @DisplayName("Keeps Account Sessions Unchanged When Transaction Rolls Back")
    void keepsAccountSessionsUnchangedWhenTransactionRollsBack() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        sessions.invalidateAccount(42L);
        TransactionSynchronization synchronization = TransactionSynchronizationManager
                .getSynchronizations()
                .getFirst();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(generations);
    }

    @Test
    @DisplayName("Terminates Every Account Session And Publishes The Generation Change")
    void terminatesEveryAccountSessionAndPublishesTheGenerationChange() {
        long accountId = 42;

        sessions.terminateAccount(accountId, SessionTerminationReason.IDENTITY_UNAVAILABLE);

        verify(stpLogic).logout(accountId);
        verify(generations).invalidate(accountId);
    }

    @Test
    @DisplayName("Does Not Publish A Generation Change When Account Logout Fails")
    void doesNotPublishAGenerationChangeWhenAccountLogoutFails() {
        long accountId = 42;
        IllegalStateException failure = new IllegalStateException("session store unavailable");
        doThrow(failure).when(stpLogic).logout(accountId);

        assertThatThrownBy(() -> sessions.terminateAccount(accountId, SessionTerminationReason.IDENTITY_UNAVAILABLE))
                .isSameAs(failure);

        verify(generations, never()).invalidate(accountId);
    }
}
