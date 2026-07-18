package com.sca4d.schedule;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

/** ScheduleTask 单次执行使用的主键、幂等键、触发来源和时间。 */
public final class ScheduleTaskExecution {

    private final Long scheduleId;
    private final Long executionId;
    private final String fireKey;
    private final TriggerSource triggerSource;
    private final OffsetDateTime scheduledFireTime;
    private final OffsetDateTime actualFireTime;

    /**
     * 固化一次任务执行的幂等标识、来源和时间数据。
     *
     * @param scheduleId Schedule 主键
     * @param executionId 本次执行主键
     * @param fireKey 同一触发或恢复过程复用的幂等键
     * @param triggerSource 计划、人工或故障恢复来源
     * @param scheduledFireTime Quartz 计划触发时间；人工执行时为空
     * @param actualFireTime 当前节点实际开始执行时间
     */
    public ScheduleTaskExecution(
            @NotNull Long scheduleId,
            @NotNull Long executionId,
            @NotNull String fireKey,
            @NotNull TriggerSource triggerSource,
            @Nullable OffsetDateTime scheduledFireTime,
            @NotNull OffsetDateTime actualFireTime) {
        this.scheduleId = scheduleId;
        this.executionId = executionId;
        this.fireKey = fireKey;
        this.triggerSource = triggerSource;
        this.scheduledFireTime = scheduledFireTime;
        this.actualFireTime = actualFireTime;
    }

    /** @return 当前执行所属 Schedule 主键 */
    public @NotNull Long getScheduleId() {
        return scheduleId;
    }

    /** @return 当前执行主键 */
    public @NotNull Long getExecutionId() {
        return executionId;
    }

    /** @return 当前执行的固定幂等键 */
    public @NotNull String getFireKey() {
        return fireKey;
    }

    /** @return 当前执行的触发来源 */
    public @NotNull TriggerSource getTriggerSource() {
        return triggerSource;
    }

    /** @return Quartz 计划触发时间；人工执行时为空 */
    public @Nullable OffsetDateTime getScheduledFireTime() {
        return scheduledFireTime;
    }

    /** @return 当前节点实际开始执行时间 */
    public @NotNull OffsetDateTime getActualFireTime() {
        return actualFireTime;
    }
}
