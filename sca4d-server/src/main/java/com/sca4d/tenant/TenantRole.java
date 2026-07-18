package com.sca4d.tenant;

import org.jetbrains.annotations.NotNull;

/**
 * 租户内可以分配给成员的有效角色。
 *
 * <p>{@code code} 来自当前租户 IAM 角色定义，并由租户角色管理接口维护，不由成员模块自行生成。
 */
public class TenantRole {

    private final Long roleId;
    private final String code;
    private final String name;

    /**
     * 创建租户角色读取对象。
     *
     * @param roleId 角色主键
     * @param code 租户内固定角色编码
     * @param name 管理端展示名称
     */
    public TenantRole(@NotNull Long roleId, @NotNull String code, @NotNull String name) {
        this.roleId = roleId;
        this.code = code;
        this.name = name;
    }

    /**
     * 返回角色关系使用的持久化主键。
     *
     * @return 租户角色主键
     */
    public @NotNull Long getRoleId() {
        return roleId;
    }

    /**
     * 返回租户范围内固定的角色编码。
     *
     * @return 角色编码
     */
    public @NotNull String getCode() {
        return code;
    }

    /**
     * 返回成员管理界面使用的角色名称。
     *
     * @return 角色展示名称
     */
    public @NotNull String getName() {
        return name;
    }
}
