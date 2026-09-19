package com.sca4d.core.api.connection;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 表示 App 连接使用的稳定协议编码。 */
public final class ConnectionProtocol {

    private final @NotNull String code;

    /** 创建一个不包含空白的协议编码。 */
    public ConnectionProtocol(@NotNull String code) {
        String checked = TextUtils.requireText(Objects.requireNonNull(code, "Connection 协议不能为空"), "Connection 协议");
        if (checked.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Connection 协议不能包含空白");
        }
        this.code = checked;
    }

    /**
     * 返回协议编码。
     *
     * @return 不包含空白的稳定协议编码
     */
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ConnectionProtocol protocol && code.equals(protocol.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return code;
    }
}
