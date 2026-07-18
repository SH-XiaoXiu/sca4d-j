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
import com.sca4d.schedule.Schedule;
import com.sca4d.schedule.ScheduleErrorCode;
import com.sca4d.schedule.ScheduleExecution;
import com.sca4d.schedule.SchedulePlanSynchronization;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleTask;
import com.sca4d.schedule.ScheduleTaskPayload;
import com.sca4d.schedule.ScheduleTaskRegistry;
import com.sca4d.schedule.ScheduleType;
import com.sca4d.schedule.TriggerSource;
import com.sca4d.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 验证 Schedule 创建、完整替换、删除和人工执行。 */
class DefaultScheduleServiceTest {

    private static final String TASK_TYPE = "notification.sse";

    private ScheduleRepository repository;
    private SchedulePlanSynchronization synchronization;
    private IdGenerator idGenerator;
    private DefaultScheduleService service;

    @BeforeEach
    void createService() {
        repository = mock(ScheduleRepository.class);
        synchronization = mock(SchedulePlanSynchronization.class);
        idGenerator = mock(IdGenerator.class);
        ScheduleTask<ScheduleTaskPayload> task = mock(ScheduleTask.class);
        ScheduleTaskPayload payload = mock(ScheduleTaskPayload.class);
        when(task.taskType()).thenReturn(TASK_TYPE);
        when(task.payloadVersion()).thenReturn(2);
        when(task.decode(2, "{\"title\":\"notice\"}")).thenReturn(payload);
        when(task.encode(payload)).thenReturn("{\"title\":\"notice\",\"version\":2}");
        when(task.displayNameKey()).thenReturn("schedule.task.notification-sse");
        service = new DefaultScheduleService(
                repository, new ScheduleTaskRegistry(List.of(task)), synchronization, idGenerator);
    }

