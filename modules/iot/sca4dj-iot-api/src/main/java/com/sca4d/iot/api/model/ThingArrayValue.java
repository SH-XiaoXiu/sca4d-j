package com.sca4d.iot.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 保存已经按 array 物模型定义校验的不可修改元素值。 */
public class ThingArrayValue {

    private final @NotNull List<Object> elements;

    /** @param elements 已校验且不包含 null 的元素 */
    public ThingArrayValue(@NotNull List<Object> elements) {
        this.elements = List.copyOf(Objects.requireNonNull(elements, "数组元素不能为空"));
    }

    /** @return 不可修改元素值 */
    public @NotNull @Unmodifiable List<Object> elements() {
        return elements;
    }
}
