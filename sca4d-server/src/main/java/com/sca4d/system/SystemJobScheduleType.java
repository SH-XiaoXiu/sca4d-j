package com.sca4d.system;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 系统 Job 当前支持的只读计划类型。 */
public enum SystemJobScheduleType implements BaseEnum {
    /** 按固定时间间隔重复执行。 */
    INTERVAL,
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
