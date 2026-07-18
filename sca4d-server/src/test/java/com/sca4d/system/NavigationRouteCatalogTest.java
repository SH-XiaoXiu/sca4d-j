package com.sca4d.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.LocalizedMessagesFixture;
import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.common.permission.PermissionNode;
import com.sca4d.system.view.NavigationRouteView;
import com.sca4d.tenant.TenantNavigationRoutes;
import com.sca4d.tenant.TenantPermissions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** 验证菜单导航路由都来自已注册的管理端页面目录。 */
class NavigationRouteCatalogTest {

    @Test
    void shouldRegisterIdentityProviderManagementRoutes() {
        LocalizedMessages messages = LocalizedMessagesFixture.create();
        assertThat(new SystemNavigationRoutes().navigationRoutes())
                .filteredOn(route -> route.getCode().equals("system.identity-providers"))
                .singleElement()
                .satisfies(route -> {
                    assertThat(route.getPathTemplate()).isEqualTo("/system/identity-providers");
                    assertThat(route.getPermissionCode()).isEqualTo(SystemPermissions.IDENTITY_PROVIDERS_READ);
                    assertThat(route.getDefaultTitleKey()).isEqualTo("navigation.system.identity-providers");
                    assertThat(NavigationRouteView.from(route, messages).getDefaultTitles())
                            .containsEntry("zh-CN", "身份源")
                            .containsEntry("en-US", "Identity providers");
                });
        assertThat(new TenantNavigationRoutes().navigationRoutes())
                .filteredOn(route -> route.getCode().equals("tenant.identity-providers"))
                .singleElement()
                .satisfies(route -> {
                    assertThat(route.getPathTemplate())
                            .isEqualTo("/tenant/tenants/{tenantId}/identity-providers");
                    assertThat(route.getPermissionCode())
                            .isEqualTo(TenantPermissions.TENANT_IDENTITY_PROVIDERS_READ);
                });
    }

    @Test
    void shouldValidatePermissionsAndResolveTenantPathFromPrincipal() {
        PermissionCatalog permissions = new PermissionCatalog(List.of(() -> PermissionNode.module(
                "tenant",
                "permission.tenant",
                10,
                PermissionNode.resource(
                        "tenant:members",
                        "permission.tenant.members",
                        10,
                        PermissionNode.action("tenant:members:read", "permission.tenant.members.read", 10)))));
        NavigationRoute route = new NavigationRoute(
                "tenant.members",
                "/tenant/tenants/{tenantId}/members",
                Set.of(PrincipalType.TENANT),
                "tenant:members:read",
                MenuIconName.of("Users"));
        NavigationRouteCatalog catalog = new NavigationRouteCatalog(
                List.of(() -> List.of(route)), permissions, LocalizedMessagesFixture.create());
        Principal principal = Principal.withDisplayName(21L, PrincipalType.TENANT, 10L, 88L, "租户成员");

        assertThat(catalog.find("tenant.members")).isSameAs(route);
        assertThat(route.pathFor(principal)).isEqualTo("/tenant/tenants/88/members");
    }

    @Test
    void shouldRejectUnknownPermissionAndDuplicateRouteCode() {
        PermissionCatalog permissions = new PermissionCatalog(List.of(
                () -> PermissionNode.module("system", "permission.system", 10)));
        NavigationRoute route = new NavigationRoute(
                "system.menus",
                "/system/menus",
                Set.of(PrincipalType.PLATFORM),
                "system:menus:read",
                MenuIconName.of("PanelLeft"));

        LocalizedMessages messages = LocalizedMessagesFixture.create();
        assertThatThrownBy(() -> new NavigationRouteCatalog(
                List.of(() -> List.of(route)), permissions, messages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未注册权限");

        NavigationRoute dashboard = new NavigationRoute(
                "workspace.dashboard",
                "/dashboard",
                Set.of(PrincipalType.DEFAULT),
                null,
                MenuIconName.of("LayoutDashboard"));
        assertThatThrownBy(() -> new NavigationRouteCatalog(
                List.of(() -> List.of(dashboard), () -> List.of(dashboard)),
                permissions,
                messages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复导航路由编码");
    }
}
