package com.sca4d.core.api.message;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存需要由具体组件按 scheme 解析的外部内容引用。 */
public final class ReferencedContent implements ContentBody {

    private final @NotNull String scheme;

    private final @NotNull String key;

    /** 创建外部内容引用。 */
    public ReferencedContent(@NotNull String scheme, @NotNull String key) {
        this.scheme = required(scheme, "内容引用 scheme");
        this.key = required(key, "内容引用标识");
    }

    /** @return 解析引用所使用的 scheme */
    public @NotNull String scheme() {
        return scheme;
    }

    /** @return scheme 内的外部内容标识 */
    public @NotNull String key() {
        return key;
    }

    private static @NotNull String required(@NotNull String value, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return checked;
    }
}
