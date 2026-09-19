package com.sca4d.app.server.iot;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 表示 Sca4d Server 是否允许一个 IoT Product 或 Device 参与运行。 */
public enum IotProfileStatus implements BaseEnum {
    /** 允许 Product 被 Device 启用，或允许 Device 接受认证和消息。 */
    ENABLED("启用"),
    /** 只允许管理和配置，不允许 Device 接受认证和消息。 */
    DISABLED("停用");

    private final @NotNull String description;

    IotProfileStatus(@NotNull String description) {
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
