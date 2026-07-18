package com.sca4d.system;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 只读查询系统 Quartz Job 定义、Trigger 状态和最近一次运行结果。 */
public interface SystemScheduledJobService {

    /** @return 固定包含三个系统 Job 且按代码定义顺序排列的只读集合 */
    @NotNull
    List<SystemScheduledJob> jobs();

    /** @return 指定固定 JobKey 的只读聚合 @throws BizException JobKey 不属于系统目录时抛出 */
    @NotNull
    SystemScheduledJob require(@NotNull String jobGroup, @NotNull String jobName);
}
