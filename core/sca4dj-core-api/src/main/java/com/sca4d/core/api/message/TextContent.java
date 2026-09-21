package com.sca4d.core.api.message;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存已经准备好的文本内容。 */
public final class TextContent implements ContentBody {

    private final @NotNull String value;

    public TextContent(@NotNull String value) {
        this.value = Objects.requireNonNull(value, "文本内容不能为空");
    }

    public @NotNull String value() {
        return value;
    }
}
