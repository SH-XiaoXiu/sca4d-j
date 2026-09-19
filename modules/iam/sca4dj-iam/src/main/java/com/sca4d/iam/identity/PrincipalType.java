package com.sca4d.iam.identity;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 表示 Principal 具体 Java 类型对应的固定持久化判别值。 */
@SuppressWarnings("ClassCanBeRecord")
public final class PrincipalType {
    private final @NotNull String value;

    /**
     * 创建不可变的 Principal 类型判别值。
     *
     * @param value Session Principal Codec 使用且跨版本保持不变的 type
     */
    public PrincipalType(@NotNull String value) {
        String checkedValue = Objects.requireNonNull(value, "Principal 类型值不能为空");
        if (checkedValue.isBlank() || checkedValue.length() > 128) {
            throw new IllegalArgumentException("Principal 类型值必须为 1 到 128 个字符");
        }
        this.value = checkedValue;
    }

    /**
     * 读取数据库和 Session wire 识别 Principal 类型时使用的值。
     *
     * @return 长度为 1 到 128 个字符的持久化判别值
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof PrincipalType type && value.equals(type.value);
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
