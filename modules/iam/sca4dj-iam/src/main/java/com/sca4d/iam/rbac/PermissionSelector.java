package com.sca4d.iam.rbac;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** RBAC 表保存的具体 Operation 键或内部全量选择符。 */
@SuppressWarnings("ClassCanBeRecord")
public final class PermissionSelector {
    /** 表示 Operation Catalog 当前全部 ACTION 的内部选择符。 */
    public static final @NotNull PermissionSelector ALL = new PermissionSelector("*");

    private final @NotNull String value;

    /**
     * 创建 RBAC 权限选择符。
     *
     * @param value 具体 ACTION 编码或内部 {@code "*"} 值
     */
    public PermissionSelector(@NotNull String value) {
        String checked = Objects.requireNonNull(value, "权限选择符不能为空");
        if (checked.isBlank() || checked.length() > 256) {
            throw new IllegalArgumentException("权限选择符必须为 1 到 256 个字符");
        }
        this.value = checked;
    }

    /**
     * 返回持久化使用的选择符值。
     *
     * @return 选择符值
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof PermissionSelector selector && value.equals(selector.value);
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public int hashCode() {
        return value.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return value;
    }
}
