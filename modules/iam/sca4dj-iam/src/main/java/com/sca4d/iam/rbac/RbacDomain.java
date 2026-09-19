package com.sca4d.iam.rbac;

import com.sca4d.core.api.enums.BaseEnum;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 保存用于选择一套 RBAC Repository 和权限目录的大小写不敏感编码。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class RbacDomain {

    private final @NotNull String value;

    /**
     * 创建 RBAC 领域编码。
     *
     * @param value 只包含字母、数字、下划线或连字符的领域编码；输入大小写不敏感
     */
    public RbacDomain(@NotNull String value) {
        String checked = BaseEnum.normalize(value);
        if (!checked.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("RBAC 领域编码只能包含字母、数字、下划线或连字符");
        }
        this.value = checked;
    }

    /**
     * 返回转换为大写的领域编码。
     *
     * @return RBAC 领域编码
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof RbacDomain domain && value.equals(domain.value);
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
