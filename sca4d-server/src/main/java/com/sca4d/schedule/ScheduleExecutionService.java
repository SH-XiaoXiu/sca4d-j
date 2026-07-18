package com.sca4d.schedule;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

/** 领取单次 Schedule 执行记录，并在业务事务内运行已注册的 ScheduleTask。 */
public interface ScheduleExecutionService {

    /**
     * 执行一次计划、人工或故障恢复触发。
     *
     * @param scheduleId JobDataMap 中唯一允许长期保存的 Schedule 主键
     * @param manualExecutionId 人工执行已创建的 executionId；计划触发时为空
     * @param expectedFingerprint 计划 Trigger 描述中保存的数据库定义指纹；人工执行不依赖计划时为空
     * @param triggerSource 当前触发来源
     * @param scheduledFireTime Quartz 原计划时间；人工执行时不参与 fireKey
     * @param actualFireTime 当前节点实际开始时间
     * @param executedInstanceId 当前 Quartz 节点实例标识
     */
    void run(
            @NotNull Long scheduleId,
            @Nullable Long manualExecutionId,
            @Nullable String expectedFingerprint,
            @NotNull TriggerSource triggerSource,
            @Nullable OffsetDateTime scheduledFireTime,
            @NotNull OffsetDateTime actualFireTime,
            @NotNull String executedInstanceId);
}
