package com.sca4d.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.ScheduleDefinitionEntity;
import com.sca4d.entity.ScheduleExecutionEntity;
import com.sca4d.schedule.ExecutionStatus;
import com.sca4d.schedule.SchedulePlanSynchronization;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleTask;
import com.sca4d.schedule.ScheduleTaskPayload;
import com.sca4d.schedule.ScheduleTaskRegistry;
import com.sca4d.schedule.ScheduleTaskResult;
import com.sca4d.schedule.ScheduleType;
import com.sca4d.schedule.TriggerSource;
import com.sca4d.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 验证执行记录领取、指纹校验、任务调用和最终状态更新。 */
class DefaultScheduleExecutionServiceTest {

    @Test
    void shouldCommitTaskSuccessAndCompleteOnceSchedule() {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        SchedulePlanSynchronization synchronization = mock(SchedulePlanSynchronization.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        ScheduleTask<ScheduleTaskPayload> task = mock(ScheduleTask.class);
        ScheduleTaskPayload payload = mock(ScheduleTaskPayload.class);
        when(task.taskType()).thenReturn("notification.sse");
        when(task.payloadVersion()).thenReturn(1);
        when(task.displayNameKey()).thenReturn("schedule.task.notification-sse");
        when(task.decode(1, "{}")).thenReturn(payload);
        when(task.execute(any(), any())).thenReturn(new ScheduleTaskResult("recipientCount=1"));
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(1L)
                .setScheduleType(ScheduleType.ONCE)
                .setStatus(ScheduleStatus.ACTIVE)
                .setTaskType("notification.sse")
                .setTaskPayloadVersion(1)
                .setTaskPayload("{}")
                .setDefinitionFingerprint("current")
                .setVersion(3L)
                .setDeleted(0);
        when(repository.find(1L)).thenReturn(Optional.of(schedule));
        when(repository.createExecution(any())).thenReturn(true);
        when(repository.claimExecution(any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.completeExecution(any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.changeStatus(eq(1L), eq(ScheduleStatus.ACTIVE), eq(ScheduleStatus.COMPLETED), eq(3L), any()))
                .thenReturn(true);
        when(idGenerator.nextId()).thenReturn(11L);
        DefaultScheduleExecutionService service = new DefaultScheduleExecutionService(
                repository,
                new ScheduleTaskRegistry(List.of(task)),
                idGenerator,
                synchronization,
                transactionManager);

        service.run(
                1L,
                null,
                "current",
                TriggerSource.SCHEDULED,
                OffsetDateTime.parse("2026-07-14T10:00:00Z"),
                OffsetDateTime.parse("2026-07-14T10:00:01Z"),
                "node-1");

        verify(repository).completeExecution(any(), ArgumentMatchers.eq(ExecutionStatus.SUCCESS),
                ArgumentMatchers.eq("recipientCount=1"), any(), any(), any());
        verify(synchronization).removeAfterCommit(1L);
        verify(repository, never()).saveUnsuccessfulExecution(any());
    }

    @Test
    void shouldFailOnceOnlyWhenTaskActuallyFails() {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        SchedulePlanSynchronization synchronization = mock(SchedulePlanSynchronization.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        ScheduleTask<ScheduleTaskPayload> task = mock(ScheduleTask.class);
        ScheduleTaskPayload payload = mock(ScheduleTaskPayload.class);
        when(task.taskType()).thenReturn("notification.sse");
        when(task.payloadVersion()).thenReturn(1);
        when(task.displayNameKey()).thenReturn("schedule.task.notification-sse");
        when(task.decode(1, "{}")).thenReturn(payload);
        when(task.execute(any(), any())).thenThrow(new IllegalStateException("task failed"));
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(2L)
                .setScheduleType(ScheduleType.ONCE)
                .setStatus(ScheduleStatus.ACTIVE)
                .setTaskType("notification.sse")
                .setTaskPayloadVersion(1)
                .setTaskPayload("{}")
                .setDefinitionFingerprint("current")
                .setVersion(4L)
                .setDeleted(0);
        when(repository.find(2L)).thenReturn(Optional.of(schedule));
        when(repository.findIncludingCancelled(2L)).thenReturn(Optional.of(schedule));
        when(repository.createExecution(any())).thenReturn(true);
        when(repository.claimExecution(any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.changeStatus(eq(2L), eq(ScheduleStatus.ACTIVE), eq(ScheduleStatus.FAILED), eq(4L), any()))
                .thenReturn(true);
        when(idGenerator.nextId()).thenReturn(21L, 22L);
        DefaultScheduleExecutionService service = new DefaultScheduleExecutionService(
                repository,
                new ScheduleTaskRegistry(List.of(task)),
                idGenerator,
                synchronization,
                transactionManager);

        assertThatThrownBy(() -> service.run(
                2L,
                null,
                "current",
                TriggerSource.SCHEDULED,
                OffsetDateTime.parse("2026-07-14T11:00:00Z"),
                OffsetDateTime.parse("2026-07-14T11:00:01Z"),
                "node-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("task failed");

        ArgumentCaptor<ScheduleExecutionEntity> failure = ArgumentCaptor.forClass(ScheduleExecutionEntity.class);
        verify(repository).saveUnsuccessfulExecution(failure.capture());
        assertThat(failure.getValue().getStatus()).isEqualTo(ExecutionStatus.FAILED);
        verify(repository).changeStatus(eq(2L), eq(ScheduleStatus.ACTIVE), eq(ScheduleStatus.FAILED), eq(4L), any());
        verify(synchronization).removeAfterCommit(2L);
    }

    @Test
    void shouldSkipStaleProjectionWithoutFailingOnceSchedule() {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        SchedulePlanSynchronization synchronization = mock(SchedulePlanSynchronization.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(3L)
                .setScheduleType(ScheduleType.ONCE)
                .setStatus(ScheduleStatus.ACTIVE)
                .setTaskType("notification.sse")
                .setDefinitionFingerprint("current")
                .setVersion(5L)
                .setDeleted(0);
        when(repository.find(3L)).thenReturn(Optional.of(schedule));
        when(repository.findIncludingCancelled(3L)).thenReturn(Optional.of(schedule));
        when(idGenerator.nextId()).thenReturn(31L);
        DefaultScheduleExecutionService service = new DefaultScheduleExecutionService(
                repository,
                new ScheduleTaskRegistry(List.of()),
                idGenerator,
                synchronization,
                transactionManager);

        assertThatThrownBy(() -> service.run(
                3L,
                null,
                "stale",
                TriggerSource.SCHEDULED,
                OffsetDateTime.parse("2026-07-14T12:00:00Z"),
                OffsetDateTime.parse("2026-07-14T12:00:01Z"),
                "node-2"))
                .isInstanceOf(BizException.class);

        ArgumentCaptor<ScheduleExecutionEntity> skipped = ArgumentCaptor.forClass(ScheduleExecutionEntity.class);
        verify(repository).saveUnsuccessfulExecution(skipped.capture());
        assertThat(skipped.getValue().getStatus()).isEqualTo(ExecutionStatus.SKIPPED);
        verify(repository, never()).changeStatus(
                ArgumentMatchers.anyLong(),
                any(),
                ArgumentMatchers.eq(ScheduleStatus.FAILED),
                ArgumentMatchers.anyLong(),
                any());
        verify(synchronization).synchronizeAfterCommit(3L);
    }

    @Test
    void shouldNotRewriteExecutionWhenAnotherTransactionWinsClaim() {
        ScheduleRepository repository = mock(ScheduleRepository.class);
        SchedulePlanSynchronization synchronization = mock(SchedulePlanSynchronization.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        ScheduleTask<ScheduleTaskPayload> task = mock(ScheduleTask.class);
        when(task.taskType()).thenReturn("notification.sse");
        when(task.payloadVersion()).thenReturn(1);
        when(task.displayNameKey()).thenReturn("schedule.task.notification-sse");
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(4L)
                .setScheduleType(ScheduleType.CRON)
                .setStatus(ScheduleStatus.ACTIVE)
                .setTaskType("notification.sse")
                .setTaskPayloadVersion(1)
                .setTaskPayload("{}")
                .setDefinitionFingerprint("current")
                .setVersion(2L)
                .setDeleted(0);
        ScheduleExecutionEntity pending = new ScheduleExecutionEntity()
                .setExecutionId(41L)
                .setScheduleId(4L)
                .setFireKey("4:scheduled:1784023200000")
                .setStatus(ExecutionStatus.PENDING)
                .setVersion(0L);
        ScheduleExecutionEntity running = new ScheduleExecutionEntity()
                .setExecutionId(41L)
                .setScheduleId(4L)
                .setFireKey("4:scheduled:1784023200000")
                .setStatus(ExecutionStatus.RUNNING)
                .setVersion(1L);
        when(repository.find(4L)).thenReturn(Optional.of(schedule));
        when(repository.createExecution(any())).thenReturn(false);
        when(repository.findExecutionByFireKey(4L, "4:scheduled:1784023200000"))
                .thenReturn(Optional.of(pending), Optional.of(running));
        when(repository.claimExecution(any(), any(), any(), any(), any())).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(41L);
        DefaultScheduleExecutionService service = new DefaultScheduleExecutionService(
                repository,
                new ScheduleTaskRegistry(List.of(task)),
                idGenerator,
                synchronization,
                transactionManager);

        service.run(
                4L,
                null,
                "current",
                TriggerSource.SCHEDULED,
                OffsetDateTime.parse("2026-07-14T10:00:00Z"),
                OffsetDateTime.parse("2026-07-14T10:00:01Z"),
                "node-2");

        verify(repository, never()).saveUnsuccessfulExecution(any());
        verify(task, never()).execute(any(), any());
    }
}
