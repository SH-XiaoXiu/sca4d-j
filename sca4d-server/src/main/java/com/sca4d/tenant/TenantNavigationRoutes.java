package com.sca4d.tenant;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.system.MenuIconName;
import com.sca4d.system.NavigationRoute;
import com.sca4d.system.NavigationRouteContributor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 租户管理模块可进入共用菜单的代码路由。 */
@Component
public class TenantNavigationRoutes implements NavigationRouteContributor {

    /**
     * 返回以当前 Principal 租户 ID 解析的成员、角色和字典入口。
     *
     * @return 只允许 TENANT Principal 使用的固定路径模板
     */
    @Override
    public @NotNull List<NavigationRoute> navigationRoutes() {
        return List.of(
                new NavigationRoute(
                        "tenant.members",
                        "/tenant/tenants/{tenantId}/members",
                        Set.of(PrincipalType.TENANT),
                        TenantPermissions.TENANT_MEMBERS_READ,
                        MenuIconName.of("Users")),
                new NavigationRoute(
                        "tenant.roles",
                        "/tenant/tenants/{tenantId}/roles",
                        Set.of(PrincipalType.TENANT),
                        TenantPermissions.TENANT_ROLES_READ,
                        MenuIconName.of("ShieldCheck")),
                new NavigationRoute(
                        "tenant.dictionaries",
                        "/tenant/tenants/{tenantId}/dictionaries",
                        Set.of(PrincipalType.TENANT),
                        TenantPermissions.TENANT_DICTIONARIES_READ,
                        MenuIconName.of("BookOpenText")),
                new NavigationRoute(
                        "tenant.identity-providers",
                        "/tenant/tenants/{tenantId}/identity-providers",
                        Set.of(PrincipalType.TENANT),
                        TenantPermissions.TENANT_IDENTITY_PROVIDERS_READ,
                        MenuIconName.of("Network")));
    }
}
