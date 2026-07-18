package com.sca4d.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.me.service.DefaultMeService;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.PermissionChecker;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.iam.principal.PrincipalStatus;
import com.sca4d.iam.profile.DefaultProfile;
import com.sca4d.iam.profile.service.DefaultProfileService;
import com.sca4d.system.MenuIconName;
import com.sca4d.system.MenuService;
import com.sca4d.system.NavigationMenu;
import com.sca4d.tenant.TenantMembership;
import com.sca4d.tenant.TenantService;
import com.sca4d.test.TestSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证当前用户快照包含 Subject、Principal、角色、权限和租户成员关系。 */
class DefaultMeServiceTest {

    @Test
    void shouldBuildSnapshotFromExplicitCurrentRequest() {
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        DefaultProfileService defaultProfileService = mock(DefaultProfileService.class);
        TenantService tenantService = mock(TenantService.class);
        MenuService menuService = mock(MenuService.class);
        Principal principal = Principal.withDisplayName(22L, PrincipalType.PLATFORM, 11L, null, "管理员");
        SessionPermission permission = new SessionPermission(22L, Set.of("PLATFORM_ADMIN"), Set.of("system:read"));
        CurrentRequest currentRequest = CurrentRequest.authenticated(TestSession.withAuthorization(
                new Subject(11L, "管理员"), List.of(principal), Map.of(22L, permission)));
        currentRequest.setPrincipal(principal);
        when(permissionChecker.require(currentRequest)).thenReturn(permission);
        DefaultProfile defaultProfile = new DefaultProfile()
                .setSubjectId(11L)
                .setAvatarFileId(44L)
                .setVersion(5L);
        when(defaultProfileService.require(11L)).thenReturn(defaultProfile);
        TenantMembership membership = new TenantMembership();
        membership.setTenantId(33L);
        when(tenantService.memberships(11L)).thenReturn(List.of(membership));
        NavigationMenu navigation = new NavigationMenu(
                1L, "workspace.dashboard", "/dashboard", "工作台", MenuIconName.of("LayoutDashboard"), List.of());
        when(menuService.navigation(principal, permission)).thenReturn(List.of(navigation));

        var snapshot = new DefaultMeService(permissionChecker, defaultProfileService, tenantService, menuService)
                .snapshot(currentRequest);

        assertThat(snapshot.getSubject().getSubjectId()).isEqualTo(11L);
        assertThat(snapshot.getAccount().getAccountId()).isEqualTo(11L);
        assertThat(snapshot.getAccount().getSubjectId()).isEqualTo(11L);
        assertThat(snapshot.getDefaultProfile()).isSameAs(defaultProfile);
        assertThat(snapshot.getPrincipal()).isEqualTo(principal);
        assertThat(snapshot.getSession()).isSameAs(currentRequest.getSession());
        assertThat(snapshot.getPermission()).isSameAs(permission);
        assertThat(snapshot.getAvailablePrincipals()).singleElement().satisfies(available -> {
            assertThat(available.getPrincipalId()).isEqualTo(22L);
            assertThat(available.getStatus()).isEqualTo(PrincipalStatus.ACTIVE);
        });
        assertThat(snapshot.getTenants()).containsExactly(membership);
        assertThat(snapshot.getNavigation()).containsExactly(navigation);
        verify(permissionChecker).require(currentRequest);
        verify(defaultProfileService).require(11L);
        verify(menuService).navigation(principal, permission);
    }
}
