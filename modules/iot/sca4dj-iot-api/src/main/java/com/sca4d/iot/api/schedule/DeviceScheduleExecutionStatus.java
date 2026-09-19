package com.sca4d.iot.api.schedule;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示一次计划时间生成设备下行的结果。 */
public enum DeviceScheduleExecutionStatus implements BaseEnum {
    /** 已生成一条可靠设备下行消息。 */
    ENQUEUED("已生成下行"),
    /** 到期时已经超过规则允许的最晚发送时间。 */
    EXPIRED("已过期"),
    /** 规则、Device 或物模型已经不允许生成下行。 */
    FAILED("生成失败");

    private final @NotNull String description;

    DeviceScheduleExecutionStatus(@NotNull String description) {
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
