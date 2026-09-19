package com.sca4d.core.api.query;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一个安全排序字段及其排序方向。 */
public final class SortOrder {

    private final @NotNull Sort field;

    private final @NotNull SortDir direction;

    private SortOrder(@NotNull Sort field, @NotNull SortDir direction) {
        this.field = Objects.requireNonNull(field, "排序字段不能为空");
        this.direction = Objects.requireNonNull(direction, "排序方向不能为空");
    }

    /** @return 使用指定字段和方向的新排序要求 */
    @CheckReturnValue
    @Contract(value = "_, _ -> new", pure = true)
    public static @NotNull SortOrder of(
            @NotNull Sort field,
            @NotNull SortDir direction) {
        return new SortOrder(field, direction);
    }

    /** @return 当前排序字段 */
    @Contract(pure = true)
    public @NotNull Sort getField() {
        return field;
    }

    /** @return 当前字段的排序方向 */
    @Contract(pure = true)
    public @NotNull SortDir getDirection() {
        return direction;
    }
}
