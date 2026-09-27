package com.sca4d.iam.rbac;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.OptionalLong;

/** 定位一个 RBAC 领域中的全局角色空间或一个带数字标识的业务角色空间。 */
public final class RbacScope {

    private final @NotNull RbacDomain domain;

    private final @Nullable Long id;

    private RbacScope(@NotNull RbacDomain domain, @Nullable Long id) {
        this.domain = Objects.requireNonNull(domain, "RBAC 领域不能为空");
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("RBAC 范围标识必须为正数");
        }
        this.id = id;
    }

    /**
     * 创建一个领域唯一的全局角色空间。
     *
     * @param domain RBAC 领域
     * @return 全局 RBAC 范围
     */
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull RbacScope global(@NotNull RbacDomain domain) {
        return new RbacScope(domain, null);
    }

    /**
     * 创建一个由业务数字标识定位的角色空间。
     *
     * @param domain RBAC 领域
     * @param id 例如 Project 标识的正数范围标识
     * @return 具体 RBAC 范围
     */
    @Contract(value = "_, _ -> new", pure = true)
    public static @NotNull RbacScope of(@NotNull RbacDomain domain, long id) {
        return new RbacScope(domain, id);
    }

    /** @return 选择 Repository 和权限目录的 RBAC 领域 */
    @Contract(pure = true)
    public @NotNull RbacDomain domain() {
        return domain;
    }

    /** @return 具体业务范围标识；全局范围返回空 */
    @Contract(pure = true)
    public @NotNull OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    /** @return 当前范围是否为领域唯一的全局角色空间 */
    @Contract(pure = true)
    public boolean global() {
        return id == null;
    }

    /**
     * 返回具体业务范围标识。
     *
     * @return 正数范围标识
     * @throws IllegalStateException 当前范围为全局范围时抛出
     */
    @Contract(pure = true)
    public long businessId() {
        if (id == null) {
            throw new IllegalStateException("全局 RBAC 范围没有业务标识");
        }
        return id;
    }

    /** {@inheritDoc} */
    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof RbacScope scope
                        && domain.equals(scope.domain)
                        && Objects.equals(id, scope.id);
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hash(domain, id);
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public @NotNull String toString() {
        return id == null ? domain.toString() + ":GLOBAL" : domain.toString() + ':' + id;
    }
}
