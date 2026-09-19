package com.sca4d.iot.api.schedule;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示设备定时规则是否继续产生新的设备下行。 */
public enum DeviceScheduleStatus implements BaseEnum {
    /** 按下一次触发时间继续运行。 */
    ACTIVE("运行中"),
    /** 保留规则但不产生下行。 */
    PAUSED("已暂停"),
    /** 已取消且不能再次启用。 */
    CANCELLED("已取消"),
    /** 单次规则已经产生执行结果。 */
    COMPLETED("已完成");

    private final @NotNull String description;

    DeviceScheduleStatus(@NotNull String description) {
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
