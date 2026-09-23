package com.sca4d.iam.rbac;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * 保存一个 Repository 读取的角色标识、授权状态和生命周期时间。
 */
public final class Role implements Resource {

    /**
     * Role 使用的稳定 Resource 类型。
     */
    public static final @NotNull TreeCode RESOURCE_TYPE = TreeCode.ofSlash("IAM/ROLE");

    /**
     * 供 Java 注解参数使用的资源类型文本。
     */
    public static final @NotNull String RESOURCE_TYPE_VALUE = "IAM/ROLE";

    private final @NotNull Long id;

    private final @NotNull RbacScope scope;

    private final @NotNull String code;

    private final @NotNull RoleStatus status;

    private final boolean builtIn;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    private final @NotNull Version version;

    /**
     * 创建从持久化层读取的角色。
     *
     * @param id        角色标识
     * @param scope     角色所属的 RBAC 范围
     * @param code      当前 RBAC 范围中不可变的角色编码
     * @param status    角色状态
     * @param builtIn   是否为实现声明的内置角色
     * @param createdAt 创建时间
     * @param updatedAt 最近更新时间
     * @param version   名称、状态、Operation 和删除共享的当前版本
     */
    public Role(
            @NotNull Long id,
            @NotNull RbacScope scope,
            @NotNull String code,
            @NotNull RoleStatus status,
            boolean builtIn,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull Version version) {
        this.id = Objects.requireNonNull(id, "角色标识不能为空");
        this.scope = Objects.requireNonNull(scope, "角色范围不能为空");
        this.code = Objects.requireNonNull(code, "角色编码不能为空");
        this.status = Objects.requireNonNull(status, "角色状态不能为空");
        this.builtIn = builtIn;
        this.createdAt = Objects.requireNonNull(createdAt, "角色创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "角色更新时间不能为空");
        this.version = Objects.requireNonNull(version, "角色版本不能为空");
    }

    /**
     * 返回角色标识。
     *
     * @return 角色标识
     */
    @Contract(pure = true)
    public @NotNull Long roleId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Contract(pure = true)
    public @NotNull TreeCode type() {
        return RESOURCE_TYPE;
    }

    /**
     * 返回能够区分不同 `RbacScope` 中同号角色的资源标识。
     *
     * @return `RbacScope:roleId` 格式的稳定资源标识
     */
    @Override
    @Contract(pure = true)
    public @NotNull String id() {
        return scope + ":" + id;
    }

    /**
     * 返回角色所属的 RBAC 范围。
     *
     * @return RBAC 范围
     */
    @Contract(pure = true)
    public @NotNull RbacScope scope() {
        return scope;
    }

    /**
     * 返回当前 Repository 中创建后不可修改的角色编码。
     *
     * @return 角色编码
     */
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    /**
     * 返回角色是否向 Principal 提供 Operation。
     *
     * @return 角色状态
     */
    @Contract(pure = true)
    public @NotNull RoleStatus status() {
        return status;
    }

    /**
     * 返回角色是否由 Repository 实现创建并保护。
     *
     * @return {@code true} 表示内置角色
     */
    @Contract(pure = true)
    public boolean builtIn() {
        return builtIn;
    }

    /**
     * 返回角色创建时间。
     *
     * @return 创建时间
     */
    @Contract(pure = true)
    public @NotNull Instant createdAt() {
        return createdAt;
    }

    /**
     * 返回角色最近更新时间。
     *
     * @return 最近更新时间
     */
    @Contract(pure = true)
    public @NotNull Instant updatedAt() {
        return updatedAt;
    }

    /**
     * 返回名称、状态、Operation 和删除共享的当前版本。
     *
     * @return 当前版本
     */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }

}
