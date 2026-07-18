package com.sca4d.iam.resource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 资源引用解析所需的 Tenant 安全事实。 */
public class TenantReference {

    private final Long tenantId;
    private final String name;
    private final boolean deleted;

    public TenantReference(@NotNull Long tenantId, @Nullable String name, boolean deleted) {
        this.tenantId = tenantId;
        this.name = name;
        this.deleted = deleted;
    }

    public @NotNull Long getTenantId() {
        return tenantId;
    }

    public @Nullable String getName() {
        return name;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
