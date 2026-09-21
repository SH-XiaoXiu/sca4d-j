package com.sca4d.core.api.render;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

/** 保存模板源完成语法检查后引用的变量名称。 */
public final class TemplateInspection {

    private final @NotNull Set<@NotNull String> variables;

    /** 创建不可修改的模板检查结果。 */
    public TemplateInspection(@NotNull Set<@NotNull String> variables) {
        this.variables = Set.copyOf(variables);
    }

    /** @return 模板源引用的不可修改变量集合 */
    public @NotNull @Unmodifiable Set<@NotNull String> variables() {
        return variables;
    }
}