    @Test
    void shouldCreateValidatedOnceAndCronDefinitions() {
        when(idGenerator.nextId()).thenReturn(101L, 102L);
        OffsetDateTime executeAt = OffsetDateTime.now().plusHours(1);

        Schedule once = service.create(
                "  one-time notice  ",
                ScheduleType.ONCE,
                executeAt,
                null,
                null,
                TASK_TYPE,
                "{\"title\":\"notice\"}",
                7L);
        Schedule cron = service.create(
                "recurring notice",
                ScheduleType.CRON,
                null,
                " 0 0/5 * * * ? ",
                " Asia/Shanghai ",
                TASK_TYPE,
                "{\"title\":\"notice\"}",
                7L);

        assertThat(once.getScheduleId()).isEqualTo(101L);
        assertThat(once.getName()).isEqualTo("one-time notice");
        assertThat(once.getExecuteAt().toInstant()).isEqualTo(executeAt.toInstant());
        assertThat(once.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(once.getTaskPayloadVersion()).isEqualTo(2);
        assertThat(once.getTaskPayload()).isEqualTo("{\"title\":\"notice\",\"version\":2}");
        assertThat(cron.getScheduleId()).isEqualTo(102L);
        assertThat(cron.getCronExpression()).isEqualTo("0 0/5 * * * ?");
        assertThat(cron.getTimeZone()).isEqualTo("Asia/Shanghai");
        verify(synchronization).synchronizeAfterCommit(101L);
        verify(synchronization).synchronizeAfterCommit(102L);
    }

    @Test
    void shouldRejectInvalidRulesAndUnknownTaskTypeBeforeWriting() {
        assertThatThrownBy(() -> service.create(
                "expired",
                ScheduleType.ONCE,
                OffsetDateTime.now().minusMinutes(1),
                null,
                null,
                TASK_TYPE,
                "{\"title\":\"notice\"}",
                7L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ScheduleErrorCode.SCHEDULE_INVALID));
        assertThatThrownBy(() -> service.create(
                "unknown",
                ScheduleType.CRON,
                null,
                "0 0 * * * ?",
                "UTC",
                "arbitrary.BeanName",
                "{}",
                7L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ScheduleErrorCode.SCHEDULE_TASK_TYPE_UNKNOWN));

        verify(repository, never()).create(any());
        verify(synchronization, never()).synchronizeAfterCommit(any());
    }

    @Test
    void shouldFullyReplaceMutableDefinitionAndPreserveImmutableTaskType() {
        ScheduleDefinitionEntity current = activeSchedule(201L).setVersion(4L);
        when(repository.find(201L)).thenReturn(Optional.of(current));
        when(repository.update(any(), eq(4L))).thenReturn(true);

        Schedule updated = service.update(
                201L,
                " paused recurring notice ",
                ScheduleType.CRON,
                null,
                "0 0/10 * * * ?",
                "UTC",
                ScheduleStatus.PAUSED,
                TASK_TYPE,
                "{\"title\":\"notice\"}",
                4L);

        assertThat(updated.getName()).isEqualTo("paused recurring notice");
        assertThat(updated.getScheduleType()).isEqualTo(ScheduleType.CRON);
        assertThat(updated.getStatus()).isEqualTo(ScheduleStatus.PAUSED);
        assertThat(updated.getVersion()).isEqualTo(5L);
        assertThat(updated.getTaskType()).isEqualTo(TASK_TYPE);
        verify(synchronization).synchronizeAfterCommit(201L);

        assertThatThrownBy(() -> service.update(
                201L,
                "changed task",
                ScheduleType.CRON,
                null,
                "0 0/10 * * * ?",
                "UTC",
                ScheduleStatus.ACTIVE,
                "other.task",
                "{}",
                4L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ScheduleErrorCode.SCHEDULE_TASK_TYPE_IMMUTABLE));
    }

    @Test
    void shouldEnforceOptimisticDeleteAndKeepRepeatedDeleteIdempotent() {
        ScheduleDefinitionEntity active = activeSchedule(301L).setVersion(8L);
        ScheduleDefinitionEntity cancelled = activeSchedule(302L)
                .setStatus(ScheduleStatus.CANCELLED)
                .setDeleted(1);
        when(repository.findIncludingCancelled(301L)).thenReturn(Optional.of(active));
        when(repository.findIncludingCancelled(302L)).thenReturn(Optional.of(cancelled));
        when(repository.cancel(eq(301L), eq(8L), any())).thenReturn(true);

        service.delete(301L, 8L);
        service.delete(302L, 99L);

        verify(synchronization).removeAfterCommit(301L);
        verify(repository, never()).cancel(eq(302L), eq(99L), any());

        when(repository.findIncludingCancelled(303L)).thenReturn(Optional.of(activeSchedule(303L).setVersion(3L)));
        when(repository.cancel(eq(303L), eq(2L), any())).thenReturn(false);
        assertThatThrownBy(() -> service.delete(303L, 2L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ScheduleErrorCode.SCHEDULE_VERSION_CHANGED));
    }

    @Test
    void shouldCreateIndependentManualExecutionForActiveOrPausedSchedule() {
        when(repository.find(401L)).thenReturn(Optional.of(activeSchedule(401L).setStatus(ScheduleStatus.PAUSED)));
        when(repository.createExecution(any())).thenReturn(true);
        when(idGenerator.nextId()).thenReturn(501L);

        ScheduleExecution execution = service.createManualExecution(401L);

        assertThat(execution.getExecutionId()).isEqualTo(501L);
        assertThat(execution.getScheduleId()).isEqualTo(401L);
        assertThat(execution.getTriggerSource()).isEqualTo(TriggerSource.MANUAL);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        ArgumentCaptor<ScheduleExecutionEntity> storedExecution = ArgumentCaptor.forClass(ScheduleExecutionEntity.class);
        verify(repository).createExecution(storedExecution.capture());
        assertThat(storedExecution.getValue().getFireKey()).isEqualTo("401:manual:501");
        verify(synchronization).startManualAfterCommit(401L, 501L);

        when(repository.find(402L)).thenReturn(Optional.of(activeSchedule(402L).setStatus(ScheduleStatus.COMPLETED)));
        assertThatThrownBy(() -> service.createManualExecution(402L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ScheduleErrorCode.SCHEDULE_EXECUTION_NOT_ALLOWED));
    }

    /** @return 使用指定主键且允许人工执行的一次性 ACTIVE Schedule */
    private static ScheduleDefinitionEntity activeSchedule(long scheduleId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ScheduleDefinitionEntity()
                .setId(scheduleId)
                .setName("notice")
                .setScheduleType(ScheduleType.ONCE)
                .setExecuteAt(now.plusHours(1))
                .setStatus(ScheduleStatus.ACTIVE)
                .setTaskType(TASK_TYPE)
                .setTaskPayload("{\"title\":\"notice\",\"version\":2}")
                .setTaskPayloadVersion(2)
                .setDefinitionFingerprint("fingerprint")
                .setCreatedByPrincipalId(7L)
                .setVersion(0L)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setDeleted(0);
    }
}
