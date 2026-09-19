package com.sca4d.core.api.identity;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 表示账号可用于登录解析的开放标识类型。 */
@SuppressWarnings("ClassCanBeRecord")
public final class AccountIdentifierType {
    /** 用户名标识。 */
    public static final @NotNull AccountIdentifierType USERNAME = new AccountIdentifierType("USERNAME");

    private final @NotNull String value;

    /**
     * 创建一个开放的登录标识类型。
     *
     * @param value 持久化使用的稳定类型值
     */
    public AccountIdentifierType(@NotNull String value) {
        String checkedValue = Objects.requireNonNull(value, "登录标识类型值不能为空");
        if (checkedValue.isBlank() || checkedValue.length() > 128) {
            throw new IllegalArgumentException("登录标识类型值必须为 1 到 128 个字符");
        }
        this.value = checkedValue;
    }

    /**
     * 返回持久化使用的稳定类型值。
     *
     * @return 类型值
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof AccountIdentifierType type && value.equals(type.value);
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return value;
    }
}
