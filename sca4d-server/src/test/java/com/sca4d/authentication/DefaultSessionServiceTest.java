package com.sca4d.authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.realtime.AuthenticationRealtimePublisher;
import com.sca4d.authentication.service.DefaultSessionService;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Authentication;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.realtime.DefaultRealtimePublisher;
import com.sca4d.common.realtime.RealtimeEnvelope;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.common.realtime.sse.SseConnectionRegistry;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.permission.repository.SessionRow;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

/** 验证 Session 创建、事务提交后刷新以及用户不存在时结束全部会话。 */
class DefaultSessionServiceTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldMergeSessionRefreshesRegisteredInSameTransaction() {
        IamRepository repository = mock(IamRepository.class);
        Authentication authentication = mock(Authentication.class);
        AuthenticationRealtimePublisher realtimePublisher = mock(AuthenticationRealtimePublisher.class);
        when(repository.findAccountsBySubjectIds(ArgumentMatchers.anyCollection()))
                .thenAnswer(invocation -> invocation.<Collection<Long>>getArgument(0).stream()
                        .map(subjectId -> new IamAccountEntity().setId(subjectId + 100L).setSubjectId(subjectId).setScopeType(AccountScopeType.GLOBAL)
                                .setScopeId(null).setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(0L))
                        .toList());
        when(repository.findSessionRows(List.of(101L, 102L, 103L))).thenReturn(List.of());
        DefaultSessionService service = new DefaultSessionService(repository, authentication, realtimePublisher);
        TransactionSynchronizationManager.initSynchronization();

        service.refreshAfterCommit(List.of(1L, 2L));
        service.refreshAfterCommit(List.of(2L, 3L));

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        Assertions.assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCommit();
        synchronizations.getFirst().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(repository).findSessionRows(List.of(101L, 102L, 103L));
        verify(authentication).endSessions(101L);
        verify(authentication).endSessions(102L);
        verify(authentication).endSessions(103L);
        verify(realtimePublisher, never()).sessionChanged(any());
    }

    @Test
    void shouldPublishOnlyAfterEachRedisSessionUpdateSucceeds() {
        IamRepository repository = mock(IamRepository.class);
        Authentication authentication = mock(Authentication.class);
        AuthenticationRealtimePublisher realtimePublisher = mock(AuthenticationRealtimePublisher.class);
        when(repository.findAccountsBySubjectIds(ArgumentMatchers.anyCollection())).thenReturn(List.of(
                new IamAccountEntity().setId(101L).setSubjectId(1L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null).setStatus(AccountStatus.ACTIVE)
                        .setSecondFactorRequired(false).setVersion(0L),
                new IamAccountEntity().setId(102L).setSubjectId(2L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null).setStatus(AccountStatus.ACTIVE)
                        .setSecondFactorRequired(false).setVersion(0L)));
        when(repository.findSessionRows(List.of(101L, 102L))).thenReturn(List.of());
        doThrow(new IllegalStateException("redis unavailable")).when(authentication).endSessions(101L);
        DefaultSessionService service = new DefaultSessionService(repository, authentication, realtimePublisher);

        service.refreshAfterCommit(List.of(1L, 2L));

        verify(realtimePublisher, never()).sessionChanged(any());
        verify(authentication).endSessions(102L);
    }

    @Test
    void shouldPublishSessionChangeInsideOriginalAfterCommitCallback() {
        IamRepository repository = mock(IamRepository.class);
        Authentication authentication = mock(Authentication.class);
        SseConnectionRegistry sseRegistry = mock(SseConnectionRegistry.class);
        DefaultRealtimePublisher publisher = new DefaultRealtimePublisher(
                mock(SimpMessagingTemplate.class), sseRegistry, null, new Sca4djRealtimeProperties());
        @SuppressWarnings("unchecked")
        ObjectProvider<RealtimePublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        when(repository.findAccountsBySubjectIds(ArgumentMatchers.anyCollection())).thenReturn(List.of(
                new IamAccountEntity().setId(107L).setSubjectId(7L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null).setStatus(AccountStatus.ACTIVE)
                        .setSecondFactorRequired(false).setVersion(0L)));
        SessionRow row = new SessionRow();
        row.setAccountId(107L);
        row.setAccountScopeType(AccountScopeType.GLOBAL);
        row.setSecondFactorRequired(true);
        row.setSubjectId(7L);
        row.setSubjectDisplayName("User 7");
        row.setPrincipalId(7L);
        row.setPrincipalType(PrincipalType.DEFAULT);
        row.setPrincipalDisplayName("User 7");
        when(repository.findSessionRows(List.of(107L))).thenReturn(List.of(row));
        DefaultSessionService service = new DefaultSessionService(
                repository, authentication, new AuthenticationRealtimePublisher(provider));
        TransactionSynchronizationManager.initSynchronization();

        service.refreshAfterCommit(List.of(7L));

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        Assertions.assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCommit();

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(authentication).replaceSession(eq(107L), any(), accountCaptor.capture(), any(), any());
        Assertions.assertThat(accountCaptor.getValue().isSecondFactorRequired()).isTrue();
        verify(sseRegistry).send(eq("subject.7"), any(RealtimeEnvelope.class));
        Assertions.assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        synchronizations.getFirst().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    }

    @Test
    void shouldRefreshOnlyAccountIdsReportedOnlineByAuthentication() {
        IamRepository repository = mock(IamRepository.class);
        Authentication authentication = mock(Authentication.class);
        AuthenticationRealtimePublisher realtimePublisher = mock(AuthenticationRealtimePublisher.class);
        when(authentication.onlineAccountIds()).thenReturn(List.of(3L, 4L));
        when(repository.findSessionRows(List.of(3L, 4L))).thenReturn(List.of());
        DefaultSessionService service = new DefaultSessionService(repository, authentication, realtimePublisher);

        service.refreshOnlineAfterCommit();

        verify(repository).findSessionRows(List.of(3L, 4L));
        verify(authentication).endSessions(3L);
        verify(authentication).endSessions(4L);
    }
}
