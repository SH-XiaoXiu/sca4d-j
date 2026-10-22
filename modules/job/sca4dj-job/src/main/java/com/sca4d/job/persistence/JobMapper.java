package com.sca4d.job.persistence;

import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.job.JobRunStatus;
import com.sca4d.core.api.job.JobWorkKey;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.job.JobAttemptStatus;
import com.sca4d.job.JobNodeStatus;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 使用 MyBatis 执行 Job run、节点、依赖和尝试的单条 PostgreSQL 读写。 */
public interface JobMapper {

    /** 返回当前 App 隔离名称、Job 定义和计划共同限定的运行数量。 */
    long countScheduledRuns(
            @Param("appName") @NotNull Name appName,
            @Param("scheduleKey") @NotNull ScheduleKey scheduleKey,
            @Param("jobKey") @NotNull JobKey jobKey,
            @Param("definitionVersion") int definitionVersion);

    /** 返回指定计划的运行分页以及目标成功节点的输出。 */
    @NotNull
    List<ScheduledRunRecord> selectScheduledRuns(
            @Param("appName") @NotNull Name appName,
            @Param("scheduleKey") @NotNull ScheduleKey scheduleKey,
            @Param("jobKey") @NotNull JobKey jobKey,
            @Param("definitionVersion") int definitionVersion,
            @Param("nodeKey") @NotNull String nodeKey,
            @Param("page") @NotNull PageRequest page);

    /** 插入根运行并返回数据库主键。 */
    long insertRun(
            @Param("appName") @NotNull Name appName,
            @Param("jobKey") @NotNull JobKey jobKey,
            @Param("definitionVersion") int definitionVersion,
            @Param("kind") @NotNull String kind,
            @Param("capability") @NotNull TreeCode capability,
            @Param("systemIdentity") @NotNull TreeCode systemIdentity,
            @Param("workKey") @NotNull JobWorkKey workKey,
            @Param("overlapPolicy") @NotNull String overlapPolicy,
            @Param("targetInstanceId") @Nullable String targetInstanceId,
            @Param("targetGenerationId") @Nullable UUID targetGenerationId,
            @Param("targetExpiresAt") @Nullable Instant targetExpiresAt,
            @Param("dispatchId") @Nullable Long dispatchId,
            @Param("inputJson") @Nullable String inputJson,
            @Param("scheduleKey") @Nullable ScheduleKey scheduleKey,
            @Param("scheduledAt") @Nullable Instant scheduledAt);

    /** 按计划、可选触发时间及状态读取首个匹配运行。 */
    @Nullable
    Long selectScheduledRun(
            @Param("appName") @NotNull Name appName,
            @Param("scheduleKey") @NotNull ScheduleKey scheduleKey,
            @Param("scheduledAt") @NotNull Instant scheduledAt,
            @Param("targetInstanceId") @Nullable String targetInstanceId);

    /** 查询同一系统身份、Job 和工作键下尚未结束的 Run。 */
    @Nullable
    RunRecord selectActiveWork(
            @Param("appName") @NotNull Name appName,
            @Param("systemIdentity") @NotNull TreeCode systemIdentity,
            @Param("jobKey") @NotNull JobKey jobKey,
            @Param("workKey") @NotNull JobWorkKey workKey,
            @Param("targetInstanceId") @Nullable String targetInstanceId);

    /** 登记或续约当前执行实例和启用能力。 */
    int upsertInstance(
            @Param("appName") @NotNull Name appName,
            @Param("instanceId") @NotNull String instanceId,
            @Param("generationId") @NotNull UUID generationId,
            @Param("capabilitiesJson") @NotNull String capabilitiesJson,
            @Param("expiresAt") @NotNull Instant expiresAt);

    /** 查找提交时在线且启用目标能力的实例。 */
    @NotNull
    List<JobInstance> selectEligibleInstances(
            @Param("appName") @NotNull Name appName,
            @Param("capabilityCode") @NotNull String capabilityCode,
            @Param("now") @NotNull Instant now);

