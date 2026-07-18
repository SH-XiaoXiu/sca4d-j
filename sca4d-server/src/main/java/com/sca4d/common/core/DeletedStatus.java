package com.sca4d.common.core;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * 逻辑删除状态。
 */
public enum DeletedStatus implements BaseEnum {
    /** 未删除。 */
    NORMAL("NORMAL", "正常"),
    /** 已删除。 */
    DELETED("DELETED", "已删除");

    private final String value;
    private final String description;

    DeletedStatus(@NotNull String value, @NotNull String description) {
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
