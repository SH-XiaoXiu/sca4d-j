package com.sca4d.tenant.policy;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.access.ResourceAccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** 校验当前请求是否可以在指定租户范围内管理成员。 */
@Component
@RequiredArgsConstructor
public class TenantMemberAccessPolicy {

    private final ResourceAccess resourceAccess;

    /**
     * 确认当前请求的租户范围和访问等级包含目标租户。
     *
     * <p>范围由 Controller 的 {@code @Access} 写入请求态。成员创建没有既存资源行，仍必须在写入工作流开始前
     * 通过本策略校验。
     *
     * @param tenantId 目标租户主键
     * @param requiredAction 当前业务操作要求的最低访问等级
     */
    public void require(@NotNull Long tenantId, @NotNull AccessAction requiredAction) {
        TenantAccessCriteria criteria = resourceAccess.require(TenantAccessCriteria.class);
        if (!criteria.getAction().permits(requiredAction)
                || (criteria.getTargetTenantId() != null && !criteria.getTargetTenantId().equals(tenantId))
                || (criteria.isAllData()
                        ? criteria.getExcludedTenantIds().contains(tenantId)
                        : !criteria.getTenantIds().contains(tenantId))) {
            throw new ResourceAccessDeniedException();
        }
    }
}
