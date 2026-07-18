package com.sca4d.schedule;

import com.sca4d.common.web.PageData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/** 允许 PLATFORM Principal 创建和修改 Schedule，并查询或人工触发 execution。 */
public interface ScheduleService {

    /** 按名称、类型、状态和任务类型分页读取未删除 Schedule。 */
    @NotNull
    PageData<Schedule> page(
            @Nullable String keyword,
            @Nullable ScheduleType scheduleType,
            @Nullable ScheduleStatus status,
            @Nullable String taskType,
            long page,
            long size);

    /** @param scheduleId Schedule 主键 @return 当前未删除 Schedule @throws BizException 不存在时抛出 */
    @NotNull
    Schedule require(@NotNull Long scheduleId);

    /**
     * 创建默认 ACTIVE 的 Schedule，并在数据库事务提交后同步 Quartz 执行计划。
     *
     * @param name 管理端识别计划的业务名称
     * @param scheduleType ONCE 或 CRON
     * @param executeAt ONCE 的未来执行时间
     * @param cronExpression CRON 的 Quartz 表达式
     * @param timeZone CRON 的 IANA 时区
     * @param taskType 已注册开放任务类型
     * @param taskPayloadJson 当前任务类型的完整参数 JSON
     * @param createdByPrincipalId 创建操作的 PLATFORM Principal 主键
     * @return 已持久化的完整 Schedule 定义
     */
    @NotNull
    Schedule create(
            @NotNull String name,
            @NotNull ScheduleType scheduleType,
            @Nullable OffsetDateTime executeAt,
            @Nullable String cronExpression,
            @Nullable String timeZone,
            @NotNull String taskType,
            @NotNull String taskPayloadJson,
            @NotNull Long createdByPrincipalId);

    /**
     * 使用 If-Match 版本完整替换 Schedule 可修改表示。
     *
     * @return 更新后的完整 Schedule 定义
     */
    @NotNull
    Schedule update(
            @NotNull Long scheduleId,
            @NotNull String name,
            @NotNull ScheduleType scheduleType,
            @Nullable OffsetDateTime executeAt,
            @Nullable String cronExpression,
            @Nullable String timeZone,
            @NotNull ScheduleStatus status,
            @NotNull String taskType,
            @NotNull String taskPayloadJson,
            long expectedVersion);

    /** 使用 If-Match 版本把 Schedule 置为内部取消终态，并在提交后删除 Quartz 执行计划。 */
    void delete(@NotNull Long scheduleId, long expectedVersion);

    /** 分页读取指定 Schedule 的计划、人工和恢复执行数据。 */
    @NotNull
    PageData<ScheduleExecution> pageExecutions(@NotNull Long scheduleId, long page, long size);

    /** @return 指定 Schedule 下的单次执行数据 @throws BizException 不存在时抛出 */
    @NotNull
    ScheduleExecution requireExecution(@NotNull Long scheduleId, @NotNull Long executionId);

    /** 创建 PENDING 人工执行数据，并在事务提交后触发一次不改变原计划的 Quartz Job。 */
    @NotNull
    ScheduleExecution createManualExecution(@NotNull Long scheduleId);

    /** @return 按 taskType 固定排列的已注册任务类型目录 */
    @NotNull
    List<ScheduleTaskType> taskTypes();
}
