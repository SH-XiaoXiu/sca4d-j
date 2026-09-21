package com.sca4d.core.api.render;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 在 Renderer 调用前执行真实发送和预览使用的模板变量策略。 */
public final class TemplateVariables {

    private TemplateVariables() {
    }

    /** 要求所有引用变量存在且不为空白。 */
    public static @NotNull @Unmodifiable Map<String, String> require(
            @NotNull Set<String> names,
            @NotNull Map<String, String> supplied) {
        return complete(names, supplied, true);
    }

    /** 为预览补充缺失变量，缺失值使用空字符串。 */
    public static @NotNull @Unmodifiable Map<String, String> fillMissing(
            @NotNull Set<String> names,
            @NotNull Map<String, String> supplied) {
        return complete(names, supplied, false);
    }

    private static @NotNull @Unmodifiable Map<String, String> complete(
            @NotNull Set<String> names,
            @NotNull Map<String, String> supplied,
            boolean requireValue) {
        Map<String, String> checked = new LinkedHashMap<>();
        Map<String, String> values = Objects.requireNonNull(supplied, "模板变量不能为空");
        for (String name : Objects.requireNonNull(names, "模板变量名称不能为空")) {
            String value = values.get(name);
            if (value == null) {
                if (requireValue) {
                    throw new IllegalArgumentException("模板变量缺失: " + name);
                }
                value = "";
            } else if (requireValue && value.isBlank()) {
                throw new IllegalArgumentException("模板变量不能为空白: " + name);
            }
            checked.put(name, value);
        }
        return Map.copyOf(checked);
    }
}
