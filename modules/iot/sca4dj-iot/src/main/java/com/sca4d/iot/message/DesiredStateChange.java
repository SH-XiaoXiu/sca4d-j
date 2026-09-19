package com.sca4d.iot.message;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 保存一次 desired HTTP 动作明确要求 set 和 clear 的属性集合。 */
public final class DesiredStateChange {

    private final @NotNull Map<String, Object> setValues;

    private final @NotNull Set<String> clearCodes;

    /** @param setValues 本次明确设置的属性值 @param clearCodes 本次明确停止维持的属性 code */
    public DesiredStateChange(@NotNull Map<String, ?> setValues, @NotNull Set<String> clearCodes) {
        this.setValues = Map.copyOf(Objects.requireNonNull(setValues, "desired set 不能为空"));
        this.clearCodes = Set.copyOf(Objects.requireNonNull(clearCodes, "desired clear 不能为空"));
        if (this.setValues.isEmpty() && this.clearCodes.isEmpty()) {
            throw new IllegalArgumentException("desired change 必须包含 set 或 clear");
        }
        if (this.setValues.keySet().stream().anyMatch(this.clearCodes::contains)
                || this.setValues.keySet().stream().anyMatch(value -> value == null || value.isBlank())
                || this.setValues.values().stream().anyMatch(Objects::isNull)
                || this.clearCodes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("desired set 与 clear 不能重叠且不能包含空值");
        }
    }

    /** @return 不可修改 set 值 */
    public @NotNull @Unmodifiable Map<String, Object> setValues() {
        return setValues;
    }

    /** @return 不可修改 clear code */
    public @NotNull @Unmodifiable Set<String> clearCodes() {
        return clearCodes;
    }
}
