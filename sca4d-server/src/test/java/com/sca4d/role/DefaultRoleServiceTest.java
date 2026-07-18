package com.sca4d.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.principal.RoleStatus;
import com.sca4d.iam.repository.IamRepository;
import com.sca4d.role.service.DefaultRoleService;
import com.sca4d.tenant.TenantService;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证角色创建、修改、删除、权限替换和使用中检查。 */
class DefaultRoleServiceTest {

    @Test
    void shouldRejectReservedBuiltInCodesInTheirOwnScope() {
        IamRepository repository = mock(IamRepository.class);
        DefaultRoleService service = new DefaultRoleService(repository, mock(PrincipalService.class), mock(LocalizedMessages.class), mock(TenantService.class));

        assertThatThrownBy(() -> service.create(null, "PLATFORM_ADMIN", "管理员", List.of()))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey())
                                .isEqualTo("error.role.built-in-code-reserved"));
        assertThatThrownBy(() -> service.create(20L, "TENANT_ADMIN", "管理员", List.of()))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey())
                                .isEqualTo("error.role.built-in-code-reserved"));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectPermissionOutsideExactRoleScope() {
        IamRepository repository = mock(IamRepository.class);
        when(repository.findAssignableRolePermissions(true))
                .thenReturn(List.of(new ManagedPermission()
                        .setPermissionId(10L)
                        .setCode("tenant:read")
                        .setNameKey("permission.tenant.read")));
        DefaultRoleService service = new DefaultRoleService(repository, mock(PrincipalService.class), mock(LocalizedMessages.class), mock(TenantService.class));

        assertThatThrownBy(() -> service.create(20L, "AUDITOR", "审计员", List.of(10L, 11L)))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey())
                                .isEqualTo("error.role.permission-out-of-scope"));

        verify(repository, never()).createManagedRole(20L, "AUDITOR", "审计员");
    }

    @Test
    void shouldProtectBuiltInPermissionsAndDeletion() {
        IamRepository repository = mock(IamRepository.class);
        ManagedRole builtIn = new ManagedRole()
                .setRoleId(10L)
                .setCode("PLATFORM_ADMIN")
                .setName("平台管理员")
                .setStatus(RoleStatus.ACTIVE)
                .setBuiltIn(true)
                .setVersion(2L);
        when(repository.findManagedRole(null, 10L)).thenReturn(builtIn);
        DefaultRoleService service = new DefaultRoleService(repository, mock(PrincipalService.class), mock(LocalizedMessages.class), mock(TenantService.class));

        assertThatThrownBy(() -> service.replacePermissions(null, 10L, List.of(), 2L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey())
                                .isEqualTo("error.role.built-in-permissions-readonly"));
        assertThatThrownBy(() -> service.delete(null, 10L, 2L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey())
                                .isEqualTo("error.role.built-in-delete-forbidden"));

        verify(repository, never()).deleteManagedRole(null, 10L, 2L);
        verify(repository, never()).replaceManagedRolePermissions(10L, List.of());
    }

    @Test
    void shouldRejectDeletingRoleStillUsedByPrincipal() {
        IamRepository repository = mock(IamRepository.class);
        ManagedRole role = new ManagedRole()
                .setRoleId(10L)
                .setCode("AUDITOR")
                .setBuiltIn(false)
                .setPrincipalCount(2L);
        when(repository.findManagedRole(20L, 10L)).thenReturn(role);
        DefaultRoleService service = new DefaultRoleService(repository, mock(PrincipalService.class), mock(LocalizedMessages.class), mock(TenantService.class));

        assertThatThrownBy(() -> service.delete(20L, 10L, 3L))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMessageKey()).isEqualTo("error.role.in-use"));

        verify(repository, never()).deleteManagedRole(20L, 10L, 3L);
    }

    @Test
    void shouldRefreshRoleSessionsOnlyWhenStatusActuallyChanges() {
        IamRepository repository = mock(IamRepository.class);
        PrincipalService principalService = mock(PrincipalService.class);
        ManagedRole role = new ManagedRole()
                .setRoleId(10L)
                .setName("审计员")
                .setStatus(RoleStatus.ACTIVE)
                .setBuiltIn(false);
        when(repository.findManagedRole(20L, 10L)).thenReturn(role);
        DefaultRoleService service = new DefaultRoleService(repository, principalService, mock(LocalizedMessages.class), mock(TenantService.class));

        service.update(20L, 10L, "新审计员", RoleStatus.ACTIVE, 2L);
        verify(principalService, never()).refreshRoleSessions(10L);

        service.update(20L, 10L, "新审计员", RoleStatus.DISABLED, 3L);
        verify(principalService).refreshRoleSessions(10L);
    }

    @Test
    void shouldRefreshRoleSessionsAfterPermissionReplacement() {
        IamRepository repository = mock(IamRepository.class);
        PrincipalService principalService = mock(PrincipalService.class);
        ManagedRole role = new ManagedRole()
                .setRoleId(10L)
                .setName("审计员")
                .setStatus(RoleStatus.ACTIVE)
                .setBuiltIn(false);
        when(repository.findManagedRole(20L, 10L)).thenReturn(role);
        when(repository.findAssignableRolePermissions(true))
                .thenReturn(List.of(new ManagedPermission()
                        .setPermissionId(30L)
                        .setNameKey("permission.tenant.roles.manage")));
        DefaultRoleService service = new DefaultRoleService(repository, principalService, mock(LocalizedMessages.class), mock(TenantService.class));

        service.replacePermissions(20L, 10L, List.of(30L, 30L), 4L);

        verify(repository).updateManagedRole(20L, 10L, "审计员", RoleStatus.ACTIVE, 4L);
        verify(repository).replaceManagedRolePermissions(10L, List.of(30L));
        verify(principalService).refreshRoleSessions(10L);
    }

    @Test
    void shouldLeaveDatabaseConstraintTranslationToRepository() {
        IamRepository repository = mock(IamRepository.class);
        when(repository.findAssignableRolePermissions(false)).thenReturn(List.of());
        when(repository.createManagedRole(null, "AUDITOR", "审计员"))
                .thenThrow(new IllegalStateException("repository failure"));
        DefaultRoleService service = new DefaultRoleService(repository, mock(PrincipalService.class), mock(LocalizedMessages.class), mock(TenantService.class));

        assertThatThrownBy(() -> service.create(null, "AUDITOR", "审计员", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("repository failure");
    }

    @Test
    void shouldLocalizeRoleDetailAndAssignablePermissions() {
        IamRepository repository = mock(IamRepository.class);
        LocalizedMessages messages = mock(LocalizedMessages.class);
        ManagedPermission detailPermission = new ManagedPermission()
                .setPermissionId(10L)
                .setNameKey("permission.platform.roles.read");
        ManagedPermission assignablePermission = new ManagedPermission()
                .setPermissionId(20L)
                .setNameKey("permission.tenant.roles.manage");
        when(repository.findManagedRole(null, 30L))
                .thenReturn(new ManagedRole().setRoleId(30L).setPermissions(List.of(detailPermission)));
        when(repository.findAssignableRolePermissions(true)).thenReturn(List.of(assignablePermission));
        when(messages.get("permission.platform.roles.read")).thenReturn("View platform roles");
        when(messages.get("permission.tenant.roles.manage")).thenReturn("Manage roles");
        DefaultRoleService service = new DefaultRoleService(repository, mock(PrincipalService.class), messages, mock(TenantService.class));

        ManagedRole detail = service.detail(null, 30L);
        List<ManagedPermission> assignable = service.assignablePermissions(40L);

        assertThat(detail.getPermissions().getFirst().getNameKey()).isEqualTo("permission.platform.roles.read");
        assertThat(detail.getPermissions().getFirst().getName()).isEqualTo("View platform roles");
        assertThat(assignable.getFirst().getNameKey()).isEqualTo("permission.tenant.roles.manage");
        assertThat(assignable.getFirst().getName()).isEqualTo("Manage roles");
    }
}
