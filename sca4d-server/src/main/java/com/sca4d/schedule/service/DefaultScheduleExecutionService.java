package com.sca4d.schedule.service;

import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.ScheduleDefinitionEntity;
import com.sca4d.entity.ScheduleExecutionEntity;
import com.sca4d.schedule.ExecutionStatus;
import com.sca4d.schedule.ScheduleErrorCode;
import com.sca4d.schedule.ScheduleExecutionService;
import com.sca4d.schedule.SchedulePlanSynchronization;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleTaskExecution;
import com.sca4d.schedule.ScheduleTaskRegistry;
import com.sca4d.schedule.ScheduleTaskResult;
import com.sca4d.schedule.ScheduleType;
import com.sca4d.schedule.TriggerSource;
import com.sca4d.schedule.repository.ScheduleRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;

/** 通过数据库唯一执行数据和业务事务实现 ScheduleTask 幂等执行。 */
@Service
public class DefaultScheduleExecutionService implements ScheduleExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultScheduleExecutionService.class);

    private final ScheduleRepository repository;
    private final ScheduleTaskRegistry taskRegistry;
    private final IdGenerator idGenerator;
    private final SchedulePlanSynchronization planSynchronization;
    private final TransactionTemplate businessTransaction;
    private final TransactionTemplate failureTransaction;

    /**
     * 创建使用当前应用唯一事务管理器的 Schedule 执行服务。
     *
     * @param repository 读写 Schedule 定义和执行记录的仓储
     * @param taskRegistry 已注册任务类型目录
     * @param idGenerator 集群执行主键生成器
     * @param transactionManager 当前 PostgreSQL 业务事务管理器
     */
    public DefaultScheduleExecutionService(
            @NotNull ScheduleRepository repository,
            @NotNull ScheduleTaskRegistry taskRegistry,
            @NotNull IdGenerator idGenerator,
            @NotNull SchedulePlanSynchronization planSynchronization,
            @NotNull PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.taskRegistry = taskRegistry;
        this.idGenerator = idGenerator;
        this.planSynchronization = planSynchronization;
        this.businessTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void run(
            @NotNull Long scheduleId,
            @Nullable Long manualExecutionId,
            @Nullable String expectedFingerprint,
            @NotNull TriggerSource triggerSource,
            @Nullable OffsetDateTime scheduledFireTime,
            @NotNull OffsetDateTime actualFireTime,
            @NotNull String executedInstanceId) {
        long startedAt = System.nanoTime();
        String fireKey = manualExecutionId == null
                ? scheduleId + ":scheduled:" + Objects.requireNonNull(scheduledFireTime).toInstant().toEpochMilli()
                : scheduleId + ":manual:" + manualExecutionId;
        try {
            ScheduleExecutionEntity completedExecution = businessTransaction.execute(transaction -> {
                // 每次执行重新读取数据库定义，校验当前状态和 Quartz 指纹后再调用任务实现。
                ScheduleDefinitionEntity schedule = repository.find(scheduleId)
                        .orElseThrow(() -> new BizException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
                boolean allowed = manualExecutionId == null
                        ? schedule.getStatus() == ScheduleStatus.ACTIVE
                        : schedule.getStatus() == ScheduleStatus.ACTIVE || schedule.getStatus() == ScheduleStatus.PAUSED;
                if (!allowed) {
                    throw new BizException(ScheduleErrorCode.SCHEDULE_EXECUTION_NOT_ALLOWED);
                }
                if (triggerSource == TriggerSource.SCHEDULED
                        && !schedule.getDefinitionFingerprint().equals(expectedFingerprint)) {
                    throw new BizException(ScheduleErrorCode.SCHEDULE_SYNCHRONIZATION_FAILED);
                }
                taskRegistry.require(schedule.getTaskType());

                // 计划执行按 scheduleId + scheduledFireTime 生成固定 fireKey；人工执行复用服务端 executionId。
                ScheduleExecutionEntity execution;
                if (manualExecutionId != null) {
                    execution = repository.findExecution(scheduleId, manualExecutionId)
                            .orElseThrow(() -> new BizException(ScheduleErrorCode.SCHEDULE_EXECUTION_NOT_FOUND));
                    if (!fireKey.equals(execution.getFireKey())) {
                        throw new BizException(ScheduleErrorCode.SCHEDULE_EXECUTION_NOT_FOUND);
                    }
                } else {
                    execution = new ScheduleExecutionEntity()
                            .setExecutionId(idGenerator.nextId())
                            .setScheduleId(scheduleId)
                            .setFireKey(fireKey)
                            .setTriggerSource(triggerSource)
                            .setScheduledFireTime(scheduledFireTime)
                            .setActualFireTime(actualFireTime)
                            .setStatus(ExecutionStatus.PENDING)
                            .setTaskType(schedule.getTaskType())
                            .setCreatedAt(actualFireTime)
                            .setUpdatedAt(actualFireTime)
                            .setVersion(0L);
                    if (!repository.createExecution(execution)) {
                        execution = repository.findExecutionByFireKey(scheduleId, fireKey)
                                .orElseThrow(() -> new IllegalStateException("fireKey 冲突后缺少执行事实"));
                    }
                }
                if (execution.getStatus() == ExecutionStatus.SUCCESS) {
                    log.debug(
                            "调度执行已完成，当前触发跳过 | scheduleId={} executionId={} triggerSource={}",
                            scheduleId,
                            execution.getExecutionId(),
                            triggerSource);
                    return null;
                }

                // 普通触发只领取 PENDING/FAILED；节点故障 recovery 还允许接管原 RUNNING 数据并复用同一 fireKey。
                Set<ExecutionStatus> claimable = triggerSource == TriggerSource.RECOVERY
                        ? Set.of(ExecutionStatus.PENDING, ExecutionStatus.FAILED, ExecutionStatus.RUNNING)
                        : Set.of(ExecutionStatus.PENDING, ExecutionStatus.FAILED);
                if (!repository.claimExecution(execution, triggerSource, executedInstanceId, actualFireTime, claimable)) {
                    repository.findExecutionByFireKey(scheduleId, fireKey)
                            .orElseThrow(() -> new IllegalStateException("执行领取失败后缺少持久化事实"));
                    // 领取失败表示另一个事务已经推进同一执行事实；当前触发直接结束，不能把竞争结果改写为 SKIPPED。
                    log.debug(
                            "调度执行已由其他节点处理，当前触发跳过 | scheduleId={} executionId={} triggerSource={} instanceId={}",
                            scheduleId,
                            execution.getExecutionId(),
                            triggerSource,
                            executedInstanceId);
                    return null;
                }
                execution.setStatus(ExecutionStatus.RUNNING).setVersion(execution.getVersion() + 1);
                log.info(
                        "调度任务开始 | scheduleId={} executionId={} triggerSource={} taskType={} instanceId={}",
                        scheduleId,
                        execution.getExecutionId(),
                        triggerSource,
                        schedule.getTaskType(),
                        executedInstanceId);

                // 已注册任务在当前事务内完成业务写入；随后 SUCCESS 与 ONCE 终态一起提交，任一步失败均整体回滚。
                ScheduleTaskResult result;
                try {
                    result = taskRegistry.run(
                            schedule.getTaskType(),
                            schedule.getTaskPayloadVersion(),
                            schedule.getTaskPayload(),
                            new ScheduleTaskExecution(
                                    scheduleId,
                                    execution.getExecutionId(),
                                    fireKey,
                                    triggerSource,
                                    scheduledFireTime,
                                    actualFireTime));
                } catch (RuntimeException taskFailure) {
                    throw new ScheduleTaskRunFailure(taskFailure);
                }
                if (!repository.completeExecution(
                        execution,
                        ExecutionStatus.SUCCESS,
                        result.getSummary(),
                        null,
                        null,
                        TimeSupport.now())) {
                    throw new IllegalStateException("ScheduleExecution SUCCESS 状态写入冲突");
                }
                if (manualExecutionId == null && schedule.getScheduleType() == ScheduleType.ONCE) {
                    if (!repository.changeStatus(
                            scheduleId,
                            ScheduleStatus.ACTIVE,
                            ScheduleStatus.COMPLETED,
                            schedule.getVersion(),
                            TimeSupport.now())) {
                        throw new IllegalStateException("ONCE Schedule 完成状态写入冲突");
                    }
                    planSynchronization.removeAfterCommit(scheduleId);
                }
                return execution;
            });
            if (completedExecution != null) {
                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                log.info(
                        "调度任务完成 | scheduleId={} executionId={} result={} durationMs={} taskType={} instanceId={}",
                        scheduleId,
                        completedExecution.getExecutionId(),
                        ExecutionStatus.SUCCESS,
                        durationMs,
                        completedExecution.getTaskType(),
                        executedInstanceId);
            }
        } catch (RuntimeException exception) {
            boolean taskFailed = exception instanceof ScheduleTaskRunFailure;
            RuntimeException failure = taskFailed && exception.getCause() instanceof RuntimeException cause
                    ? cause
                    : exception;
            // 业务事务已经回滚；独立事务保存 FAILED 或 SKIPPED，只有真正进入任务实现的 ONCE 才能进入 FAILED。
            Long unsuccessfulExecutionId = failureTransaction.execute(transaction -> {
                ScheduleDefinitionEntity schedule = repository.findIncludingCancelled(scheduleId).orElse(null);
                String taskType = schedule == null ? "unknown" : schedule.getTaskType();
                String safeMessage = failure instanceof BizException bizException
                        ? bizException.getMessageKey()
                        : null;
                Long executionId = manualExecutionId == null ? idGenerator.nextId() : manualExecutionId;
                repository.saveUnsuccessfulExecution(new ScheduleExecutionEntity()
                        .setExecutionId(executionId)
                        .setScheduleId(scheduleId)
                        .setFireKey(fireKey)
                        .setTriggerSource(triggerSource)
                        .setScheduledFireTime(scheduledFireTime)
                        .setActualFireTime(actualFireTime)
                        .setCompletedAt(TimeSupport.now())
                        .setStatus(taskFailed ? ExecutionStatus.FAILED : ExecutionStatus.SKIPPED)
                        .setTaskType(taskType)
                        .setSafeErrorType(failure.getClass().getName())
                        .setSafeErrorMessage(safeMessage)
                        .setExecutedInstanceId(executedInstanceId)
                        .setCreatedAt(actualFireTime)
                        .setUpdatedAt(TimeSupport.now())
                        .setVersion(0L));
                if (schedule != null
                        && taskFailed
                        && manualExecutionId == null
                        && schedule.getScheduleType() == ScheduleType.ONCE
                        && schedule.getStatus() == ScheduleStatus.ACTIVE) {
                    boolean changed = repository.changeStatus(
                            scheduleId,
                            ScheduleStatus.ACTIVE,
                            ScheduleStatus.FAILED,
                            schedule.getVersion(),
                            TimeSupport.now());
                    if (changed) {
                        planSynchronization.removeAfterCommit(scheduleId);
                    }
                } else if (schedule != null && !schedule.getStatus().terminal() && schedule.getDeleted() == 0) {
                    planSynchronization.synchronizeAfterCommit(scheduleId);
                }
                return executionId;
            });
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            ExecutionStatus result = taskFailed ? ExecutionStatus.FAILED : ExecutionStatus.SKIPPED;
            // Quartz 继续作为异常堆栈的最终记录边界；此处只记录已经提交的执行结果，避免同一异常重复打印。
            if (taskFailed) {
                log.warn(
                        "调度任务失败，失败结果已保存 | scheduleId={} executionId={} result={} durationMs={} triggerSource={} instanceId={} failureType={}",
                        scheduleId,
                        unsuccessfulExecutionId,
                        result,
                        durationMs,
                        triggerSource,
                        executedInstanceId,
                        failure.getClass().getName());
            } else {
                log.warn(
                        "调度任务未执行，跳过结果已保存 | scheduleId={} executionId={} result={} durationMs={} triggerSource={} instanceId={} failureType={}",
                        scheduleId,
                        unsuccessfulExecutionId,
                        result,
                        durationMs,
                        triggerSource,
                        executedInstanceId,
                        failure.getClass().getName());
            }
            throw failure;
        }
    }

    /** 标记异常确实来自 ScheduleTask 解码或业务执行阶段，供事务外区分 FAILED 与 SKIPPED。 */
    private static final class ScheduleTaskRunFailure extends RuntimeException {

        /** @param cause ScheduleTask 解码、校验或业务写入抛出的原始异常 */
        private ScheduleTaskRunFailure(@NotNull RuntimeException cause) {
            super(cause);
        }
    }
}
