package com.sca4d.job.persistence;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.job.JobWorkKey;
import com.sca4d.core.api.job.PipelineJob;
import com.sca4d.core.api.job.PipelineJobPlan;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.api.runtime.InstanceId;
import com.sca4d.core.api.scheduling.OverlapPolicy;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.job.JobAttemptStatus;
import com.sca4d.job.JobNodeStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 隐藏 Job Mapper 和 App 隔离名称，提供稳定的持久化语义。 */
public class JobRepository {
    private static final String DIRECT_NODE_KEY = "direct";

    private final @NotNull JobMapper mapper;

    private final @NotNull Name appName;

    private final @NotNull SqlBatches sqlBatches;

    /** 创建绑定当前应用 App 隔离名称的 Job Repository。 */
    public JobRepository(
            @NotNull JobMapper mapper,
            @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "JobMapper 不能为空");
        this.appName = AppName.current();
        if (this.appName.format(NameFormat.COLON).length() > 128) {
            throw new IllegalArgumentException("应用名称超过 Job app_name 的 128 字符限制");
        }
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /** 查询同一计划和 fire 已存在的根运行。 */
    public @Nullable Long findScheduledRun(
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt,
            @Nullable InstanceId targetInstanceId) {
        return mapper.selectScheduledRun(
                appName,
                scheduleKey,
                scheduledAt,
                targetInstanceId == null ? null : targetInstanceId.format(NameFormat.COLON));
    }

    /** 返回当前 App 隔离名称、Job 定义和计划共同限定的运行分页。 */
    public @NotNull Page<ScheduledRunRecord> findScheduledRuns(
            @NotNull ScheduleKey scheduleKey,
            @NotNull JobKey jobKey,
            int definitionVersion,
            @NotNull String nodeKey,
            @NotNull PageRequest page) {
        PageRequest checkedPage = Objects.requireNonNull(page, "Job 历史分页要求不能为空");
        long total = mapper.countScheduledRuns(
                appName,
                Objects.requireNonNull(scheduleKey, "ScheduleKey 不能为空"),
                Objects.requireNonNull(jobKey, "Job key 不能为空"),
                definitionVersion);
        List<ScheduledRunRecord> items = total == 0
                ? List.of()
                : mapper.selectScheduledRuns(
                        appName,
                        scheduleKey,
                        jobKey,
                        definitionVersion,
                        Objects.requireNonNull(nodeKey, "Job 节点名称不能为空"),
                        checkedPage);
        return new Page<>(items, checkedPage, total);
    }

    /** 查询同一身份、定义和工作键下的活动运行。 */
    public @Nullable RunRecord findActiveWork(
            @NotNull TreeCode systemIdentity,
            @NotNull JobKey jobKey,
            @NotNull JobWorkKey workKey,
            @Nullable InstanceId targetInstanceId) {
        return mapper.selectActiveWork(
                appName,
                systemIdentity,
                jobKey,
                workKey,
                targetInstanceId == null ? null : targetInstanceId.format(NameFormat.COLON));
    }

    /** 登记本次进程代次及可执行的能力。 */
    public void registerInstance(
            @NotNull InstanceId instanceId,
            @NotNull UUID generationId,
            @NotNull String capabilitiesJson,
            @NotNull Instant expiresAt) {
        mapper.upsertInstance(appName, instanceId.format(NameFormat.COLON), generationId, capabilitiesJson, expiresAt);
    }

    /** 返回仍在线且启用指定能力的实例快照。 */
    public @NotNull List<JobInstance> eligibleInstances(@NotNull String capabilityCode, @NotNull Instant now) {
        return mapper.selectEligibleInstances(appName, capabilityCode, now);
    }

    /** 只注销本次进程代次，不删除替换它的新进程登记。 */
    public void unregisterInstance(@NotNull InstanceId instanceId, @NotNull UUID generationId) {
        mapper.deleteInstance(appName, instanceId.format(NameFormat.COLON), generationId);
    }

    /** 保存广播目标清单并返回数据库分配的广播标识。 */
    public @NotNull Id createDispatch(
            @NotNull JobKey jobKey,
            @Nullable ScheduleKey scheduleKey,
            @Nullable Instant scheduledAt,
            @NotNull String targetsJson) {
        return Id.of(mapper.insertDispatch(appName, jobKey, scheduleKey, scheduledAt, targetsJson));
    }

    /** 查找同一计划与原定时间已经固定的广播。 */
    public @Nullable JobDispatchRecord findScheduledDispatch(
            @NotNull ScheduleKey scheduleKey,
            @NotNull Instant scheduledAt) {
        return mapper.selectScheduledDispatch(appName, scheduleKey, scheduledAt);
    }

