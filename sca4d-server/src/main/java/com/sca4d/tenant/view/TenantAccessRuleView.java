package com.sca4d.tenant.view;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.resource.view.ResourceReferenceView;
import com.sca4d.tenant.policy.TenantAccessRule;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/** 管理端展示的具体租户 ACL 规则。 */
@Data
@Accessors(chain = true)
public class TenantAccessRuleView {

    private ResourceReferenceView tenant;
    private AccessAction accessAction;

    /** @return 包含租户资源引用和访问等级的管理端规则视图 */
    public static @NotNull TenantAccessRuleView from(@NotNull TenantAccessRule rule) {
        return new TenantAccessRuleView()
                .setTenant(ResourceReferenceView.from(rule.getTenant()))
                .setAccessAction(rule.getAccessAction());
    }
}
