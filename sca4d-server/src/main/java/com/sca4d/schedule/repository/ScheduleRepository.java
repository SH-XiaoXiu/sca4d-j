package com.sca4d.schedule.repository;

import static com.mybatisflex.core.query.QueryMethods.column;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.sca4d.common.web.PageData;
import com.sca4d.entity.ScheduleDefinitionEntity;
import com.sca4d.entity.ScheduleExecutionEntity;
import com.sca4d.entity.ScheduleJobRuntimeEntity;
import com.sca4d.mapper.ScheduleDefinitionMapper;
import com.sca4d.mapper.ScheduleExecutionMapper;
import com.sca4d.mapper.ScheduleJobRuntimeMapper;
import com.sca4d.schedule.ExecutionStatus;
import com.sca4d.schedule.ScheduleStatus;
import com.sca4d.schedule.ScheduleType;
import com.sca4d.schedule.TriggerSource;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 读写 Schedule 定义和单次执行记录，并查询系统 Job 最近一次运行结果。 */
@Repository
@RequiredArgsConstructor
public class ScheduleRepository {

    private final ScheduleDefinitionMapper definitionMapper;
    private final ScheduleExecutionMapper executionMapper;
    private final ScheduleJobRuntimeMapper runtimeMapper;

    /** 保存已经完成业务校验和指纹计算的新 Schedule 定义。 */
    public void create(@NotNull ScheduleDefinitionEntity schedule) {
        if (definitionMapper.insertDefinition(schedule) != 1) {
            throw new IllegalStateException("Schedule 写入行数异常");
        }
    }

    /** @return 当前未删除 Schedule；不存在或已取消时返回空 */
    public @NotNull Optional<ScheduleDefinitionEntity> find(@NotNull Long scheduleId) {
        QueryWrapper query = selectDefinitions()
                .where(ScheduleDefinitionEntity::getId).eq(scheduleId)
                .and(ScheduleDefinitionEntity::getDeleted).eq(0);
        return Optional.ofNullable(definitionMapper.selectOneByQuery(query));
    }

    /** @return 包含内部取消终态的 Schedule；数据库中从未存在时返回空 */
    public @NotNull Optional<ScheduleDefinitionEntity> findIncludingCancelled(@NotNull Long scheduleId) {
        return Optional.ofNullable(definitionMapper.selectOneByQuery(
                selectDefinitions().where(ScheduleDefinitionEntity::getId).eq(scheduleId)));
    }