    /** 返回一次广播已创建的持久化运行。 */
    public @NotNull List<JobDispatchRun> dispatchRuns(@NotNull Id dispatchId) {
        return mapper.selectDispatchRuns(appName, dispatchId.value());
    }

    /** 查询目标实例上同键的无记录工作是否仍待执行或运行中。 */
    public boolean hasActiveDelivery(
            @NotNull TreeCode systemIdentity,
            @NotNull JobKey jobKey,
            @NotNull JobWorkKey workKey,
            @NotNull InstanceId target) {
        return mapper.selectActiveDelivery(
                appName,
                systemIdentity,
                jobKey,
                workKey,
                target.format(NameFormat.COLON)) != null;
    }

    /** 保存一份无需 Run 的指定实例工作。 */
    public void createDelivery(
            @NotNull Id dispatchId,
            @NotNull Job<?, ?> job,
            @NotNull TreeCode systemIdentity,
            @NotNull JobWorkKey workKey,
            @NotNull String overlapPolicy,
            @Nullable String inputJson,
            @NotNull JobInstance target,
            @NotNull Instant expiresAt) {
        mapper.insertDelivery(
                appName,
                dispatchId.value(),
                job.key(),
                job.definitionVersion(),
                job.requiredCapability(),
                systemIdentity,
                workKey,
                overlapPolicy,
                inputJson,
                target.instanceId().format(NameFormat.COLON),
                target.generationId(),
                expiresAt);
    }

    /** 领取当前进程启动代次的一份无记录工作。 */
    public @Nullable JobDelivery claimDelivery(
            @NotNull InstanceId instanceId,
            @NotNull UUID generationId,
            @NotNull Instant now,
            @NotNull Instant leaseUntil,
            @NotNull Map<JobKey, Map<Integer, InstanceCapability>> definitions) {
        if (definitions.isEmpty()) {
            return null;
        }
        return mapper.updateClaimableDelivery(
                appName,
                instanceId.format(NameFormat.COLON),
                generationId,
                now,
                leaseUntil,
                definitions);
    }

    /** 续期当前实例仍在执行的无记录份额。 */
    public boolean renewDelivery(
            @NotNull JobDelivery delivery,
            @NotNull UUID generationId,
            @NotNull Instant now,
            @NotNull Instant leaseUntil) {
        return AffectedRowCounts.changed(
                mapper.updateDeliveryLease(
                        delivery.id(),
                        delivery.targetInstanceId().format(NameFormat.COLON),
                        generationId,
                        now,
                        leaseUntil),
                "Job 无记录份额租约");
    }

    /** 保存无记录工作最终结果。 */
    public boolean completeDelivery(
            @NotNull JobDelivery delivery,
            @NotNull UUID generationId,
            boolean succeeded,
            @Nullable String error,
            @NotNull Instant at) {
        return AffectedRowCounts.changed(
                mapper.updateDeliveryResult(
                        delivery.id(),
                        delivery.targetInstanceId().format(NameFormat.COLON),
                        generationId,
                        succeeded ? "SUCCEEDED" : "FAILED",
                        error,
                        at),
                "Job 无记录工作结果");
    }

    /** 清理到期的短期投递协调记录。 */
    public void maintainDeliveries(@NotNull Instant now, @NotNull Instant completedBefore) {
        mapper.updateExpiredDeliveries(appName, now);
        mapper.deleteCompletedDeliveries(appName, completedBefore);
        mapper.deleteCompletedDispatches(appName, completedBefore);
        mapper.deleteExpiredInstances(appName, completedBefore);
    }

    /** 查询当前 App 隔离名称的根运行。 */
    public @Nullable RunRecord findRun(@NotNull Id runId) {
        Id checked = Objects.requireNonNull(runId, "Id 不能为空");
        return mapper.selectRun(appName, checked.value());
    }

    /** 锁定根运行，使并发分支的成功和后继激活按事务顺序发生。 */
    public void lockRun(@NotNull Id runId) {
        Id checked = Objects.requireNonNull(runId, "Job run 标识不能为空");
        if (mapper.selectRunIdForUpdate(appName, checked.value()) == null) {
            throw new IllegalStateException("Job run 不存在: " + checked);
        }
    }

    /** 原子领取一个可执行节点。 */
    public @Nullable NodeClaim claim(
            @NotNull String workerId,
            @NotNull InstanceId instanceId,
            @NotNull UUID generationId,
            @NotNull Instant now,
            @NotNull Instant leaseUntil,
            @NotNull Map<JobKey, Map<Integer, InstanceCapability>> definitions) {
        if (definitions.isEmpty()) {
            return null;
        }
        return mapper.updateClaimableNode(
                appName,
                workerId,
                instanceId.format(NameFormat.COLON),
                generationId,
                now,
                leaseUntil,
                definitions);
    }

