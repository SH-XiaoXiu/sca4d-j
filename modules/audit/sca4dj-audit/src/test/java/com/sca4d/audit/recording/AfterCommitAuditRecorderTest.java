package com.sca4d.audit.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.audit.AuditOperation;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.spring.call.CallHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/** 验证尽力审计只在事务成功后排队，后台任务使用独立 Call。 */
class AfterCommitAuditRecorderTest {

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(
                            synchronization -> synchronization
                                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        CallHolder.clear();
    }

    @Test
    void enqueuesImmediatelyWithoutTransactionAndRunsInItsOwnCall() {
        AuditRepository repository = mock(AuditRepository.class);
        AuditRecord record = mock(AuditRecord.class);
        when(record.requestId()).thenReturn("request-1");
        when(record.operation()).thenReturn(AuditOperation.declared("TEST/RECORD"));
        FrozenAuditEntry frozen = mock(FrozenAuditEntry.class);
        when(repository.freeze(record)).thenReturn(frozen);
        List<Runnable> work = new ArrayList<>();
        AfterCommitAuditRecorder recorder = new AfterCommitAuditRecorder(repository, work::add);

        recorder.record(record);
        assertThat(work).hasSize(1);
        work.getFirst().run();

        verify(repository).appendFrozen(frozen);
        assertThat(CallHolder.currentOrNull()).isNull();
    }

    @Test
    void enqueuesOnlyAfterCommitAndDropsRejectedWork() {
        AuditRepository repository = mock(AuditRepository.class);
        AuditRecord record = mock(AuditRecord.class);
        when(record.requestId()).thenReturn("request-2");
        when(record.operation()).thenReturn(AuditOperation.declared("TEST/RECORD"));
        FrozenAuditEntry frozen = mock(FrozenAuditEntry.class);
        when(repository.freeze(record)).thenReturn(frozen);
        List<Runnable> work = new ArrayList<>();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        AfterCommitAuditRecorder recorder = new AfterCommitAuditRecorder(repository, work::add);

        recorder.record(record);
        assertThat(work).isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(work).hasSize(1);

        AfterCommitAuditRecorder rejected = new AfterCommitAuditRecorder(repository, task -> {
            throw new RejectedExecutionException("queue full");
        });
        assertThatCode(() -> rejected.record(record)).doesNotThrowAnyException();
    }
}
