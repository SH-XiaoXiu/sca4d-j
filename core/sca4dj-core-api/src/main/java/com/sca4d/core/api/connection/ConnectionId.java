package com.sca4d.core.api.connection;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示 App 对外持续网络连接的不可变标识。 */
public final class ConnectionId {

    private final @NotNull String value;

    /** 创建一个不透明连接标识。 */
    public ConnectionId(@NotNull String value) {
        this.value = TextUtils.requireText(Objects.requireNonNull(value, "Connection 标识不能为空"), "Connection 标识");
    }

    /**
     * 返回不透明连接标识文本。
     *
     * @return 已去除首尾空白且不为空的连接标识
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ConnectionId id && value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return value;
    }
}