    /** 延长仍由指定 worker 持有的节点租约。 */
    public boolean renew(long nodeId, @NotNull String workerId, @NotNull Instant leaseUntil) {
        return AffectedRowCounts.changed(
                mapper.updateNodeLease(nodeId, workerId, leaseUntil),
                "Job 节点租约");
    }

    /** 读取节点依赖输出。 */
    public @NotNull List<@Nullable String> dependencyOutputs(long nodeId) {
        return Objects.requireNonNull(mapper.selectDependencyOutputs(nodeId));
    }

    /** 查询节点是否有后继。 */
    public boolean hasDependents(long nodeId) {
        return mapper.selectHasDependents(nodeId);
    }

    /** 原子保存本次节点尝试结果，旧工作实例或旧尝试不能覆盖接管后的结果。 */
    public void saveNodeResult(@NotNull NodeClaim result, @NotNull String workerId) {
        NodeClaim checked = Objects.requireNonNull(result, "Job 节点尝试结果不能为空");
        boolean succeeded = checked.attemptStatus() == JobAttemptStatus.SUCCEEDED;
        String outputJson = checked.outputJson();
        String error = checked.error();
        Instant availableAt = checked.availableAt();
        AffectedRowCounts.requireSingle(
                mapper.updateNodeProgress(
                        checked.nodeId(),
                        checked.attempt(),
                        Objects.requireNonNull(workerId, "Job worker 标识不能为空"),
                        Patch.set(checked.resultStatus()),
                        Patch.set(checked.attemptStatus()),
                        succeeded
                                ? outputJson == null ? Patch.clear() : Patch.set(outputJson)
                                : Patch.ignore(),
                        error == null ? Patch.ignore() : Patch.set(error),
                        availableAt == null ? Patch.ignore() : Patch.set(availableAt),
                        checked.resultStatus() == JobNodeStatus.SUCCEEDED
                                ? Patch.set(checked.completedAt())
                                : Patch.ignore(),
                        Patch.set(checked.completedAt())),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "Job 节点尝试结果");
    }

    /** 激活依赖已经满足的后继节点。 */
    public void activateDependents(long sourceNodeId, @NotNull Instant availableAt) {
        mapper.updateDependentNodes(sourceNodeId, availableAt);
    }

    /** 保存业务层确定的根运行状态、输出和结束时间。 */
    public void saveRunResult(
            long runId,
            @NotNull JobRunStatus status,
            @Nullable String outputJson,
            @NotNull Instant completedAt) {
        AffectedRowCounts.requireSingle(
                mapper.updateRunResult(
                        runId,
                        Patch.set(status),
                        outputJson == null ? Patch.clear() : Patch.set(outputJson),
                        Patch.set(completedAt)),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "Job run 结果");
    }

    /** 写入最终失败结果。 */
    public void failNode(
            long nodeId,
            long runId,
            long attempt,
            @NotNull String workerId,
            @NotNull String error,
            @NotNull Instant failedAt) {
        AffectedRowCounts.requireSingle(
                mapper.updateNodeFailed(nodeId, runId, attempt, workerId, error, failedAt),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "Job 节点失败结果");
    }

    /** 处理租约到期且重试耗尽的节点。 */
    public void failExpiredExhausted(@NotNull Instant now) {
        mapper.updateExpiredNodes(appName, now);
    }

    /** 在当前事务中锁定一批仍允许再次执行的过期尝试。 */
    public @NotNull List<ExpiredJobAttempt> expiredAttempts(@NotNull Instant now) {
        return mapper.selectExpiredAttempts(appName, now);
    }

    /** 保存过期尝试的放弃事实和业务层计算的下次领取时间。 */
    public void rescheduleExpired(
            @NotNull ExpiredJobAttempt attempt,
            @NotNull Instant availableAt,
            @NotNull Instant now) {
        AffectedRowCounts.requireSingle(
                mapper.rescheduleExpired(
                        attempt.nodeId(),
                        attempt.attempt(),
                        attempt.workerId(),
                        attempt.leaseUntil(),
                        availableAt,
                        now),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "过期 Job 尝试恢复");
    }

    /** 结束目标实例等待期限已到而尚未启动的 Run。 */
    public void failExpiredTargets(@NotNull Instant now) {
        mapper.updateExpiredTargets(appName, now);
    }

