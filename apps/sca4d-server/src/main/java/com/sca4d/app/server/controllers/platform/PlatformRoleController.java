package com.sca4d.app.server.controllers.platform;

import com.sca4d.app.server.audit.ApplicationAuditValues;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.platform.request.ChangeRoleAccountsRequest;
import com.sca4d.app.server.platform.request.CreateRoleRequest;
import com.sca4d.app.server.platform.request.ReplaceRoleI18nRequest;
import com.sca4d.app.server.platform.request.ReplaceRolePermissionsRequest;
import com.sca4d.app.server.platform.request.UpdateRoleStatusRequest;
import com.sca4d.app.server.platform.view.PlatformAccountView;
import com.sca4d.app.server.platform.view.RoleI18nView;
import com.sca4d.app.server.platform.view.RolePermissionsView;
import com.sca4d.app.server.platform.view.RoleView;
import com.sca4d.app.server.service.PlatformAccountApplicationService;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditPayload;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.authentication.RequireSessionToken;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.core.spring.web.annotation.PageOptions;
import com.sca4d.iam.base.PlatformAccountQuery;
import com.sca4d.iam.base.rbac.PlatformOperations;
import com.sca4d.iam.base.rbac.PlatformRbacService;
import com.sca4d.iam.base.rbac.PlatformRoleDetails;
import com.sca4d.iam.identity.IdentityStatus;
import com.sca4d.iam.rbac.Role;
import com.sca4d.iam.rbac.RolePermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** 管理完整示例 App 的 Platform 角色、角色 ACTION 和角色 Account 关系。 */
@RestController
@RequestMapping("/platform/roles")
@Tag(name = "Platform RBAC")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class PlatformRoleController {

    private final @NotNull PlatformRbacService rbac;

    private final @NotNull PlatformAccountApplicationService accounts;

    /**
     * 创建 Platform 角色管理入口。
     *
     * @param rbac Platform RBAC Java API
     * @param accounts 示例 App 的 Platform Account 应用服务
     */
    public PlatformRoleController(
            @NotNull PlatformRbacService rbac,
            @NotNull PlatformAccountApplicationService accounts) {
        this.rbac = Objects.requireNonNull(rbac, "Platform RBAC 服务不能为空");
        this.accounts = Objects.requireNonNull(accounts, "Platform Account 应用服务不能为空");
    }

    /**
     * 查询全部 Platform 角色。
     *
     * @return 全部 Platform 角色
     */
    @GetMapping
    @Operation(summary = "查询 Platform 角色")
    @RequirePermission(operation = PlatformOperations.ROLES_READ_VALUE)
    public @NotNull ApiResponse<List<RoleView>> findRoles() {
        return ApiResponse.ok(rbac.findRoles().stream().map(RoleView::from).toList());
    }

    /**
     * 查询指定 Platform 角色。
     *
     * @param roleId 角色标识
     * @return 指定角色
     */
    @GetMapping("/{roleId}")
    @Operation(summary = "查询 Platform 角色详情")
    @RequirePermission(operation = PlatformOperations.ROLES_READ_VALUE)
    public @NotNull ApiResponse<RoleView> get(@PathVariable long roleId) {
        PlatformRoleDetails result = rbac.loadRoleById(roleId);
        return ApiResponse.ok(RoleView.from(result));
    }

    /**
     * 查询指定 Platform 角色已分配或未分配的一页 Account。
     *
     * @param roleId Platform 角色标识
     * @param assigned 查询已分配 Account 时为 {@code true}
     * @param search 可选用户名搜索词
     * @param status 可选 Account 状态
     * @param page 分页要求
     * @return 当前页完整账号
     */
    @GetMapping("/{roleId}/accounts")
    @Operation(summary = "查询 Platform 角色 Account")
    @RequirePermission(operation = PlatformOperations.ROLES_READ_VALUE)
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_READ_VALUE)
    public @NotNull ApiResponse<Page<PlatformAccountView>> findRoleAccounts(
            @PathVariable long roleId,
            @RequestParam(defaultValue = "true") boolean assigned,
            @RequestParam(required = false) @Nullable String search,
            @RequestParam(required = false) @Nullable IdentityStatus status,
            @PageOptions(maxSize = 100) @NotNull PageRequest page) {
        return ApiResponse.ok(
                accounts.findRoleAccounts(
                        roleId,
                        assigned,
                        new PlatformAccountQuery(search, status, page))
                        .map(PlatformAccountView::from));
    }

    /**
     * 原子批量增加或移除指定 Platform 角色的 Account。
     *
     * @param roleId Platform 角色标识
     * @param request 添加和移除 Account 标识集合
     * @return 不包含数据的成功响应
     */
    @PatchMapping("/{roleId}/accounts")
    @Operation(summary = "批量修改 Platform 角色 Account")
    @RequirePermission(operation = PlatformOperations.ROLES_READ_VALUE)
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE_VALUE)
    @Audit(operation = "platform/role/accounts/change", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<Void> changeRoleAccounts(
            @PathVariable @AuditTarget(resourceType = Role.RESOURCE_TYPE_VALUE, idPrefix = "platform:") long roleId,
            @Valid @RequestBody @NotNull @AuditPayload ChangeRoleAccountsRequest request) {
        accounts.changeRoleAccounts(
                roleId,
                request.getAddAccountIds(),
                request.getRemoveAccountIds());
        return ApiResponse.noContent();
    }

    /**
     * 创建 Platform 角色。
     *
     * @param request 角色编码、完整多语言字段和初始 ACTION
     * @return 新角色和 Location
     */
    @PostMapping
    @Operation(summary = "创建 Platform 角色")
    @RequirePermission(operation = PlatformOperations.ROLES_MANAGE_VALUE)
    @Audit(operation = "platform/role/create", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<RoleView> createRole(
            @RequestBody @NotNull @AuditPayload CreateRoleRequest request) {
        PlatformRoleDetails created = rbac.createRole(
                request.getCode(),
                request.getI18n(),
                request.getPermissionCodeTrees());
        return ApiResponse.created(
                RoleView.from(created),
                URI.create("/platform/roles/" + created.role().roleId()));
    }

    /**
     * 修改 Platform 角色状态。
     *
     * @param roleId 角色标识
     * @param request 新状态
     * @return 修改后的角色
     */
    @PutMapping("/{roleId}/status")
    @IfMatch
    @Operation(summary = "修改 Platform 角色状态")
    @RequirePermission(operation = PlatformOperations.ROLES_MANAGE_VALUE)
    @Audit(operation = "platform/role/update", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<RoleView> updateRoleStatus(
            @PathVariable @AuditTarget(resourceType = Role.RESOURCE_TYPE_VALUE, idPrefix = "platform:") long roleId,
            @RequestBody @NotNull @AuditPayload UpdateRoleStatusRequest request) {
        PlatformRoleDetails updated = rbac.updateRoleStatus(roleId, request.getStatus());
        return ApiResponse.ok(RoleView.from(updated));
    }

    /**
     * 查询 Platform 角色编辑使用的完整多语言字段。
     *
     * @param roleId 角色标识
     * @return 完整语言映射和当前 ETag
     */
    @GetMapping("/{roleId}/i18n")
    @Operation(summary = "查询 Platform 角色多语言字段")
    @RequirePermission(operation = PlatformOperations.ROLES_READ_VALUE)
    public @NotNull ApiResponse<RoleI18nView> findRoleI18n(@PathVariable long roleId) {
        PlatformRoleDetails result = rbac.loadRoleById(roleId);
        return ApiResponse.ok(RoleI18nView.from(result));
    }

    /**
     * 原子替换 Platform 角色的完整多语言字段。
     *
     * @param roleId 角色标识
     * @param request 新的完整语言映射
     * @return 修改后的完整语言映射和新 ETag
     */
    @PutMapping("/{roleId}/i18n")
    @IfMatch
    @Operation(summary = "替换 Platform 角色多语言字段")
    @RequirePermission(operation = PlatformOperations.ROLES_MANAGE_VALUE)
    @Audit(operation = "platform/role/update", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<RoleI18nView> replaceRoleI18n(
            @PathVariable @AuditTarget(resourceType = Role.RESOURCE_TYPE_VALUE, idPrefix = "platform:") long roleId,
            @RequestBody @NotNull @AuditPayload ReplaceRoleI18nRequest request) {
        PlatformRoleDetails updated = rbac.replaceRoleI18n(roleId, request.getI18n());
        return ApiResponse.ok(RoleI18nView.from(updated));
    }

    /**
     * 删除 Platform 角色。
     *
     * @param roleId 角色标识
     * @return 不包含数据的成功响应
     */
    @DeleteMapping("/{roleId}")
    @IfMatch
    @RequireSessionToken
    @Operation(summary = "删除 Platform 角色")
    @RequirePermission(operation = PlatformOperations.ROLES_MANAGE_VALUE)
    @Audit(operation = "platform/role/delete")
    public @NotNull ApiResponse<Void> deleteRole(
            @PathVariable @AuditTarget(resourceType = Role.RESOURCE_TYPE_VALUE, idPrefix = "platform:") long roleId) {
        rbac.deleteRole(roleId);
        return ApiResponse.noContent();
    }

    /**
     * 查询 Platform 角色当前得到的 ACTION。
     *
     * @param roleId 角色标识
     * @return 角色当前得到的具体 ACTION 和 ETag
     */
    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "查询 Platform 角色 Operation")
    @RequirePermission(operation = PlatformOperations.ROLES_READ_VALUE)
    public @NotNull ApiResponse<RolePermissionsView> findRolePermissions(
            @PathVariable long roleId) {
        RolePermissions result = rbac.findRolePermissions(roleId);
        return ApiResponse.ok(RolePermissionsView.from(result));
    }

    /**
     * 原子替换 Platform 角色得到的 ACTION。
     *
     * @param roleId 角色标识
     * @param request 新 ACTION 编码集合
     * @return 原子替换后的具体 ACTION 和新 ETag
     */
    @PutMapping("/{roleId}/permissions")
    @IfMatch
    @Operation(summary = "替换 Platform 角色 Operation")
    @RequirePermission(operation = PlatformOperations.ROLES_MANAGE_VALUE)
    @Audit(operation = "platform/role/permissions/replace", payloadFactory = ApplicationAuditValues.MutationDetails.class)
    public @NotNull ApiResponse<RolePermissionsView> replaceRolePermissions(
            @PathVariable @AuditTarget(resourceType = Role.RESOURCE_TYPE_VALUE, idPrefix = "platform:") long roleId,
            @RequestBody @NotNull @AuditPayload ReplaceRolePermissionsRequest request) {
        RolePermissions result = rbac.replaceRolePermissions(
                roleId,
                request.getPermissionCodeTrees());
        return ApiResponse.ok(RolePermissionsView.from(result));
    }
}
