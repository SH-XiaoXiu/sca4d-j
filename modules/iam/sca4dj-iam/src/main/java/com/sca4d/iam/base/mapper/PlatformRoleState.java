package com.sca4d.iam.base.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.iam.rbac.RoleStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 {@code platform_role} 的角色状态和权限字段。 */
@SuppressWarnings("ClassCanBeRecord")
public final class PlatformRoleState {

    private final @NotNull Long id;

    private final @NotNull String code;

    private final @NotNull LocalizedFields i18n;

    private final @NotNull RoleStatus status;

    private final boolean builtIn;

    private final @NotNull Instant createdAt;

    private final @NotNull Instant updatedAt;

    private final @NotNull Version version;

    /**
     * 创建 Platform 角色查询结果。
     *
     * @param id 角色标识
     * @param code 不可修改的角色编码
     * @param i18n 完整多语言字段
     * @param status 角色状态
     * @param builtIn 是否为内置角色
     * @param createdAt 创建时间
     * @param updatedAt 最近更新时间
     * @param version 名称、状态、权限和删除共享的版本
     */
    public PlatformRoleState(
            @NotNull Long id,
            @NotNull String code,
            @NotNull LocalizedFields i18n,
            @NotNull RoleStatus status,
            boolean builtIn,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull Version version) {
        this.id = Objects.requireNonNull(id, "角色标识不能为空");
        this.code = Objects.requireNonNull(code, "角色编码不能为空");
        this.i18n = Objects.requireNonNull(i18n, "角色多语言字段不能为空");
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
    public @NotNull Long id() {
        return id;
    }

    /**
     * 返回创建后不可修改的角色编码。
     *
     * @return 角色编码
     */
    @Contract(pure = true)
    public @NotNull String code() {
        return code;
    }

    /**
     * 返回角色完整多语言字段。
     *
     * @return 完整多语言字段
     */
    @Contract(pure = true)
    public @NotNull LocalizedFields i18n() {
        return i18n;
    }

    /**
     * 返回角色状态。
     *
     * @return 角色状态
     */
    @Contract(pure = true)
    public @NotNull RoleStatus status() {
        return status;
    }

    /**
     * 返回角色是否由 IAM 创建并保护。
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
     * 返回名称、状态、权限和删除共享的版本。
     *
     * @return 当前版本
     */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }
}
