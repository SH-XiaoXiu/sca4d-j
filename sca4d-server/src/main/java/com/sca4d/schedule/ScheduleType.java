package com.sca4d.schedule;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** Schedule 的一次执行和重复 Cron 两种固定计划类型。 */
public enum ScheduleType implements BaseEnum {
    /** 只在指定时间执行一次。 */
    ONCE,
    /** 按 Cron 表达式重复执行。 */
    CRON;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }
}