    /** 批量删除当前 App 隔离名称内超过保留时间的成功根运行及其节点、依赖和尝试。 */
    @Transactional
    public int deleteSucceededRunsBefore(@NotNull Instant completedBefore, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Job 成功历史删除上限必须大于零");
        }
        List<Long> selected = mapper.selectExpiredRunIdsForUpdate(
                appName,
                Objects.requireNonNull(completedBefore, "Job 成功历史截止时间不能为空"),
                limit);
        int[] deleted = {0};
        sqlBatches.forEach(selected, 1, 1, batch -> {
            mapper.deleteAttempts(batch);
            mapper.deleteDependencies(batch);
            mapper.deleteNodes(batch);
            int count = mapper.deleteRuns(appName, batch);
            AffectedRowCounts.requireExactly(count, batch.size(), "清理 Job 运行历史");
            deleted[0] += count;
        });
        return deleted[0];
    }

    /** 原子保存 Job 根运行、声明节点与有序依赖；任一写入失败整体回滚。 */
    @Transactional
    public @NotNull Id createRun(
            @NotNull Job<?, ?> job,
            @Nullable String inputJson,
            @Nullable ScheduleKey scheduleKey,
            @Nullable Instant scheduledAt,
            @NotNull Instant now,
            @NotNull TreeCode systemIdentity,
            @NotNull JobWorkKey workKey,
            @NotNull OverlapPolicy overlapPolicy,
            @Nullable InstanceId targetInstanceId,
            @Nullable UUID targetGenerationId,
            @Nullable Instant targetExpiresAt,
            @Nullable Long dispatchId) {
        Job<?, ?> checked = Objects.requireNonNull(job, "Job 不能为空");
        String kind;
        if (checked instanceof DirectJob<?, ?>) {
            kind = "DIRECT";
        } else if (checked instanceof PipelineJob<?, ?>) {
            kind = "PIPELINE";
        } else {
            throw new IllegalArgumentException("不支持的 Job 子类: " + checked.getClass().getName());
        }
        long runId = mapper.insertRun(
                appName,
                checked.key(),
                checked.definitionVersion(),
                kind,
                checked.requiredCapability(),
                systemIdentity,
                workKey,
                overlapPolicy.name(),
                targetInstanceId == null ? null : targetInstanceId.format(NameFormat.COLON),
                targetGenerationId,
                targetExpiresAt,
                dispatchId,
                inputJson,
                scheduleKey,
                scheduledAt);
        if (checked instanceof DirectJob<?, ?> directJob) {
            mapper.insertNode(
                    runId,
                    DIRECT_NODE_KEY,
                    "DIRECT",
                    inputJson,
                    JobNodeStatus.READY,
                    false,
                    directJob.retryRule(),
                    now);
            return Id.of(runId);
        }
        PipelineJobPlan<?, ?> plan = ((PipelineJob<?, ?>) checked).definition();
        Map<String, Long> nodeIds = new HashMap<>();
        for (PipelineJobPlan.Node node : plan.nodes()) {
            long nodeId = mapper.insertNode(
                    runId,
                    node.key(),
                    "PIPELINE",
                    node.dependencies().isEmpty() ? inputJson : null,
                    node.dependencies().isEmpty() ? JobNodeStatus.READY : JobNodeStatus.WAITING,
                    node.parallelMerge(),
                    node.retryRule(),
                    now);
            nodeIds.put(node.key(), nodeId);
        }
        // 所有节点标识已属于本次根运行，依赖写入直接使用已知关系，不再逐边联表确认。
        Map<Long, Map<Integer, Long>> dependencies = new LinkedHashMap<>();
        int edgeCount = 0;
        for (PipelineJobPlan.Node node : plan.nodes()) {
            long nodeId = nodeId(nodeIds, node.key());
            for (int order = 0; order < node.dependencies().size(); order++) {
                dependencies.computeIfAbsent(nodeId, ignored -> new LinkedHashMap<>())
                        .put(order, nodeId(nodeIds, node.dependencies().get(order)));
                edgeCount++;
                if (edgeCount == 1000) {
                    AffectedRowCounts
                            .requireExactly(mapper.insertDependencies(runId, dependencies), edgeCount, "Job 节点依赖");
                    dependencies.clear();
                    edgeCount = 0;
                }
            }
        }
        if (edgeCount > 0) {
            AffectedRowCounts.requireExactly(mapper.insertDependencies(runId, dependencies), edgeCount, "Job 节点依赖");
        }
        return Id.of(runId);
    }

    /** 从本次创建的节点中解析依赖，缺失时使整个运行创建失败。 */
    private static long nodeId(@NotNull Map<String, Long> nodeIds, @NotNull String key) {
        Long nodeId = nodeIds.get(key);
        if (nodeId == null) {
            throw new IllegalStateException("PipelineJob 依赖节点不存在: " + key);
        }
        return nodeId;
    }
}
