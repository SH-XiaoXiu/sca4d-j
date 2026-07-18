package com.sca4d.controllers.mg;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.controllers.mg.platform.PlatformAccountController;
import com.sca4d.controllers.mg.platform.PlatformPrincipalController;
import com.sca4d.controllers.mg.platform.PlatformPrincipalTenantAccessController;
import com.sca4d.controllers.mg.platform.PlatformRoleController;
import com.sca4d.controllers.mg.platform.PlatformTenantAccountController;
import com.sca4d.controllers.mg.platform.PlatformTenantController;
import com.sca4d.controllers.mg.platform.PlatformTenantDictionaryController;
import com.sca4d.controllers.mg.platform.PlatformTenantMemberController;
import com.sca4d.controllers.mg.platform.PlatformTenantRoleController;
import com.sca4d.controllers.mg.system.SystemMenuController;
import com.sca4d.controllers.mg.tenant.TenantAccountController;
import com.sca4d.controllers.mg.tenant.TenantDictionaryController;
import com.sca4d.controllers.mg.tenant.TenantMemberController;
import com.sca4d.controllers.mg.tenant.TenantRoleController;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.iam.principal.TenantPrincipalSelection;
import com.sca4d.iam.principal.request.PlatformPrincipalRolesRequest;
import com.sca4d.iam.principal.request.PlatformPrincipalStatusRequest;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.system.SystemPermissions;
import com.sca4d.system.request.MenuCreateRequest;
import com.sca4d.system.request.MenuDeleteRequest;
import com.sca4d.tenant.TenantPermissions;
import com.sca4d.tenant.policy.TenantAccessPolicy;
import com.sca4d.tenant.request.TenantAccessReplaceRequest;
import com.sca4d.tenant.request.TenantCreateRequest;
import com.sca4d.tenant.request.TenantDeleteRequest;
import com.sca4d.tenant.request.TenantMemberCreateAccountRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

/** 验证管理端 Controller 的路由、权限声明和参数类型。 */
class ManagementApiContractTest {

