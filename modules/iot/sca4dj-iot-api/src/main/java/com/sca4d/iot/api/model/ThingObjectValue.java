package com.sca4d.iot.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 保存已经按 object 物模型定义校验的不可修改字段值。 */
public class ThingObjectValue {

    private final @NotNull Map<String, Object> fields;

    /** @param fields code 到已校验 Java 值的映射 */
    public ThingObjectValue(@NotNull Map<String, Object> fields) {
        this.fields = Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        Objects.requireNonNull(fields, "object 字段不能为空")));
    }

    /** @return 不可修改字段值 */
    public @NotNull @Unmodifiable Map<String, Object> fields() {
        return fields;
    }
}