    /** 删除本次进程启动代次的实例登记。 */
    int deleteInstance(
            @Param("appName") @NotNull Name appName,
            @Param("instanceId") @NotNull String instanceId,
            @Param("generationId") @NotNull UUID generationId);

    /** 删除租约早已结束的实例登记。 */
    int deleteExpiredInstances(
            @Param("appName") @NotNull Name appName,
            @Param("expiredBefore") @NotNull Instant expiredBefore);

    /** 为一次普通或定时广播创建完整的目标清单。 */
    long insertDispatch(
            @Param("appName") @NotNull Name appName,
            @Param("jobKey") @NotNull JobKey jobKey,
            @Param("scheduleKey") @Nullable ScheduleKey scheduleKey,
            @Param("scheduledAt") @Nullable Instant scheduledAt,
            @Param("targetsJson") @NotNull String targetsJson);

    /** 查询同一次定时触发已经固定的广播目标。 */
    @Nullable
    JobDispatchRecord selectScheduledDispatch(
            @Param("appName") @NotNull Name appName,
            @Param("scheduleKey") @NotNull ScheduleKey scheduleKey,
            @Param("scheduledAt") @NotNull Instant scheduledAt);

    /** 读取某次广播下已经创建的全部持久化 Run。 */
    @NotNull
    List<JobDispatchRun> selectDispatchRuns(
            @Param("appName") @NotNull Name appName,
            @Param("dispatchId") long dispatchId);

    /** 查询当前实例上同一工作键的活动短期投递。 */
    @Nullable
    Long selectActiveDelivery(
            @Param("appName") @NotNull Name appName,
            @Param("systemIdentity") @NotNull TreeCode systemIdentity,
            @Param("jobKey") @NotNull JobKey jobKey,
            @Param("workKey") @NotNull JobWorkKey workKey,
            @Param("targetInstanceId") @NotNull String targetInstanceId);

    /** 为无记录广播或固定目标创建短期投递份额。 */
    int insertDelivery(
            @Param("appName") @NotNull Name appName,
            @Param("dispatchId") long dispatchId,
            @Param("jobKey") @NotNull JobKey jobKey,
            @Param("definitionVersion") int definitionVersion,
            @Param("capability") @NotNull TreeCode capability,
            @Param("systemIdentity") @NotNull TreeCode systemIdentity,
            @Param("workKey") @NotNull JobWorkKey workKey,
            @Param("overlapPolicy") @NotNull String overlapPolicy,
            @Param("inputJson") @Nullable String inputJson,
            @Param("targetInstanceId") @NotNull String targetInstanceId,
            @Param("targetGenerationId") @Nullable UUID targetGenerationId,
            @Param("expiresAt") @NotNull Instant expiresAt);

    /** 按实例代次领取一份无记录工作。 */
    @Nullable
    JobDelivery updateClaimableDelivery(
            @Param("appName") @NotNull Name appName,
            @Param("instanceId") @NotNull String instanceId,
            @Param("generationId") @NotNull UUID generationId,
            @Param("now") @NotNull Instant now,
            @Param("leaseUntil") @NotNull Instant leaseUntil,
            @Param("definitions") @NotNull Map<JobKey, Map<Integer, InstanceCapability>> definitions);

    /** 续期仍由目标实例代次持有的无记录工作份额。 */
    int updateDeliveryLease(
            @Param("deliveryId") long deliveryId,
            @Param("instanceId") @NotNull String instanceId,
            @Param("generationId") @NotNull UUID generationId,
            @Param("now") @NotNull Instant now,
            @Param("leaseUntil") @NotNull Instant leaseUntil);

    /** 保存无记录工作完成或失败；不重新投递。 */
    int updateDeliveryResult(
            @Param("deliveryId") long deliveryId,
            @Param("instanceId") @NotNull String instanceId,
            @Param("generationId") @NotNull UUID generationId,
            @Param("status") @NotNull String status,
            @Param("error") @Nullable String error,
            @Param("completedAt") @NotNull Instant completedAt);

    /** 标记固定目标已过期的未开始或失联无记录工作。 */
    int updateExpiredDeliveries(
            @Param("appName") @NotNull Name appName,
            @Param("now") @NotNull Instant now);

