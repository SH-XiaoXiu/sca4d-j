package com.sca4d.tenant.view;

import com.sca4d.common.access.AccessAction;
import com.sca4d.tenant.policy.PlatformTenantAccess;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 平台操作员 PLATFORM Principal 的完整租户 ACL 响应。 */
@Data
@Accessors(chain = true)
public class PlatformTenantAccessView {

    private String principalId;
    private Long version;
    private AccessAction defaultAction;
    private List<TenantAccessRuleView> grants;

    /** @return 包含默认访问等级和全部租户规则的管理端视图 */
    public static @NotNull PlatformTenantAccessView from(@NotNull PlatformTenantAccess access) {
        return new PlatformTenantAccessView()
                .setPrincipalId(access.getPrincipalId().toString())
                .setVersion(access.getVersion())
                .setDefaultAction(access.getDefaultAction())
                .setGrants(access.getRules().stream().map(TenantAccessRuleView::from).toList());
    }
}
