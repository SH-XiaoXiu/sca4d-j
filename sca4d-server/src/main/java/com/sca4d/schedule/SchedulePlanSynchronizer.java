package com.sca4d.schedule;

import org.jetbrains.annotations.NotNull;

/** 把数据库中的 Schedule 定义同步为 Quartz Job 和 Trigger，并修复两者差异。 */
public interface SchedulePlanSynchronizer {

    /** @param scheduleId 已提交数据库事务的 Schedule 主键 */
    void synchronize(@NotNull Long scheduleId);

    /** @param scheduleId 已进入删除终态且不应保留执行计划的 Schedule 主键 */
    void remove(@NotNull Long scheduleId);

    /** 在集群启动锁内使全部业务执行计划与数据库数据完全一致。 */
    void reconcileAll();

    /**
     * 为已经持久化的人工执行数据触发一次 Job，不改变原计划 Trigger。
     *
     * @param scheduleId ACTIVE 或 PAUSED Schedule 主键
     * @param executionId 服务端已经创建的 PENDING 执行主键
     */
    void startManualExecution(@NotNull Long scheduleId, @NotNull Long executionId);
}
