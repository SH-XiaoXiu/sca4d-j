package com.sca4d.core.api.render;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

/** 校验一种模板源，并按 Locale 和变量生成具体结果。 */
public interface Renderer<S, R> {

    /** @return 模板源中引用的变量和其他结构信息 */
    @CheckReturnValue
    @NotNull
    TemplateInspection inspect(@NotNull S source);

    /** @return 使用已校验变量渲染出的结果 */
    @CheckReturnValue
    @NotNull
    R render(
            @NotNull S source,
            @NotNull Locale locale,
            @NotNull Map<@NotNull String, @NotNull String> variables);
}
