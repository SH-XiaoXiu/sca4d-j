package com.sca4d.iam;

import com.sca4d.core.api.enums.BaseEnum;
import com.sca4d.iam.rbac.RbacRepository;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 保存用于选择一种业务 {@link RbacRepository} 的大小写不敏感编码。 */
@SuppressWarnings("ClassCanBeRecord")
public final class DomainScope {

    private final @NotNull String value;

    /**
     * 创建 Repository 编码。
     *
     * @param value Repository 编码；输入大小写不敏感
     */
    public DomainScope(@NotNull String value) {
        this.value = BaseEnum.normalize(value);
    }

    /**
     * 返回转换为大写的 Repository 编码。
     *
     * @return Repository 编码
     */
    @Contract(pure = true)
    public @NotNull String value() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof DomainScope scope && value.equals(scope.value);
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