    @Test
    void platformManagementEndpointsShouldUsePlatformPrincipalAndPlatformPermissions() throws Exception {
        RequestMapping accounts = PlatformAccountController.class.getAnnotation(RequestMapping.class);
        RequestMapping tenants = PlatformTenantController.class.getAnnotation(RequestMapping.class);
        RequestMapping members = PlatformTenantMemberController.class.getAnnotation(RequestMapping.class);
        RequestMapping roles = PlatformRoleController.class.getAnnotation(RequestMapping.class);
        RequestMapping principals = PlatformPrincipalController.class.getAnnotation(RequestMapping.class);
        RequestMapping tenantRoles = PlatformTenantRoleController.class.getAnnotation(RequestMapping.class);
        AuthPrincipal principal = PlatformAccountController.class.getAnnotation(AuthPrincipal.class);

        assertThat(accounts.value()).containsExactly("/mg/platform/accounts");
        assertThat(tenants.value()).containsExactly("/mg/platform/tenants");
        assertThat(members.value()).containsExactly("/mg/platform/tenants/{tenantId}/members");
        assertThat(roles.value()).containsExactly("/mg/platform/roles");
        assertThat(principals.value()).containsExactly("/mg/platform/operators");
        assertThat(tenantRoles.value()).containsExactly("/mg/platform/tenants/{tenantId}/roles");
        assertThat(principal.types()).containsExactly(PrincipalType.PLATFORM);
        assertThat(principal.selection()).isEqualTo(PlatformPrincipalSelection.class);
        assertThat(PlatformPrincipalController.class.getDeclaredMethod(
                "changeStatus",
                Long.class,
                PlatformPrincipalStatusRequest.class)
                .getAnnotation(PatchMapping.class)
                .value())
                .containsExactly("/{principalId}");
        assertThat(PlatformPrincipalController.class.getDeclaredMethod(
                "changeStatus",
                Long.class,
                PlatformPrincipalStatusRequest.class)
                .getAnnotation(RequirePermission.class)
                .value())
                .containsExactly(PlatformPermissions.OPERATORS_MANAGE);
        Method replacePlatformRoles = PlatformPrincipalController.class.getDeclaredMethod(
                "replaceRoles",
                Long.class,
                PlatformPrincipalRolesRequest.class);
        assertThat(replacePlatformRoles.getAnnotation(PutMapping.class).value())
                .containsExactly("/{principalId}/roles");
        assertThat(replacePlatformRoles.getAnnotation(RequirePermission.class).value())
                .containsExactly(PlatformPermissions.OPERATORS_MANAGE, PlatformPermissions.ROLES_MANAGE);
        Method assignableRoles = PlatformPrincipalController.class.getDeclaredMethod("assignableRoles", Long.class);
        assertThat(assignableRoles.getAnnotation(GetMapping.class).value())
                .containsExactly("/{principalId}/assignable-roles");
        assertThat(assignableRoles.getAnnotation(RequirePermission.class).value())
                .containsExactly(PlatformPermissions.OPERATORS_READ, PlatformPermissions.ROLES_READ);
        assertThat(PlatformTenantController.class.getDeclaredMethod("create", TenantCreateRequest.class)
                .getAnnotation(Idempotent.class))
                .isNotNull();
        Method deleteTenant = PlatformTenantController.class.getDeclaredMethod(
                "delete", Long.class, TenantDeleteRequest.class);
        assertThat(deleteTenant.getAnnotation(Idempotent.class)).isNotNull();
        assertThat(deleteTenant.getAnnotation(RequirePermission.class).value())
                .containsExactly(PlatformPermissions.TENANTS_DELETE);
        assertThat(deleteTenant.getAnnotation(Access.class).action()).isEqualTo(AccessAction.MANAGE);
        Method platformAssignableMemberRoles = PlatformTenantMemberController.class.getDeclaredMethod("assignableRoles", Long.class);
        assertThat(platformAssignableMemberRoles.getAnnotation(GetMapping.class).value())
                .containsExactly("/assignable-roles");
        assertThat(platformAssignableMemberRoles
                .getAnnotation(RequirePermission.class)
                .value())
                .containsExactly(PlatformPermissions.TENANT_MEMBERS_READ);
        Access platformMemberAccess = PlatformTenantMemberController.class
                .getDeclaredMethod("detail", Long.class, Long.class)
                .getAnnotation(Access.class);
        assertThat(platformMemberAccess.policy()).isEqualTo(TenantAccessPolicy.class);
        assertThat(platformMemberAccess.action()).isEqualTo(AccessAction.READ);
        assertThat(platformMemberAccess.resourceId()).isEqualTo("#tenantId");
        assertThat(PlatformRoleController.class.getDeclaredMethod("permissions")
                .getAnnotation(RequirePermission.class)
                .value())
                .containsExactly(PlatformPermissions.ROLES_READ);
        assertThat(PlatformTenantRoleController.class.getDeclaredMethod("permissions", Long.class)
                .getAnnotation(RequirePermission.class)
                .value())
                .containsExactly(PlatformPermissions.TENANT_ROLES_READ);
        Access tenantAccess = PlatformTenantController.class.getDeclaredMethod("detail", Long.class)
                .getAnnotation(Access.class);
        assertThat(tenantAccess.policy()).isEqualTo(TenantAccessPolicy.class);
        assertThat(tenantAccess.resourceId()).isEqualTo("#tenantId");
        RequestMapping principalTenantAccess = PlatformPrincipalTenantAccessController.class.getAnnotation(RequestMapping.class);
        assertThat(principalTenantAccess.value())
                .containsExactly("/mg/platform/operators/{principalId}/tenant-access-grants");
        Method readPrincipalTenantAccess = PlatformPrincipalTenantAccessController.class.getDeclaredMethod(
                "configuration", Long.class);
        assertThat(readPrincipalTenantAccess.getAnnotation(GetMapping.class)).isNotNull();
        assertThat(readPrincipalTenantAccess.getAnnotation(RequirePermission.class).value())
                .containsExactly(PlatformPermissions.TENANT_ACCESS_READ, PlatformPermissions.OPERATORS_READ);
        Method replacePrincipalTenantAccess = PlatformPrincipalTenantAccessController.class.getDeclaredMethod(
                "replace",
                Long.class,
                TenantAccessReplaceRequest.class,
                Principal.class);
        assertThat(replacePrincipalTenantAccess.getAnnotation(PutMapping.class)).isNotNull();
        assertThat(replacePrincipalTenantAccess.getAnnotation(Idempotent.class)).isNotNull();
        assertThat(replacePrincipalTenantAccess.getAnnotation(RequirePermission.class).value())
                .containsExactly(PlatformPermissions.TENANT_ACCESS_MANAGE, PlatformPermissions.OPERATORS_MANAGE);
    }

