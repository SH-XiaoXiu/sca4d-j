package com.sca4d.controllers.mg.platform;

import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
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

/** 平台 Principal 可分配角色和权限集合的管理接口。 */
@RestController
@RequestMapping("/mg/platform/roles")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class PlatformRoleController {

    private final RoleService service;

    /** 分页读取平台范围角色及当前 Principal 使用量。 */
    @GetMapping
    @RequirePermission(PlatformPermissions.ROLES_READ)
    public @NotNull ApiResponse<PageData<RoleView>> page(@Valid @NotNull RolePageRequest request) {
        return ApiResponse.ok(service
                .page(null, request.getSearch(), request.getStatus(), request.page(), request.size())
                .map(RoleView::from));
    }

    /** 读取平台角色可以分配的完整代码权限目录。 */
    @GetMapping("/permissions")
    @RequirePermission(PlatformPermissions.ROLES_READ)
    public @NotNull ApiResponse<List<RolePermissionView>> permissions() {
        return ApiResponse.ok(service.assignablePermissions(null).stream()
                .map(RolePermissionView::from)
                .toList());
    }

    /** 创建编码不可变的平台自定义角色及其初始权限集合。 */
    @PostMapping
    @Idempotent(operation = "platform.roles.create")
    @Audit(module = PlatformAuditOperations.ROLES, action = PlatformAuditOperations.CREATE, targetType = "role")
    @RequirePermission(PlatformPermissions.ROLES_MANAGE)
    public @NotNull ApiResponse<Long> create(@Valid @RequestBody @NotNull RoleCreateRequest request) {
        return ApiResponse.created(service.create(null, request.getCode(), request.getName(), request.getPermissionIds()));
    }

    /** 读取一个平台角色、权限集合和当前使用量。 */
    @GetMapping("/{roleId}")
    @RequirePermission(PlatformPermissions.ROLES_READ)
    public @NotNull ApiResponse<RoleView> detail(@PathVariable @NotNull Long roleId) {
        return ApiResponse.ok(RoleView.from(service.detail(null, roleId)));
    }

    /** 修改平台角色名称和启用状态，角色编码保持不变。 */
    @PutMapping("/{roleId}")
    @Audit(module = PlatformAuditOperations.ROLES, action = PlatformAuditOperations.UPDATE, targetType = "role", targetId = "#roleId")
    @RequirePermission(PlatformPermissions.ROLES_MANAGE)
    public @NotNull ApiResponse<Void> update(
            @PathVariable @NotNull Long roleId,
            @Valid @RequestBody @NotNull RoleUpdateRequest request) {
        service.update(null, roleId, request.getName(), request.getStatus(), request.getVersion());
        return ApiResponse.ok();
    }

    /** 原子替换平台自定义角色的全部权限集合。 */
    @PutMapping("/{roleId}/permissions")
    @Audit(module = PlatformAuditOperations.ROLES, action = PlatformAuditOperations.REPLACE_PERMISSIONS, targetType = "role", targetId = "#roleId")
    @RequirePermission(PlatformPermissions.ROLES_MANAGE)
    public @NotNull ApiResponse<Void> replacePermissions(
            @PathVariable @NotNull Long roleId,
            @Valid @RequestBody @NotNull RolePermissionsRequest request) {
        service.replacePermissions(null, roleId, request.getPermissionIds(), request.getVersion());
        return ApiResponse.ok();
    }

    /** 独立读取平台角色当前 Principal 使用量。 */
    @GetMapping("/{roleId}/usage")
    @RequirePermission(PlatformPermissions.ROLES_READ)
    public @NotNull ApiResponse<RoleUsageView> usage(@PathVariable @NotNull Long roleId) {
        return ApiResponse.ok(RoleUsageView.from(service.detail(null, roleId)));
    }

    /** 删除未被 Principal 使用的平台自定义角色。 */
    @DeleteMapping("/{roleId}")
    @Idempotent(operation = "platform.roles.delete")
    @Audit(module = PlatformAuditOperations.ROLES, action = PlatformAuditOperations.DELETE, targetType = "role", targetId = "#roleId")
    @RequirePermission(PlatformPermissions.ROLES_MANAGE)
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @NotNull Long roleId,
            @Valid @RequestBody @NotNull RoleDeleteRequest request) {
        service.delete(null, roleId, request.getVersion());
        return ApiResponse.ok();
    }
}
