package com.sca4d.iam.base.mapper;

import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存批量查询得到的一条 PlatformPrincipal 当前角色关系。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class PlatformPrincipalRole {

    private final @NotNull Long principalId;

    private final @Nullable Long roleId;

    private final @NotNull Version rolesVersion;

    private final boolean protectedRelation;

    /**
     * 创建 PlatformPrincipal 当前角色查询结果。
     *
     * @param principalId PlatformPrincipal 标识
     * @param rolesVersion Principal 当前角色集合版本
     * @param roleId 角色标识；Principal 没有角色时为空
     * @param protectedRelation 普通角色替换是否必须保留这条关系
     */
    public PlatformPrincipalRole(
            @NotNull Long principalId,
            @NotNull Version rolesVersion,
            @Nullable Long roleId,
            boolean protectedRelation) {
        this.principalId = Objects.requireNonNull(principalId, "PlatformPrincipal 标识不能为空");
        this.rolesVersion = Objects.requireNonNull(rolesVersion, "角色集合版本不能为空");
        this.roleId = roleId;
        this.protectedRelation = protectedRelation;
    }

    /** @return PlatformPrincipal 标识 */
    @Contract(pure = true)
    public @NotNull Long principalId() {
        return principalId;
    }

    /** @return 当前角色集合版本 */
    @Contract(pure = true)
    public @NotNull Version rolesVersion() {
        return rolesVersion;
    }

    /** @return 角色标识；Principal 当前没有角色时为空 */
    @Contract(pure = true)
    public @Nullable Long roleId() {
        return roleId;
    }

    /** @return 普通角色替换是否必须保留这条关系 */
    @Contract(pure = true)
    public boolean protectedRelation() {
        return protectedRelation;
    }
}
