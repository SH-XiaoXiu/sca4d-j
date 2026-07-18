package com.sca4d.tenant.policy;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** 针对一个访问等级计算完成的租户集合范围。 */
public class TenantActionScope {

    private final boolean allData;
    private final Set<Long> tenantIds;
    private final Set<Long> excludedTenantIds;

    /**
     * @param allData 默认规则是否覆盖全部租户
     * @param tenantIds 非全部模式下显式允许的租户
     * @param excludedTenantIds 全部模式下被具体低等级规则排除的租户
     */
    public TenantActionScope(
            boolean allData,
            @NotNull Set<Long> tenantIds,
            @NotNull Set<Long> excludedTenantIds) {
        if (allData && !tenantIds.isEmpty()) {
            throw new IllegalArgumentException("全部租户访问范围不能同时携带包含集合");
        }
        if (!allData && !excludedTenantIds.isEmpty()) {
            throw new IllegalArgumentException("限定租户访问范围不能同时携带排除集合");
        }
        this.allData = allData;
        this.tenantIds = Set.copyOf(tenantIds);
        this.excludedTenantIds = Set.copyOf(excludedTenantIds);
    }

    /** @return 是否默认覆盖全部租户 */
    public boolean isAllData() {
        return allData;
    }

    /** @return 非全部模式下显式允许的租户集合 */
    public @NotNull Set<Long> getTenantIds() {
        return tenantIds;
    }

    /** @return 全部模式下被具体低等级覆盖规则排除的租户集合 */
    public @NotNull Set<Long> getExcludedTenantIds() {
        return excludedTenantIds;
    }
}