    /** 使用一条单表 SQL 批量读取资源引用需要的 Schedule 名称和删除状态。 */
    public @NotNull List<ScheduleDefinitionEntity> findReferences(@NotNull Set<Long> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(ScheduleDefinitionEntity::getId),
                        column(ScheduleDefinitionEntity::getName),
                        column(ScheduleDefinitionEntity::getDeleted))
                .from(ScheduleDefinitionEntity.class)
                .where(ScheduleDefinitionEntity::getId).in(scheduleIds);
        return definitionMapper.selectListByQueryAs(query, ScheduleDefinitionEntity.class);
    }

    /** 使用一条单表 SQL 批量读取资源引用需要的执行主键和所属 Schedule。 */
    public @NotNull List<ScheduleExecutionEntity> findExecutionReferences(@NotNull Set<Long> executionIds) {
        if (executionIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(
                        column(ScheduleExecutionEntity::getExecutionId),
                        column(ScheduleExecutionEntity::getScheduleId))
                .from(ScheduleExecutionEntity.class)
                .where(ScheduleExecutionEntity::getExecutionId).in(executionIds);
        return executionMapper.selectListByQueryAs(query, ScheduleExecutionEntity.class);
    }

    /** 按管理端筛选条件分页读取未删除 Schedule。 */
    public @NotNull PageData<ScheduleDefinitionEntity> page(
            @Nullable String keyword,
            @Nullable ScheduleType scheduleType,
            @Nullable ScheduleStatus status,
            @Nullable String taskType,
            long page,
            long size) {
        QueryWrapper query = selectDefinitions()
                .where(ScheduleDefinitionEntity::getDeleted).eq(0)
                .and(ScheduleDefinitionEntity::getScheduleType).eq(scheduleType, Objects::nonNull)
                .and(ScheduleDefinitionEntity::getStatus).eq(status, Objects::nonNull)
                .and(ScheduleDefinitionEntity::getTaskType).eq(taskType, StringUtils::hasText);
        if (StringUtils.hasText(keyword)) {
            query.and((QueryWrapper scope) -> {
                scope.where(ScheduleDefinitionEntity::getName)
                        .like(keyword.trim())
                        .or("CAST(schedule_definition.id AS text) = ?", keyword.trim());
            });
        }
        query.orderBy(ScheduleDefinitionEntity::getCreatedAt).desc()
                .orderBy(ScheduleDefinitionEntity::getId).desc();
        return PageData.from(definitionMapper.paginate(new Page<>(page, size), query));
    }

    /** @return 启动对账所需的全部 Schedule，包括内部终态 */
    public @NotNull List<ScheduleDefinitionEntity> findForReconciliation() {
        QueryWrapper query = selectDefinitions().orderBy(ScheduleDefinitionEntity::getId).asc();
        return definitionMapper.selectListByQuery(query);
    }

    /** 使用原子版本条件完整替换可修改 Schedule 定义。 */
    public boolean update(@NotNull ScheduleDefinitionEntity schedule, long expectedVersion) {
        return definitionMapper.updateDefinition(schedule, expectedVersion) == 1;
    }

    /** 使用原子版本条件把 Schedule 保存为内部取消终态。 */
    public boolean cancel(@NotNull Long scheduleId, long expectedVersion, @NotNull OffsetDateTime updatedAt) {
        ScheduleDefinitionEntity update = new ScheduleDefinitionEntity()
                .setStatus(ScheduleStatus.CANCELLED)
                .setDeleted(1)
                .setVersion(expectedVersion + 1)
                .setUpdatedAt(updatedAt);
        QueryWrapper condition = QueryWrapper.create()
                .where(ScheduleDefinitionEntity::getId).eq(scheduleId)
                .and(ScheduleDefinitionEntity::getVersion).eq(expectedVersion)
                .and(ScheduleDefinitionEntity::getDeleted).eq(0)
                .and(ScheduleDefinitionEntity::getStatus)
                .notIn(ScheduleStatus.COMPLETED, ScheduleStatus.FAILED, ScheduleStatus.CANCELLED);
        return definitionMapper.updateByQuery(update, condition) == 1;
    }

    /** 单次执行完成后，按规则原子更新 Schedule 的下一状态。 */
    public boolean changeStatus(
            @NotNull Long scheduleId,
            @NotNull ScheduleStatus expectedStatus,
            @NotNull ScheduleStatus status,
            long expectedVersion,
            @NotNull OffsetDateTime updatedAt) {
        ScheduleDefinitionEntity update = new ScheduleDefinitionEntity()
                .setStatus(status)
                .setVersion(expectedVersion + 1)
                .setUpdatedAt(updatedAt);
        QueryWrapper condition = QueryWrapper.create()
                .where(ScheduleDefinitionEntity::getId).eq(scheduleId)
                .and(ScheduleDefinitionEntity::getStatus).eq(expectedStatus)
                .and(ScheduleDefinitionEntity::getVersion).eq(expectedVersion)
                .and(ScheduleDefinitionEntity::getDeleted).eq(0);
        return definitionMapper.updateByQuery(update, condition) == 1;
    }

    /** 覆盖最近一次 Quartz 投影错误；传入 null 表示当前数据库数据已经同步。 */
    public void updateSynchronizationError(
            @NotNull Long scheduleId, @Nullable String synchronizationError, @NotNull OffsetDateTime updatedAt) {
        definitionMapper.updateSynchronizationError(scheduleId, synchronizationError, updatedAt);
    }

    /** 创建 fireKey 唯一的执行数据；已经存在时不取得执行所有权。 */
    public boolean createExecution(@NotNull ScheduleExecutionEntity execution) {
        return executionMapper.insertIgnoreDuplicate(execution) == 1;
    }

    /** @return 指定 Schedule 下的执行数据；不存在时返回空 */
    public @NotNull Optional<ScheduleExecutionEntity> findExecution(
            @NotNull Long scheduleId, @NotNull Long executionId) {
        QueryWrapper query = QueryWrapper.create()
                .where(ScheduleExecutionEntity::getScheduleId).eq(scheduleId)
                .and(ScheduleExecutionEntity::getExecutionId).eq(executionId);
        return Optional.ofNullable(executionMapper.selectOneByQuery(query));
    }

    /** @return 指定 Schedule 和 fireKey 的幂等执行数据；不存在时返回空 */
    public @NotNull Optional<ScheduleExecutionEntity> findExecutionByFireKey(
            @NotNull Long scheduleId, @NotNull String fireKey) {
        QueryWrapper query = QueryWrapper.create()
                .where(ScheduleExecutionEntity::getScheduleId).eq(scheduleId)
                .and(ScheduleExecutionEntity::getFireKey).eq(fireKey);
        return Optional.ofNullable(executionMapper.selectOneByQuery(query));
    }

    /** 分页读取指定 Schedule 的全部执行数据。 */
    public @NotNull PageData<ScheduleExecutionEntity> pageExecutions(
            @NotNull Long scheduleId, long page, long size) {
        QueryWrapper query = QueryWrapper.create()
                .where(ScheduleExecutionEntity::getScheduleId).eq(scheduleId)
                .orderBy(ScheduleExecutionEntity::getCreatedAt).desc()
                .orderBy(ScheduleExecutionEntity::getExecutionId).desc();
        return PageData.from(executionMapper.paginate(new Page<>(page, size), query));
    }

    /** 由 PENDING 或 FAILED 原子领取一次待执行数据。 */
    public boolean claimExecution(
            @NotNull ScheduleExecutionEntity execution,
            @NotNull TriggerSource triggerSource,
            @NotNull String executedInstanceId,
            @NotNull OffsetDateTime actualFireTime,
            @NotNull Set<ExecutionStatus> claimableStatuses) {
        ScheduleExecutionEntity update = new ScheduleExecutionEntity()
                .setStatus(ExecutionStatus.RUNNING)
                .setTriggerSource(triggerSource)
                .setExecutedInstanceId(executedInstanceId)
                .setActualFireTime(actualFireTime)
                .setUpdatedAt(actualFireTime)
                .setVersion(execution.getVersion() + 1);
        QueryWrapper condition = QueryWrapper.create()
                .where(ScheduleExecutionEntity::getExecutionId).eq(execution.getExecutionId())
                .and(ScheduleExecutionEntity::getVersion).eq(execution.getVersion())
                .and(ScheduleExecutionEntity::getStatus).in(claimableStatuses);
        return executionMapper.updateByQuery(update, condition) == 1;
    }

    /** 由 RUNNING 原子保存成功、失败或跳过终态。 */
    public boolean completeExecution(
            @NotNull ScheduleExecutionEntity execution,
            @NotNull ExecutionStatus status,
            @Nullable String resultSummary,
            @Nullable String safeErrorType,
            @Nullable String safeErrorMessage,
            @NotNull OffsetDateTime completedAt) {
        ScheduleExecutionEntity update = new ScheduleExecutionEntity()
                .setStatus(status)
                .setResultSummary(resultSummary)
                .setSafeErrorType(safeErrorType)
                .setSafeErrorMessage(safeErrorMessage)
                .setCompletedAt(completedAt)
                .setUpdatedAt(completedAt)
                .setVersion(execution.getVersion() + 1);
        QueryWrapper condition = QueryWrapper.create()
                .where(ScheduleExecutionEntity::getExecutionId).eq(execution.getExecutionId())
                .and(ScheduleExecutionEntity::getVersion).eq(execution.getVersion())
                .and(ScheduleExecutionEntity::getStatus).eq(ExecutionStatus.RUNNING);
        return executionMapper.updateByQuery(update, condition) == 1;
    }

    /** 保存 FAILED 或 SKIPPED 数据，重复 fireKey 永不覆盖已经提交的 SUCCESS。 */
    public void saveUnsuccessfulExecution(@NotNull ScheduleExecutionEntity execution) {
        executionMapper.upsertUnsuccessful(execution);
    }

    /** @return 仍需创建一次性 Quartz Trigger 的有效人工执行数据 */
    public @NotNull List<ScheduleExecutionEntity> findPendingManualExecutions() {
        return executionMapper.selectPendingManualExecutions();
    }

    /** 原子覆盖同一 JobKey 的最近一次系统运行快照。 */
    public void saveJobRuntime(@NotNull ScheduleJobRuntimeEntity runtime) {
        runtimeMapper.upsert(runtime);
    }

    /** @return 指定系统 Job 的最近运行快照；尚未执行时返回空 */
    public @NotNull Optional<ScheduleJobRuntimeEntity> findJobRuntime(
            @NotNull String jobGroup, @NotNull String jobName) {
        QueryWrapper query = QueryWrapper.create()
                .where(ScheduleJobRuntimeEntity::getJobGroup).eq(jobGroup)
                .and(ScheduleJobRuntimeEntity::getJobName).eq(jobName);
        return Optional.ofNullable(runtimeMapper.selectOneByQuery(query));
    }

    /** 使用一条 SQL 批量读取 system 分组中指定 Job 的最近运行快照。 */
    public @NotNull Map<String, ScheduleJobRuntimeEntity> findSystemJobRuntimes(@NotNull List<String> jobNames) {
        if (jobNames.isEmpty()) {
            return Map.of();
        }
        QueryWrapper query = QueryWrapper.create()
                .where(ScheduleJobRuntimeEntity::getJobGroup).eq("system")
                .and(ScheduleJobRuntimeEntity::getJobName).in(jobNames);
        return runtimeMapper.selectListByQuery(query).stream()
                .collect(Collectors.toUnmodifiableMap(ScheduleJobRuntimeEntity::getJobName, Function.identity()));
    }

    /**
     * 创建共用的 Schedule 定义查询投影。
     *
     * <p>PostgreSQL JSONB 显式转换为 text，使 Entity 只理解长期 JSON 内容，不依赖 JDBC PGobject。
     *
     * @return 尚未附加筛选和排序条件的类型安全查询
     */
    private static @NotNull QueryWrapper selectDefinitions() {
        return QueryWrapper.create().select(
                column(ScheduleDefinitionEntity::getId),
                column(ScheduleDefinitionEntity::getName),
                column(ScheduleDefinitionEntity::getScheduleType),
                column(ScheduleDefinitionEntity::getExecuteAt),
                column(ScheduleDefinitionEntity::getCronExpression),
                column(ScheduleDefinitionEntity::getTimeZone),
                column(ScheduleDefinitionEntity::getStatus),
                column(ScheduleDefinitionEntity::getTaskType),
                column(ScheduleDefinitionEntity::getTaskPayload).cast("text").as(ScheduleDefinitionEntity::getTaskPayload),
                column(ScheduleDefinitionEntity::getTaskPayloadVersion),
                column(ScheduleDefinitionEntity::getDefinitionFingerprint),
                column(ScheduleDefinitionEntity::getSynchronizationError),
                column(ScheduleDefinitionEntity::getCreatedByPrincipalId),
                column(ScheduleDefinitionEntity::getVersion),
                column(ScheduleDefinitionEntity::getCreatedAt),
                column(ScheduleDefinitionEntity::getUpdatedAt),
                column(ScheduleDefinitionEntity::getDeleted));
    }
}
