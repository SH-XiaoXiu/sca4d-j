package com.sca4d.schedule;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** ScheduleExecution 持久化状态。 */
public enum ExecutionStatus implements BaseEnum {
    /** 执行记录已创建，尚未开始运行。 */
    PENDING,
    /** ScheduleTask 正在运行。 */
    RUNNING,
    /** ScheduleTask 已成功完成。 */
    SUCCESS,
    /** ScheduleTask 抛出异常或返回失败。 */
    FAILED,
    /** 当前节点因状态或指纹不匹配而跳过执行。 */
    SKIPPED;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }
}
