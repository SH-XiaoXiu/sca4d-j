package com.sca4d.iam.rbac;

import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 保存 Principal 当前角色以及不能由普通替换移除的角色关系。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class PrincipalRoles {

    private final @NotNull @Unmodifiable List<@NotNull Role> roles;

    private final @NotNull Long principalId;

    private final @NotNull RbacScope scope;

    private final @NotNull @Unmodifiable Set<@NotNull Long> protectedRoleIds;

    private final @NotNull Version version;

    /**
     * 创建 Principal 角色结果。
     *
     * @param scope 角色关系所属的 RBAC 范围
     * @param principalId Principal 标识
     * @param roles 当前角色
     * @param protectedRoleIds 不能由普通替换移除的角色标识
     * @param version 当前角色集合版本
     */
    public PrincipalRoles(
            @NotNull RbacScope scope,
            @NotNull Long principalId,
            @NotNull List<@NotNull Role> roles,
            @NotNull Set<@NotNull Long> protectedRoleIds,
            @NotNull Version version) {
        this.scope = Objects.requireNonNull(scope, "RBAC 范围不能为空");
        this.principalId = Objects.requireNonNull(principalId, "Principal 标识不能为空");
        this.roles = List.copyOf(roles);
        this.protectedRoleIds = Set.copyOf(protectedRoleIds);
        this.version = Objects.requireNonNull(version, "角色集合版本不能为空");
    }

    /** @return 当前角色关系所属的 RBAC 范围 */
    @Contract(pure = true)
    public @NotNull RbacScope scope() {
        return scope;
    }

    /**
     * Principal 标识。
     *
     * @return Principal 标识
     */
    @Contract(pure = true)
    public @NotNull Long principalId() {
        return principalId;
    }

    /**
     * Principal 当前关联的角色。
     *
     * @return 不可修改的角色列表
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable List<@NotNull Role> roles() {
        return roles;
    }

    /**
     * 普通角色替换必须保留的角色关系。
     *
     * @return 不可修改的角色标识集合
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable Set<@NotNull Long> protectedRoleIds() {
        return protectedRoleIds;
    }

    /** @return Principal 角色集合的当前版本 */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }

}