    @Test
    void tenantManagementMembersEndpointShouldSelectPrincipalFromPathTenantId() throws Exception {
        RequestMapping mapping = TenantMemberController.class.getAnnotation(RequestMapping.class);
        AuthPrincipal principal = TenantMemberController.class.getAnnotation(AuthPrincipal.class);

        assertThat(mapping.value()).containsExactly("/mg/tenant/tenants/{tenantId}/members");
        assertThat(principal.types()).containsExactly(PrincipalType.TENANT);
        assertThat(principal.selection()).isEqualTo(TenantPrincipalSelection.class);
        assertThat(principal.tenantId()).isEqualTo("#path.tenantId");
        Method createAccount = TenantMemberController.class.getDeclaredMethod("createNewAccount", Long.class,
                TenantMemberCreateAccountRequest.class,
                Principal.class);
        assertThat(createAccount.getAnnotation(RequirePermission.class)
                .value())
                .containsExactly(TenantPermissions.TENANT_MEMBERS_CREATE);
        Access createAccess = createAccount.getAnnotation(Access.class);
        assertThat(createAccess.policy()).isEqualTo(TenantAccessPolicy.class);
        assertThat(createAccess.action()).isEqualTo(AccessAction.WRITE);
        assertThat(createAccess.resourceId()).isEqualTo("#tenantId");
        Method tenantAssignableMemberRoles = TenantMemberController.class.getDeclaredMethod("assignableRoles", Long.class);
        assertThat(tenantAssignableMemberRoles.getAnnotation(GetMapping.class).value())
                .containsExactly("/assignable-roles");
        assertThat(tenantAssignableMemberRoles
                .getAnnotation(RequirePermission.class)
                .value())
                .containsExactly(TenantPermissions.TENANT_MEMBERS_READ);
    }

    @Test
    void tenantRoleEndpointShouldSelectPrincipalFromPathTenantId() throws Exception {
        RequestMapping mapping = TenantRoleController.class.getAnnotation(RequestMapping.class);
        AuthPrincipal principal = TenantRoleController.class.getAnnotation(AuthPrincipal.class);

        assertThat(mapping.value()).containsExactly("/mg/tenant/tenants/{tenantId}/roles");
        assertThat(principal.types()).containsExactly(PrincipalType.TENANT);
        assertThat(principal.selection()).isEqualTo(TenantPrincipalSelection.class);
        assertThat(principal.tenantId()).isEqualTo("#path.tenantId");
        assertThat(TenantRoleController.class.getDeclaredMethod("permissions", Long.class)
                .getAnnotation(RequirePermission.class)
                .value())
                .containsExactly(TenantPermissions.TENANT_ROLES_READ);
    }

    @Test
    void systemMenuEndpointShouldUsePlatformPrincipalAndCodePermissions() throws Exception {
        RequestMapping mapping = SystemMenuController.class.getAnnotation(RequestMapping.class);
        AuthPrincipal principal = SystemMenuController.class.getAnnotation(AuthPrincipal.class);

        assertThat(mapping.value()).containsExactly("/mg/system/menus");
        assertThat(principal.types()).containsExactly(PrincipalType.PLATFORM);
        assertThat(principal.selection()).isEqualTo(PlatformPrincipalSelection.class);
        assertThat(SystemMenuController.class.getDeclaredMethod("menus")
                .getAnnotation(RequirePermission.class)
                .value())
                .containsExactly(SystemPermissions.MENUS_READ);
        assertThat(SystemMenuController.class.getDeclaredMethod(
                "create", MenuCreateRequest.class)
                .getAnnotation(Idempotent.class))
                .isNotNull();
        assertThat(SystemMenuController.class.getDeclaredMethod(
                "delete", Long.class, MenuDeleteRequest.class)
                .getAnnotation(Idempotent.class))
                .isNotNull();
    }

    @Test
    void managementWriteEndpointsShouldDeclareOperationAudit() {
        for (Class<?> controller : List.of(
                PlatformAccountController.class,
                PlatformTenantAccountController.class,
                PlatformPrincipalController.class,
                PlatformRoleController.class,
                PlatformPrincipalTenantAccessController.class,
                PlatformTenantController.class,
                PlatformTenantMemberController.class,
                PlatformTenantRoleController.class,
                PlatformTenantDictionaryController.class,
                SystemMenuController.class,
                TenantMemberController.class,
                TenantAccountController.class,
                TenantRoleController.class,
                TenantDictionaryController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                boolean writeEndpoint = method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(PatchMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class);
                if (writeEndpoint) {
                    assertThat(method.getAnnotation(Audit.class))
                            .as("%s.%s 必须声明操作审计", controller.getSimpleName(), method.getName())
                            .isNotNull();
                }
            }
        }
    }
}
