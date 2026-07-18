package com.sca4d.platform;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.system.MenuIconName;
import com.sca4d.system.NavigationRoute;
import com.sca4d.system.NavigationRouteContributor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 平台管理模块可进入共用菜单的代码路由。 */
@Component
public class PlatformNavigationRoutes implements NavigationRouteContributor {

    /**
     * 返回平台 Account、操作员、角色、租户访问控制、业务计划和租户正式菜单入口。
     *
     * @return 只允许 PLATFORM Principal 使用的固定路由
     */
    @Override
    public @NotNull List<NavigationRoute> navigationRoutes() {
        return List.of(
                new NavigationRoute(
                        "platform.accounts",
                        "/platform/accounts",
                        Set.of(PrincipalType.PLATFORM),
                        PlatformPermissions.ACCOUNTS_READ,
                        MenuIconName.of("KeyRound")),
                new NavigationRoute(
                        "platform.operators",
                        "/platform/operators",
                        Set.of(PrincipalType.PLATFORM),
                        PlatformPermissions.OPERATORS_READ,
                        MenuIconName.of("Users")),
                new NavigationRoute(
                        "platform.roles",
                        "/platform/roles",
                        Set.of(PrincipalType.PLATFORM),
                        PlatformPermissions.ROLES_READ,
                        MenuIconName.of("ShieldCheck")),
                new NavigationRoute(
                        "platform.tenant-access",
                        "/platform/tenant-access",
                        Set.of(PrincipalType.PLATFORM),
                        PlatformPermissions.TENANT_ACCESS_READ,
                        MenuIconName.of("SlidersHorizontal")),
                new NavigationRoute(
                        "platform.schedules",
                        "/platform/schedules",
                        Set.of(PrincipalType.PLATFORM),
                        PlatformPermissions.SCHEDULES_READ,
                        MenuIconName.of("CalendarClock")),
                new NavigationRoute(
                        "platform.tenants",
                        "/platform/tenants",
                        Set.of(PrincipalType.PLATFORM),
                        PlatformPermissions.TENANTS_READ,
                        MenuIconName.of("Building2")));
    }
}
