package com.sca4d.iam.rbac;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * RBAC 表保存的具体 Operation 键或内部全量选择符。
 */
@SuppressWarnings("ClassCanBeRecord")
public final class PermissionSelector {
    /**
     * 表示 Operation Catalog 当前全部 ACTION 的内部选择符。
     */
    public static final @NotNull PermissionSelector ALL = new PermissionSelector("*");

    private final @Nullable TreeCode code;

    /**
     * 创建 RBAC 权限选择符。
     *
     * @param value 具体 ACTION 编码或内部 {@code "*"} 值
     */
    public PermissionSelector(@NotNull String value) {
        String checked = Objects.requireNonNull(value, "权限选择符不能为空");
        if ("*".equals(checked)) {
            this.code = null;
            return;
        }
        if (checked.isBlank() || checked.length() > 256) {
            throw new IllegalArgumentException("权限选择符必须为 1 到 256 个字符");
        }
        this.code = TreeCode.ofColon(checked);
    }

    /**
     * 创建使用已经规范化 Operation TreeCode 的选择符。
     */
    public PermissionSelector(@NotNull TreeCode code) {
        this.code = Objects.requireNonNull(code, "权限选择符不能为空");
    }

    /**
     * 返回持久化使用的选择符值。
     *
     * @return 选择符值
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return isAll() ? "*" : code.format(COLON);
    }

    /**
     * @return 选择符是否表示当前 Catalog 的全部 ACTION
     */
    @Contract(pure = true)
    public boolean isAll() {
        return code == null;
    }

    /**
     * @return 具体选择符的 Operation TreeCode；全量选择符没有具体编码
     */
    @Contract(pure = true)
    public @NotNull TreeCode code() {
        if (code == null) {
            throw new IllegalStateException("全量权限选择符没有具体 Operation 编码");
        }
        return code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof PermissionSelector selector && Objects.equals(code, selector.code);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hashCode(code);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return value();
    }
}
