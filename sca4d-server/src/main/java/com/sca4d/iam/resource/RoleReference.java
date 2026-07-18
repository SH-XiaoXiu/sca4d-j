package com.sca4d.iam.resource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 资源引用解析所需的 Role 安全事实。 */
public class RoleReference {

    private final Long roleId;
    private final Long tenantId;
    private final String code;
    private final String name;
    private final boolean deleted;

    public RoleReference(
            @NotNull Long roleId,
            @Nullable Long tenantId,
            @Nullable String code,
            @Nullable String name,
            boolean deleted) {
        this.roleId = roleId;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.deleted = deleted;
    }

    public @NotNull Long getRoleId() {
        return roleId;
    }

    public @Nullable Long getTenantId() {
        return tenantId;
    }

    public @Nullable String getCode() {
        return code;
    }

    public @Nullable String getName() {
        return name;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
