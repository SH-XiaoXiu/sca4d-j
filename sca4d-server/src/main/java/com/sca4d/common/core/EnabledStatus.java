package com.sca4d.common.core;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * 启停状态。
 */
public enum EnabledStatus implements BaseEnum {
    /** 启用。 */
    ENABLED("ENABLED", "启用"),
    /** 停用。 */
    DISABLED("DISABLED", "停用");

    private final String value;
    private final String description;

    EnabledStatus(@NotNull String value, @NotNull String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
