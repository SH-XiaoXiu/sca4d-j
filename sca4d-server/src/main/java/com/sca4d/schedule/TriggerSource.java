package com.sca4d.schedule;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 一次 ScheduleExecution 的触发来源。 */
public enum TriggerSource implements BaseEnum {
    /** Quartz Trigger 按计划时间触发。 */
    SCHEDULED,
    /** 管理员通过接口创建人工执行。 */
    MANUAL,
    /** 系统恢复尚未完成的人工执行记录。 */
    RECOVERY;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }
}
