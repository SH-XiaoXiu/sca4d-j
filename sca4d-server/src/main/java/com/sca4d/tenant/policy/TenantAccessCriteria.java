package com.sca4d.tenant.policy;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.AccessCriteria;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** 当前请求可以作用的租户及其租户归属资源范围。 */
public class TenantAccessCriteria implements AccessCriteria {

    private final boolean allData;
    private final AccessAction action;
    private final Set<Long> tenantIds;
    private final Set<Long> excludedTenantIds;
    private final Long targetTenantId;

    /**
     * 创建租户归属资源的访问范围。
     *
     * @param allData 是否允许访问全部租户
     * @param action 当前接口要求的访问等级
     * @param tenantIds 限定模式下可访问的租户主键
     * @param excludedTenantIds 全部模式下被具体低等级规则排除的租户主键
     * @param targetTenantId 单资源接口绑定的租户主键；集合接口为 null
     */
    public TenantAccessCriteria(
            boolean allData,
            @NotNull AccessAction action,
            @NotNull Set<Long> tenantIds,
            @NotNull Set<Long> excludedTenantIds,
            @Nullable Long targetTenantId) {
        this.allData = allData;
        this.action = action;
        this.tenantIds = Set.copyOf(tenantIds);
        this.excludedTenantIds = Set.copyOf(excludedTenantIds);
        this.targetTenantId = targetTenantId;
    }

    /** @return 是否允许访问全部租户 */
    public boolean isAllData() {
        return allData;
    }

    /** @return 当前接口要求的访问等级 */
    public @NotNull AccessAction getAction() {
        return action;
    }

    /** @return 不可变的限定租户主键集合 */
    public @NotNull Set<Long> getTenantIds() {
        return tenantIds;
    }

    /** @return 全部模式下被当前访问等级排除的租户主键集合 */
    public @NotNull Set<Long> getExcludedTenantIds() {
        return excludedTenantIds;
    }

    /** @return 单资源接口绑定的租户主键；集合接口为 null */
    public @Nullable Long getTargetTenantId() {
        return targetTenantId;
    }
}
