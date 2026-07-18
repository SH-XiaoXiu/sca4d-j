package com.sca4d.tenant.policy;

import com.sca4d.common.access.AccessAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** 一个 PLATFORM Principal 当前完整的租户业务模块 ACL。 */
public class TenantAccessScope {

    private final AccessAction defaultAction;
    private final Map<Long, AccessAction> tenantActions;

    /**
     * @param defaultAction 全部租户的默认最高等级；空值表示只允许显式租户
     * @param tenantActions 具体租户覆盖规则
     */
    public TenantAccessScope(
            @Nullable AccessAction defaultAction, @NotNull Map<Long, AccessAction> tenantActions) {
        this.defaultAction = defaultAction;
        this.tenantActions = Map.copyOf(tenantActions);
    }

    /** @return 全部租户的默认最高等级；空值表示默认不能访问任何租户 */
    public @Nullable AccessAction getDefaultAction() {
        return defaultAction;
    }

    /** @return 按租户主键索引的具体覆盖规则 */
    public @NotNull Map<Long, AccessAction> getTenantActions() {
        return tenantActions;
    }
}
