package com.sca4d.job.persistence;

import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.Job;
import com.sca4d.core.api.job.JobRunId;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.job.PipelineJob;
import com.sca4d.core.api.job.PipelineJobPlan;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.runtime.RuntimeNamespace;
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

/** 隐藏 Job Mapper 和运行时命名空间，提供稳定的持久化语义。 */
public class JobRepository {
    private static final String DIRECT_NODE_KEY = "direct";

    private final @NotNull JobMapper mapper;

    private final @NotNull String runtimeNamespace;

    private final @NotNull SqlBatches sqlBatches;

    /** 创建绑定当前应用运行时命名空间的 Job Repository。 */
    public JobRepository(
            @NotNull JobMapper mapper,
            @NotNull RuntimeNamespace runtimeNamespace,
            @NotNull SqlBatches sqlBatches) {
        this.mapper = Objects.requireNonNull(mapper, "JobMapper 不能为空");
        RuntimeNamespace checked = Objects.requireNonNull(runtimeNamespace, "Job 运行时命名空间不能为空");
        this.runtimeNamespace = checked.applicationId();
        if (this.runtimeNamespace.length() > 128) {
            throw new IllegalArgumentException("应用技术 ID 超过 Job runtime_namespace 的 128 字符限制");
        }
        this.sqlBatches = Objects.requireNonNull(sqlBatches, "SQL 批处理器不能为空");
    }

    /** 查询同一计划和 fire 已存在的根运行。 */
    public @Nullable Long findScheduledRun(@NotNull String scheduleKey, @NotNull Instant scheduledAt) {
        return mapper.selectScheduledRun(runtimeNamespace, scheduleKey, scheduledAt, null);
    }

    /** 返回当前命名空间、Job 定义和计划共同限定的运行分页。 */
    public @NotNull Page<ScheduledRunRecord> findScheduledRuns(
            @NotNull String scheduleKey,
            @NotNull String jobKey,
            int definitionVersion,
            @NotNull String nodeKey,
            @NotNull PageRequest page) {
        PageRequest checkedPage = Objects.requireNonNull(page, "Job 历史分页要求不能为空");
        long total = mapper.countScheduledRuns(
                runtimeNamespace,
                Objects.requireNonNull(scheduleKey, "ScheduleKey 不能为空"),
                Objects.requireNonNull(jobKey, "Job key 不能为空"),
                definitionVersion);
        List<ScheduledRunRecord> items = total == 0
                ? List.of()
                : mapper.selectScheduledRuns(
                        runtimeNamespace,
                        scheduleKey,
                        jobKey,
                        definitionVersion,
                        Objects.requireNonNull(nodeKey, "Job 节点名称不能为空"),
                        checkedPage);
        return new Page<>(items, checkedPage, total);
    }

    /** 查询同一计划尚未结束的根运行。 */
    public @Nullable Long findActiveScheduledRun(@NotNull String scheduleKey) {
        return mapper.selectScheduledRun(
                runtimeNamespace,
                scheduleKey,
                null,
                List.of(JobRunStatus.QUEUED, JobRunStatus.RUNNING));
    }

    /** 查询当前命名空间的根运行。 */
    public @Nullable RunRecord findRun(@NotNull JobRunId runId) {
        JobRunId checked = Objects.requireNonNull(runId, "JobRunId 不能为空");
        return mapper.selectRun(runtimeNamespace, checked.value());
    }

    /** 原子领取一个可执行节点。 */
    public @Nullable NodeClaim claim(
            @NotNull String workerId,
            @NotNull Instant now,
            @NotNull Instant leaseUntil) {
        return mapper.updateClaimableNode(runtimeNamespace, workerId, now, leaseUntil);
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
        AffectedRowCounts.requireSingle(
                mapper.updateNodeProgress(
                        checked.nodeId(),
                        checked.attempt(),
                        Objects.requireNonNull(workerId, "Job worker 标识不能为空"),
                        Patch.set(checked.resultStatus()),
                        Patch.set(checked.attemptStatus()),
                        succeeded
                                ? checked.outputJson() == null ? Patch.clear() : Patch.set(checked.outputJson())
                                : Patch.ignore(),
                        checked.error() == null ? Patch.ignore() : Patch.set(checked.error()),
                        checked.availableAt() == null ? Patch.ignore() : Patch.set(checked.availableAt()),
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
            int attempt,
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
        mapper.updateExpiredNodes(runtimeNamespace, now);
    }

    /** 批量删除当前命名空间内超过保留时间的成功根运行及其节点、依赖和尝试。 */
    @Transactional
    public int deleteSucceededRunsBefore(@NotNull Instant completedBefore, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Job 成功历史删除上限必须大于零");
        }
        List<Long> selected = mapper.selectExpiredRunIdsForUpdate(
                runtimeNamespace,
                Objects.requireNonNull(completedBefore, "Job 成功历史截止时间不能为空"),
                limit);
        int[] deleted = {0};
        sqlBatches.forEach(selected, 1, 1, batch -> {
            mapper.deleteAttempts(batch);
            mapper.deleteDependencies(batch);
            mapper.deleteNodes(batch);
            int count = mapper.deleteRuns(runtimeNamespace, batch);
            AffectedRowCounts.requireExactly(count, batch.size(), "清理 Job 运行历史");
            deleted[0] += count;
        });
        return deleted[0];
    }

    /** 原子保存 Job 根运行、声明节点与有序依赖；任一写入失败整体回滚。 */
    @Transactional
    public @NotNull JobRunId createRun(
            @NotNull Job<?, ?> job,
            @Nullable String inputJson,
            @Nullable String scheduleKey,
            @Nullable Instant scheduledAt,
            @NotNull Instant now) {
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
                runtimeNamespace,
                checked.key().value(),
                checked.definitionVersion(),
                kind,
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
                    directJob.retryPolicy().maxAttempts(),
                    directJob.retryPolicy().delay().toMillis(),
                    now);
            return new JobRunId(runId);
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
                    node.retryPolicy().maxAttempts(),
                    node.retryPolicy().delay().toMillis(),
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
        return new JobRunId(runId);
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
