package com.sca4d.schedule.service;

import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.ScheduleDefinitionEntity;
import com.sca4d.entity.ScheduleExecutionEntity;
import com.sca4d.schedule.ExecutionStatus;
import com.sca4d.schedule.Schedule;
import com.sca4d.schedule.ScheduleDefinitionFingerprint;
import com.sca4d.schedule.ScheduleErrorCode;
import com.sca4d.schedule.ScheduleExecution;
import com.sca4d.schedule.SchedulePlanSynchronization;
import com.sca4d.schedule.ScheduleService;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleTaskRegistry;
import com.sca4d.schedule.ScheduleTaskType;
import com.sca4d.schedule.ScheduleType;
import com.sca4d.schedule.TriggerSource;
import com.sca4d.schedule.repository.ScheduleRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** 创建、查询、完整替换、删除和人工执行 Schedule。 */
@Service
public class DefaultScheduleService implements ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(DefaultScheduleService.class);

    private final ScheduleRepository repository;
    private final ScheduleTaskRegistry taskRegistry;
    private final SchedulePlanSynchronization planSynchronization;
    private final IdGenerator idGenerator;

    public DefaultScheduleService(
            @NotNull ScheduleRepository repository,
            @NotNull ScheduleTaskRegistry taskRegistry,
            @NotNull SchedulePlanSynchronization planSynchronization,
            @NotNull IdGenerator idGenerator) {
        this.repository = repository;
        this.taskRegistry = taskRegistry;
        this.planSynchronization = planSynchronization;
        this.idGenerator = idGenerator;
    }

    @Override
    public @NotNull PageData<Schedule> page(
            @Nullable String keyword,
            @Nullable ScheduleType scheduleType,
            @Nullable ScheduleStatus status,
            @Nullable String taskType,
            long page,
            long size) {
        return repository.page(
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                scheduleType,
                status,
                StringUtils.hasText(taskType) ? taskType.trim() : null,
                page,
                size).map(DefaultScheduleService::schedule);
    }

    @Override
    public @NotNull Schedule require(@NotNull Long scheduleId) {
        return repository.find(scheduleId)
                .map(DefaultScheduleService::schedule)
                .orElseThrow(() -> new BizException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
    }

    @Transactional
    @Override
    public @NotNull Schedule create(
            @NotNull String name,
            @NotNull ScheduleType scheduleType,
            @Nullable OffsetDateTime executeAt,
            @Nullable String cronExpression,
            @Nullable String timeZone,
            @NotNull String taskType,
            @NotNull String taskPayloadJson,
            @NotNull Long createdByPrincipalId) {
        // 创建只接受完整且互斥的 ONCE/CRON 规则；所有时间和表达式在写入数据库前完成确定性校验。
        if (!StringUtils.hasText(name) || name.trim().length() > 200) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
        }
        OffsetDateTime normalizedExecuteAt = executeAt == null ? null : TimeSupport.toUtc(executeAt);
        String normalizedCron = StringUtils.hasText(cronExpression) ? cronExpression.trim() : null;
        String normalizedTimeZone = StringUtils.hasText(timeZone) ? timeZone.trim() : null;
        if (scheduleType == ScheduleType.ONCE) {
            if (normalizedExecuteAt == null
                    || !normalizedExecuteAt.isAfter(TimeSupport.now())
                    || normalizedCron != null
                    || normalizedTimeZone != null) {
                throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
            }
        } else {
            if (normalizedExecuteAt != null
                    || normalizedCron == null
                    || !CronExpression.isValidExpression(normalizedCron)
                    || normalizedTimeZone == null) {
                throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
            }
            try {
                ZoneId.of(normalizedTimeZone);
            } catch (DateTimeException exception) {
                throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
            }
        }

        // 注册表完成 taskType 精确分派、当前 Payload 版本解码、业务校验和转换编码。
        String normalizedTaskType = taskType.trim();
        try {
            taskRegistry.require(normalizedTaskType);
        } catch (IllegalStateException exception) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_TASK_TYPE_UNKNOWN);
        }
        String normalizedPayload = taskRegistry.validateAndEncode(normalizedTaskType, taskPayloadJson);
        int payloadVersion = taskRegistry.payloadVersion(normalizedTaskType);

        // 数据库先保存唯一业务数据；事务提交后再投影 Quartz，失败只记录漂移并由后续对账修复。
        OffsetDateTime now = TimeSupport.now();
        ScheduleDefinitionEntity schedule = new ScheduleDefinitionEntity()
                .setId(idGenerator.nextId())
                .setName(name.trim())
                .setScheduleType(scheduleType)
                .setExecuteAt(normalizedExecuteAt)
                .setCronExpression(normalizedCron)
                .setTimeZone(normalizedTimeZone)
                .setStatus(ScheduleStatus.ACTIVE)
                .setTaskType(normalizedTaskType)
                .setTaskPayload(normalizedPayload)
                .setTaskPayloadVersion(payloadVersion)
                .setDefinitionFingerprint(ScheduleDefinitionFingerprint.calculate(
                        scheduleType,
                        normalizedExecuteAt,
                        normalizedCron,
                        normalizedTimeZone,
                        ScheduleStatus.ACTIVE,
                        normalizedTaskType,
                        payloadVersion,
                        normalizedPayload))
                .setCreatedByPrincipalId(createdByPrincipalId)
                .setVersion(0L)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setDeleted(0);
        repository.create(schedule);
        planSynchronization.synchronizeAfterCommit(schedule.getId());
        log.info(
                "调度计划创建已写入事务，同步已登记 | scheduleId={} status={} scheduleType={} taskType={}",
                schedule.getId(),
                schedule.getStatus(),
                schedule.getScheduleType(),
                schedule.getTaskType());
        return schedule(schedule);
    }

    @Transactional
    @Override
    public @NotNull Schedule update(
            @NotNull Long scheduleId,
            @NotNull String name,
            @NotNull ScheduleType scheduleType,
            @Nullable OffsetDateTime executeAt,
            @Nullable String cronExpression,
            @Nullable String timeZone,
            @NotNull ScheduleStatus status,
            @NotNull String taskType,
            @NotNull String taskPayloadJson,
            long expectedVersion) {
        // 完整替换先读取当前业务数据，拒绝终态、客户端终态和 taskType 变更。
        ScheduleDefinitionEntity current = repository.find(scheduleId)
                .orElseThrow(() -> new BizException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (current.getStatus().terminal()) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_TERMINAL);
        }
        if (status != ScheduleStatus.ACTIVE && status != ScheduleStatus.PAUSED) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
        }
        if (!current.getTaskType().equals(taskType.trim())) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_TASK_TYPE_IMMUTABLE);
        }
        if (!StringUtils.hasText(name) || name.trim().length() > 200) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
        }

        // ONCE 恢复或编辑必须提交新的未来时间；CRON 必须提交完整表达式和有效 IANA 时区。
        OffsetDateTime normalizedExecuteAt = executeAt == null ? null : TimeSupport.toUtc(executeAt);
        String normalizedCron = StringUtils.hasText(cronExpression) ? cronExpression.trim() : null;
        String normalizedTimeZone = StringUtils.hasText(timeZone) ? timeZone.trim() : null;
        if (scheduleType == ScheduleType.ONCE) {
            if (normalizedExecuteAt == null
                    || !normalizedExecuteAt.isAfter(TimeSupport.now())
                    || normalizedCron != null
                    || normalizedTimeZone != null) {
                throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
            }
        } else {
            if (normalizedExecuteAt != null
                    || normalizedCron == null
                    || !CronExpression.isValidExpression(normalizedCron)
                    || normalizedTimeZone == null) {
                throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
            }
            try {
                ZoneId.of(normalizedTimeZone);
            } catch (DateTimeException exception) {
                throw new BizException(ScheduleErrorCode.SCHEDULE_INVALID);
            }
        }
        String normalizedPayload = taskRegistry.validateAndEncode(current.getTaskType(), taskPayloadJson);
        int payloadVersion = taskRegistry.payloadVersion(current.getTaskType());
        ScheduleDefinitionEntity replacement = new ScheduleDefinitionEntity()
                .setId(scheduleId)
                .setName(name.trim())
                .setScheduleType(scheduleType)
                .setExecuteAt(normalizedExecuteAt)
                .setCronExpression(normalizedCron)
                .setTimeZone(normalizedTimeZone)
                .setStatus(status)
                .setTaskType(current.getTaskType())
                .setTaskPayload(normalizedPayload)
                .setTaskPayloadVersion(payloadVersion)
                .setDefinitionFingerprint(ScheduleDefinitionFingerprint.calculate(
                        scheduleType,
                        normalizedExecuteAt,
                        normalizedCron,
                        normalizedTimeZone,
                        status,
                        current.getTaskType(),
                        payloadVersion,
                        normalizedPayload))
                .setUpdatedAt(TimeSupport.now());

        // 乐观版本进入同一条原子 SQL；成功后返回新版本并在提交后同步 ACTIVE/PAUSED Trigger 状态。
        if (!repository.update(replacement, expectedVersion)) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_VERSION_CHANGED);
        }
        replacement.setCreatedByPrincipalId(current.getCreatedByPrincipalId())
                .setCreatedAt(current.getCreatedAt())
                .setVersion(expectedVersion + 1)
                .setDeleted(0);
        planSynchronization.synchronizeAfterCommit(scheduleId);
        log.info(
                "调度计划更新已写入事务，同步已登记 | scheduleId={} status={} version={} scheduleType={} taskType={}",
                scheduleId,
                status,
                expectedVersion + 1,
                scheduleType,
                current.getTaskType());
        return schedule(replacement);
    }

    @Transactional
    @Override
    public void delete(@NotNull Long scheduleId, long expectedVersion) {
        // 重复 DELETE 对内部 CANCELLED 终态保持幂等；从未存在仍按普通资源不存在处理。
        ScheduleDefinitionEntity current = repository.findIncludingCancelled(scheduleId)
                .orElseThrow(() -> new BizException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (current.getStatus() == ScheduleStatus.CANCELLED) {
            log.debug("调度计划已经取消，当前删除请求不再处理 | scheduleId={}", scheduleId);
            return;
        }
        if (current.getStatus().terminal()) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_TERMINAL);
        }
        if (!repository.cancel(scheduleId, expectedVersion, TimeSupport.now())) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_VERSION_CHANGED);
        }
        planSynchronization.removeAfterCommit(scheduleId);
        log.info("调度计划取消已写入事务，移除同步已登记 | scheduleId={}", scheduleId);
    }

    @Override
    public @NotNull PageData<ScheduleExecution> pageExecutions(
            @NotNull Long scheduleId, long page, long size) {
        require(scheduleId);
        return repository.pageExecutions(scheduleId, page, size).map(DefaultScheduleService::execution);
    }

    @Override
    public @NotNull ScheduleExecution requireExecution(
            @NotNull Long scheduleId, @NotNull Long executionId) {
        require(scheduleId);
        return repository.findExecution(scheduleId, executionId)
                .map(DefaultScheduleService::execution)
                .orElseThrow(() -> new BizException(ScheduleErrorCode.SCHEDULE_EXECUTION_NOT_FOUND));
    }

    @Transactional
    @Override
    public @NotNull ScheduleExecution createManualExecution(@NotNull Long scheduleId) {
        // 人工执行只允许 ACTIVE/PAUSED，不修改原 CRON 或 ONCE Trigger 的下一次触发时间。
        ScheduleDefinitionEntity schedule = repository.find(scheduleId)
                .orElseThrow(() -> new BizException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (schedule.getStatus() != ScheduleStatus.ACTIVE && schedule.getStatus() != ScheduleStatus.PAUSED) {
            throw new BizException(ScheduleErrorCode.SCHEDULE_EXECUTION_NOT_ALLOWED);
        }
        OffsetDateTime now = TimeSupport.now();
        Long executionId = idGenerator.nextId();
        ScheduleExecutionEntity execution = new ScheduleExecutionEntity()
                .setExecutionId(executionId)
                .setScheduleId(scheduleId)
                .setFireKey(scheduleId + ":manual:" + executionId)
                .setTriggerSource(TriggerSource.MANUAL)
                .setActualFireTime(now)
                .setStatus(ExecutionStatus.PENDING)
                .setTaskType(schedule.getTaskType())
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(0L);
        if (!repository.createExecution(execution)) {
            throw new IllegalStateException("人工 ScheduleExecution 主键或 fireKey 冲突");
        }
        planSynchronization.startManualAfterCommit(scheduleId, executionId);
        log.info(
                "人工调度执行已登记 | scheduleId={} executionId={} status={} taskType={}",
                scheduleId,
                executionId,
                execution.getStatus(),
                execution.getTaskType());
        return execution(execution);
    }

    @Override
    public @NotNull List<ScheduleTaskType> taskTypes() {
        return taskRegistry.tasks().stream()
                .map(task -> new ScheduleTaskType(task.taskType(), task.payloadVersion(), task.displayNameKey()))
                .toList();
    }

    /** 将持久化定义投影为不包含同步指纹和删除标记的 Schedule 业务对象。 */
    private static @NotNull Schedule schedule(@NotNull ScheduleDefinitionEntity entity) {
        return new Schedule(
                entity.getId(),
                entity.getName(),
                entity.getScheduleType(),
                entity.getExecuteAt(),
                entity.getCronExpression(),
                entity.getTimeZone(),
                entity.getStatus(),
                entity.getTaskType(),
                entity.getTaskPayload(),
                entity.getTaskPayloadVersion(),
                entity.getSynchronizationError(),
                entity.getCreatedByPrincipalId(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** 将持久化执行投影为不包含内部 fireKey 和乐观锁状态的执行业务对象。 */
    private static @NotNull ScheduleExecution execution(@NotNull ScheduleExecutionEntity entity) {
        return new ScheduleExecution(
                entity.getExecutionId(),
                entity.getScheduleId(),
                entity.getTriggerSource(),
                entity.getScheduledFireTime(),
                entity.getActualFireTime(),
                entity.getCompletedAt(),
                entity.getStatus(),
                entity.getTaskType(),
                entity.getResultSummary(),
                entity.getSafeErrorType(),
                entity.getSafeErrorMessage(),
                entity.getExecutedInstanceId(),
                entity.getCreatedAt());
    }
}