    /** 删除已结束且超过短期协调保留时间的无记录份额。 */
    int deleteCompletedDeliveries(
            @Param("appName") @NotNull Name appName,
            @Param("completedBefore") @NotNull Instant completedBefore);

    /** 删除不再有份额或 Run 的过期广播头。 */
    int deleteCompletedDispatches(
            @Param("appName") @NotNull Name appName,
            @Param("createdBefore") @NotNull Instant createdBefore);

    /** 插入运行节点并返回数据库主键。 */
    long insertNode(
            @Param("runId") long runId,
            @Param("nodeKey") @NotNull String nodeKey,
            @Param("kind") @NotNull String kind,
            @Param("inputJson") @Nullable String inputJson,
            @Param("status") @NotNull JobNodeStatus status,
            @Param("parallelMerge") boolean parallelMerge,
            @Param("retryRule") @NotNull RetryRule retryRule,
            @Param("availableAt") @NotNull Instant availableAt);

    /** 插入本次运行已经解析的节点依赖；外层键为节点标识，内层键为输入顺序、值为依赖节点标识。 */
    int insertDependencies(
            @Param("runId") long runId,
            @Param("dependencies") @NotNull Map<Long, Map<Integer, Long>> dependencies);

    /** 按 App 隔离名称和主键查询根运行列值。 */
    @Nullable
    RunRecord selectRun(
            @Param("appName") @NotNull Name appName,
            @Param("runId") long runId);

    /** 在同一个根运行的并发分支推进前取得数据库行锁。 */
    @Nullable
    Long selectRunIdForUpdate(
            @Param("appName") @NotNull Name appName,
            @Param("runId") long runId);

    /** 原子领取一个可执行节点并登记本次尝试。 */
    @Nullable
    NodeClaim updateClaimableNode(
            @Param("appName") @NotNull Name appName,
            @Param("workerId") @NotNull String workerId,
            @Param("instanceId") @NotNull String instanceId,
            @Param("generationId") @NotNull UUID generationId,
            @Param("now") @NotNull Instant now,
            @Param("leaseUntil") @NotNull Instant leaseUntil,
            @Param("definitions") @NotNull Map<JobKey, Map<Integer, InstanceCapability>> definitions);

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
            @Param("attempt") long attempt,
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
            @Param("attempt") long attempt,
            @Param("workerId") @NotNull String workerId,
            @Param("error") @NotNull String error,
            @Param("failedAt") @NotNull Instant failedAt);

    /** 锁定当前应用仍有尝试额度的过期租约，避免重复计算恢复时间。 */
    @NotNull
    List<ExpiredJobAttempt> selectExpiredAttempts(
            @Param("appName") @NotNull Name appName,
            @Param("now") @NotNull Instant now);

    /** 按旧租约原子放弃尝试并恢复 READY，返回结束的尝试数。 */
    int rescheduleExpired(
            @Param("nodeId") long nodeId,
            @Param("attempt") long attempt,
            @Param("workerId") @NotNull String workerId,
            @Param("leaseUntil") @NotNull Instant leaseUntil,
            @Param("availableAt") @NotNull Instant availableAt,
            @Param("now") @NotNull Instant now);

    /** 失败当前 App 隔离名称内租约过期且重试耗尽的节点及其根运行。 */
    int updateExpiredNodes(
            @Param("appName") @NotNull Name appName,
            @Param("now") @NotNull Instant now);

    /** 将固定目标等待期限已经过去的未开始运行标为失败。 */
    int updateExpiredTargets(
            @Param("appName") @NotNull Name appName,
            @Param("now") @NotNull Instant now);

    /** 锁定当前 App 隔离名称内待清理的成功根运行，跳过其他清理者已持锁的行。 */
    @NotNull
    List<Long> selectExpiredRunIdsForUpdate(
            @Param("appName") @NotNull Name appName,
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
            @Param("appName") @NotNull Name appName,
            @Param("runIds") @NotNull List<Long> runIds);
}
