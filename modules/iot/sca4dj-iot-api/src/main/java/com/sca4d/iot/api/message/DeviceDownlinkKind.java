package com.sca4d.iot.api.message;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 区分标准下行消息代表持续 desired 还是一次性 command。 */
public enum DeviceDownlinkKind implements BaseEnum {
    /** 同步一个属性的持续期望值。 */
    DESIRED("期望属性"),
    /** 执行一次物模型命令。 */
    COMMAND("一次性命令");

    private final @NotNull String description;

    DeviceDownlinkKind(@NotNull String description) {
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
