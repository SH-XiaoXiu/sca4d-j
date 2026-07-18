package com.sca4d.controllers.mg.platform;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.platform.audit.PlatformAuditOperations;
import com.sca4d.role.RoleService;
import com.sca4d.role.request.RoleCreateRequest;
import com.sca4d.role.request.RoleDeleteRequest;
import com.sca4d.role.request.RolePageRequest;
import com.sca4d.role.request.RolePermissionsRequest;
import com.sca4d.role.request.RoleUpdateRequest;
import com.sca4d.role.view.RolePermissionView;
import com.sca4d.role.view.RoleUsageView;
import com.sca4d.role.view.RoleView;
import com.sca4d.tenant.policy.TenantAccessPolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台管理员代管指定租户角色和权限集合的接口。 */
@RestController
@RequestMapping("/mg/platform/tenants/{tenantId}/roles")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class PlatformTenantRoleController {

    private final RoleService service;

    /** 分页读取指定租户精确范围内的角色及使用量。 */
    @GetMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_ROLES_READ)
    public @NotNull ApiResponse<PageData<RoleView>> page(
            @PathVariable @NotNull Long tenantId,
            @Valid @NotNull RolePageRequest request) {
        return ApiResponse.ok(service
                .page(tenantId, request.getSearch(), request.getStatus(), request.page(), request.size())
                .map(RoleView::from));
    }

    /** 读取租户角色允许分配的 tenant 权限目录。 */
    @GetMapping("/permissions")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_ROLES_READ)
    public @NotNull ApiResponse<List<RolePermissionView>> permissions(@PathVariable @NotNull Long tenantId) {
        return ApiResponse.ok(service.assignablePermissions(tenantId).stream()
                .map(RolePermissionView::from)
                .toList());
    }

    /** 为指定租户创建编码不可变的自定义角色。 */
    @PostMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Idempotent(operation = "platform.tenant-roles.create")
    @Audit(module = PlatformAuditOperations.TENANT_ROLES, action = PlatformAuditOperations.CREATE, targetType = "tenant", targetId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_ROLES_MANAGE)
    public @NotNull ApiResponse<Long> create(
            @PathVariable @NotNull Long tenantId,
            @Valid @RequestBody @NotNull RoleCreateRequest request) {
        return ApiResponse.created(service.create(
                tenantId, request.getCode(), request.getName(), request.getPermissionIds()));
    }

    /** 读取指定租户的一个角色详情。 */
    @GetMapping("/{roleId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_ROLES_READ)
    public @NotNull ApiResponse<RoleView> detail(
            @PathVariable @NotNull Long tenantId, @PathVariable @NotNull Long roleId) {
        return ApiResponse.ok(RoleView.from(service.detail(tenantId, roleId)));
    }

    /** 修改指定租户角色名称和状态。 */
    @PutMapping("/{roleId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Audit(module = PlatformAuditOperations.TENANT_ROLES, action = PlatformAuditOperations.UPDATE, targetType = "role", targetId = "#roleId")
    @RequirePermission(PlatformPermissions.TENANT_ROLES_MANAGE)
    public @NotNull ApiResponse<Void> update(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long roleId,
            @Valid @RequestBody @NotNull RoleUpdateRequest request) {
        service.update(tenantId, roleId, request.getName(), request.getStatus(), request.getVersion());
        return ApiResponse.ok();
    }

    /** 替换指定租户自定义角色的全部 tenant 权限集合。 */
    @PutMapping("/{roleId}/permissions")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Audit(module = PlatformAuditOperations.TENANT_ROLES, action = PlatformAuditOperations.REPLACE_PERMISSIONS, targetType = "role", targetId = "#roleId")
    @RequirePermission(PlatformPermissions.TENANT_ROLES_MANAGE)
    public @NotNull ApiResponse<Void> replacePermissions(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long roleId,
            @Valid @RequestBody @NotNull RolePermissionsRequest request) {
        service.replacePermissions(tenantId, roleId, request.getPermissionIds(), request.getVersion());
        return ApiResponse.ok();
    }

    /** 独立读取指定租户角色的当前 Principal 使用量。 */
    @GetMapping("/{roleId}/usage")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(PlatformPermissions.TENANT_ROLES_READ)
    public @NotNull ApiResponse<RoleUsageView> usage(
            @PathVariable @NotNull Long tenantId, @PathVariable @NotNull Long roleId) {
        return ApiResponse.ok(RoleUsageView.from(service.detail(tenantId, roleId)));
    }

    /** 删除指定租户中未被 Principal 使用的自定义角色。 */
    @DeleteMapping("/{roleId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Idempotent(operation = "platform.tenant-roles.delete")
    @Audit(module = PlatformAuditOperations.TENANT_ROLES, action = PlatformAuditOperations.DELETE, targetType = "role", targetId = "#roleId")
    @RequirePermission(PlatformPermissions.TENANT_ROLES_MANAGE)
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long roleId,
            @Valid @RequestBody @NotNull RoleDeleteRequest request) {
        service.delete(tenantId, roleId, request.getVersion());
        return ApiResponse.ok();
    }
}
