package com.sca4d.job.persistence;

import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.job.JobAttemptStatus;
import com.sca4d.job.JobNodeStatus;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 使用 MyBatis 执行 Job run、节点、依赖和尝试的单条 PostgreSQL 读写。 */
public interface JobMapper {

    /** 返回当前命名空间、Job 定义和计划共同限定的运行数量。 */
    long countScheduledRuns(
            @Param("runtimeNamespace") @NotNull String runtimeNamespace,
            @Param("scheduleKey") @NotNull String scheduleKey,
            @Param("jobKey") @NotNull String jobKey,
            @Param("definitionVersion") int definitionVersion);

    /** 返回指定计划的运行分页以及目标成功节点的输出。 */
    @NotNull
    List<ScheduledRunRecord> selectScheduledRuns(
            @Param("runtimeNamespace") @NotNull String runtimeNamespace,
            @Param("scheduleKey") @NotNull String scheduleKey,
            @Param("jobKey") @NotNull String jobKey,
            @Param("definitionVersion") int definitionVersion,
            @Param("nodeKey") @NotNull String nodeKey,
            @Param("page") @NotNull PageRequest page);

    /** 插入根运行并返回数据库主键。 */
    long insertRun(
            @Param("runtimeNamespace") @NotNull String runtimeNamespace,
            @Param("jobKey") @NotNull String jobKey,
            @Param("definitionVersion") int definitionVersion,
            @Param("kind") @NotNull String kind,
            @Param("inputJson") @Nullable String inputJson,
            @Param("scheduleKey") @Nullable String scheduleKey,
            @Param("scheduledAt") @Nullable Instant scheduledAt);

    /** 按计划、可选触发时间及状态读取首个匹配运行。 */
    @Nullable
    Long selectScheduledRun(
            @Param("runtimeNamespace") @NotNull String runtimeNamespace,
            @Param("scheduleKey") @NotNull String scheduleKey,
            @Param("scheduledAt") @Nullable Instant scheduledAt,
            @Param("statuses") @Nullable List<JobRunStatus> statuses);

    /** 插入运行节点并返回数据库主键。 */
    long insertNode(
            @Param("runId") long runId,
            @Param("nodeKey") @NotNull String nodeKey,
            @Param("kind") @NotNull String kind,
            @Param("inputJson") @Nullable String inputJson,
            @Param("status") @NotNull JobNodeStatus status,
            @Param("parallelMerge") boolean parallelMerge,
            @Param("maxAttempts") int maxAttempts,
            @Param("retryDelayMillis") long retryDelayMillis,
            @Param("availableAt") @NotNull Instant availableAt);

    /** 插入本次运行已经解析的节点依赖；外层键为节点标识，内层键为输入顺序、值为依赖节点标识。 */
    int insertDependencies(
            @Param("runId") long runId,
            @Param("dependencies") @NotNull Map<Long, Map<Integer, Long>> dependencies);

    /** 按运行时命名空间和主键查询根运行列值。 */
    @Nullable
    RunRecord selectRun(
            @Param("runtimeNamespace") @NotNull String runtimeNamespace,
            @Param("runId") long runId);

    /** 原子领取一个可执行节点并登记本次尝试。 */
    @Nullable
    NodeClaim updateClaimableNode(
            @Param("runtimeNamespace") @NotNull String runtimeNamespace,
            @Param("workerId") @NotNull String workerId,
            @Param("now") @NotNull Instant now,
            @Param("leaseUntil") @NotNull Instant leaseUntil);

    /** 按节点状态和租约 owner 更新租约结束时间。 */
    int updateNodeLease(
            @Param("nodeId") long nodeId,
            @Param("workerId") @NotNull String workerId,
            @Param("leaseUntil") @NotNull Instant leaseUntil);

    /** 查询节点直接依赖的输出列。 */
    @NotNull
    List<@Nullable String> selectDependencyOutputs(@Param("nodeId") long nodeId);

    /** 查询是否存在直接依赖当前节点的记录。 */
    boolean selectHasDependents(@Param("nodeId") long nodeId);

    /** 按本次租约和尝试序号更新节点与尝试字段。 */
    int updateNodeProgress(
            @Param("nodeId") long nodeId,
            @Param("attempt") int attempt,
            @Param("workerId") @NotNull String workerId,
            @Param("nodeStatus") @NotNull Patch<JobNodeStatus> nodeStatus,
            @Param("attemptStatus") @NotNull Patch<JobAttemptStatus> attemptStatus,
            @Param("outputJson") @NotNull Patch<String> outputJson,
            @Param("error") @NotNull Patch<String> error,
            @Param("availableAt") @NotNull Patch<Instant> availableAt,
            @Param("nodeCompletedAt") @NotNull Patch<Instant> nodeCompletedAt,
            @Param("completedAt") @NotNull Patch<Instant> completedAt);

    /** 将依赖已经全部成功的等待节点改为可领取。 */
    int updateDependentNodes(
            @Param("sourceNodeId") long sourceNodeId,
            @Param("availableAt") @NotNull Instant availableAt);

    /** 按当前未结束状态写入根运行结果字段。 */
    int updateRunResult(
            @Param("runId") long runId,
            @Param("status") @NotNull Patch<JobRunStatus> status,
            @Param("outputJson") @NotNull Patch<String> outputJson,
            @Param("completedAt") @NotNull Patch<Instant> completedAt);

    /** 原子登记最终失败、失败根运行并取消后继节点。 */
    int updateNodeFailed(
            @Param("nodeId") long nodeId,
            @Param("runId") long runId,
            @Param("attempt") int attempt,
            @Param("workerId") @NotNull String workerId,
            @Param("error") @NotNull String error,
            @Param("failedAt") @NotNull Instant failedAt);

    /** 失败当前命名空间内租约过期且重试耗尽的节点及其根运行。 */
    int updateExpiredNodes(
            @Param("runtimeNamespace") @NotNull String runtimeNamespace,
            @Param("now") @NotNull Instant now);

    /** 锁定当前命名空间内待清理的成功根运行，跳过其他清理者已持锁的行。 */
    @NotNull
    List<Long> selectExpiredRunIdsForUpdate(
            @Param("runtimeNamespace") @NotNull String runtimeNamespace,
            @Param("completedBefore") @NotNull Instant completedBefore,
            @Param("limit") int limit);

    /** 删除指定完整运行中的节点尝试。 */
    int deleteAttempts(@Param("runIds") @NotNull List<Long> runIds);

    /** 删除指定完整运行中的节点依赖关系。 */
    int deleteDependencies(@Param("runIds") @NotNull List<Long> runIds);

    /** 删除指定运行中的全部节点。 */
    int deleteNodes(@Param("runIds") @NotNull List<Long> runIds);

    /** 删除已锁定的指定成功根运行，返回实际根记录数量。 */
    int deleteRuns(
            @Param("runtimeNamespace") @NotNull String runtimeNamespace,
            @Param("runIds") @NotNull List<Long> runIds);
}
