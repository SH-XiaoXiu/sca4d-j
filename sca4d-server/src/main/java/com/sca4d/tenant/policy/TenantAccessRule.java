package com.sca4d.tenant.policy;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.resource.ResourceReference;
import org.jetbrains.annotations.NotNull;

/** 带业务名称和导航状态的具体租户 ACL 规则。 */
public class TenantAccessRule {

    private final ResourceReference tenant;
    private final AccessAction accessAction;

    public TenantAccessRule(
            @NotNull ResourceReference tenant, @NotNull AccessAction accessAction) {
        this.tenant = tenant;
        this.accessAction = accessAction;
    }

    /** @return 已按当前请求权限解析的租户资源引用 */
    public @NotNull ResourceReference getTenant() {
        return tenant;
    }

    /** @return 该租户的最高访问等级 */
    public @NotNull AccessAction getAccessAction() {
        return accessAction;
    }
}
