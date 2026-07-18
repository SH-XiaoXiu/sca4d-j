package com.sca4d.tenant.policy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 一次租户资源判断使用的目标租户和 PLATFORM 授权范围。
 */
public class TenantResource {

    private final Long tenantId;
    private final TenantActionScope scope;

    /**
     * @param tenantId 单资源接口的目标租户；集合接口为 {@code null}
     * @param scope 当前 Principal 在指定访问等级下的租户集合
     */
    public TenantResource(
            @Nullable Long tenantId, @NotNull TenantActionScope scope) {
        this.tenantId = tenantId;
        this.scope = scope;
    }

    /** @return 单资源接口的目标租户；集合接口为 {@code null} */
    public @Nullable Long getTenantId() {
        return tenantId;
    }

    /** @return 当前请求是否访问经过 Criteria 过滤的租户集合 */
    public boolean isCollection() {
        return tenantId == null;
    }

    /**
     * 判断 PLATFORM ACL 是否允许目标租户。
     *
     * <p>集合接口始终允许进入 Repository，由 Criteria 返回空集、包含集或排除集；单资源接口必须命中
     * 当前动作对应的包含规则，或未被全部模式的排除规则覆盖。
     *
     * @return ACL 允许集合查询或当前目标租户时返回 true
     */
    public boolean isGranted() {
        if (tenantId == null) {
            return true;
        }
        if (scope.isAllData()) {
            return !scope.getExcludedTenantIds().contains(tenantId);
        }
        return scope.getTenantIds().contains(tenantId);
    }

    /** @return 生成 Repository Criteria 时使用的租户集合 */
    public @NotNull TenantActionScope getScope() {
        return scope;
    }
}
