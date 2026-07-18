package com.sca4d.tenant.policy;

import com.sca4d.common.access.AccessAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 一个 PLATFORM Principal 对全部租户或具体租户的最高访问等级。 */
public class TenantAccessGrant {

    private final Long tenantId;
    private final AccessAction accessAction;

    /**
     * @param tenantId 具体租户主键；空值表示全部租户的默认等级
     * @param accessAction 该范围内允许的最高访问等级
     */
    public TenantAccessGrant(@Nullable Long tenantId, @NotNull AccessAction accessAction) {
        this.tenantId = tenantId;
        this.accessAction = accessAction;
    }

    /** @return 具体租户主键；空值表示全部租户 */
    public @Nullable Long getTenantId() {
        return tenantId;
    }

    /** @return 允许的最高访问等级 */
    public @NotNull AccessAction getAccessAction() {
        return accessAction;
    }
}
