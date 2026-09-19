package com.sca4d.notification.template;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一个通知模板渲染器生成的标题和正文。 */
public final class RenderedTemplate {

    private final @NotNull String title;

    private final @NotNull String body;

    public RenderedTemplate(@NotNull String title, @NotNull String body) {
        this.title = Objects.requireNonNull(title, "通知标题不能为空");
        this.body = Objects.requireNonNull(body, "通知正文不能为空");
    }

    @Contract(pure = true)
    public @NotNull String title() {
        return title;
    }

    @Contract(pure = true)
    public @NotNull String body() {
        return body;
    }
}
