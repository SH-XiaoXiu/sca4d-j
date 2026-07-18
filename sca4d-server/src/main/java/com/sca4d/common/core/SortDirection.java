package com.sca4d.common.core;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/**
 * 排序方向。
 */
public enum SortDirection implements BaseEnum {
    /** 升序。 */
    ASC("ASC", "升序"),
    /** 降序。 */
    DESC("DESC", "降序");

    private final String value;
    private final String description;

    SortDirection(@NotNull String value, @NotNull String description) {
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
