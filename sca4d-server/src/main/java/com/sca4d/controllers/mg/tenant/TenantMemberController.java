package com.sca4d.controllers.mg.tenant;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.principal.TenantPrincipalSelection;
import com.sca4d.tenant.TenantMemberService;
import com.sca4d.tenant.TenantPermissions;
import com.sca4d.tenant.audit.TenantAuditOperations;
import com.sca4d.tenant.policy.TenantAccessPolicy;
import com.sca4d.tenant.request.TenantMemberCreateAccountRequest;
import com.sca4d.tenant.request.TenantMemberExistingAccountRequest;
import com.sca4d.tenant.request.TenantMemberPageRequest;
import com.sca4d.tenant.request.TenantMemberRolesRequest;
import com.sca4d.tenant.request.TenantMemberStatusRequest;
import com.sca4d.tenant.view.TenantMemberView;
import com.sca4d.tenant.view.TenantRoleView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户管理端成员接口。
 */
@RestController
@RequestMapping("/mg/tenant/tenants/{tenantId}/members")
@AuthPrincipal(types = PrincipalType.TENANT, selection = TenantPrincipalSelection.class, tenantId = "#path.tenantId")
@RequiredArgsConstructor
public class TenantMemberController {

    private final TenantMemberService tenantMemberService;

    /**
     * 查询当前租户中可以分配给成员的有效角色。
     *
     * @param tenantId 已由 TENANT Principal 选择器校验的租户主键
     * @return 角色选择项
     */
    @GetMapping("/assignable-roles")
    @RequirePermission(TenantPermissions.TENANT_MEMBERS_READ)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    public @NotNull ApiResponse<List<TenantRoleView>> assignableRoles(@PathVariable @NotNull Long tenantId) {
        return ApiResponse.ok(tenantMemberService.assignableRoles(tenantId).stream()
                .map(TenantRoleView::from)
                .toList());
    }

    /**
     * 分页查询当前租户中的成员和角色分配。
     *
     * @param tenantId 已由 TENANT Principal 选择器校验的租户主键
     * @param request 分页和筛选条件
     * @return 成员展示对象分页
     */
    @GetMapping
    @RequirePermission(TenantPermissions.TENANT_MEMBERS_READ)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    public @NotNull ApiResponse<PageData<TenantMemberView>> page(
            @PathVariable @NotNull Long tenantId, @NotNull TenantMemberPageRequest request) {
        return ApiResponse.ok(tenantMemberService.page(
                tenantId, request.getSearch(), request.getStatus(), request.page(), request.size())
                .map(TenantMemberView::from));
    }

    /**
     * 查询当前租户中的一个成员。
     *
     * @param tenantId 已由 TENANT Principal 选择器校验的租户主键
     * @param principalId 成员 TENANT Principal 主键
     * @return 成员及角色分配展示对象
     */
    @GetMapping("/{principalId}")
    @RequirePermission(TenantPermissions.TENANT_MEMBERS_READ)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    public @NotNull ApiResponse<TenantMemberView> detail(
            @PathVariable @NotNull Long tenantId, @PathVariable @NotNull Long principalId) {
        return ApiResponse.ok(TenantMemberView.from(tenantMemberService.require(tenantId, principalId)));
    }

    /**
     * 创建租户 Account 后立即加入当前租户。
     *
     * @param tenantId 已由 TENANT Principal 选择器校验的租户主键
     * @param request Account 认证材料、默认展示名和初始角色集合
     * @param actor 执行创建操作的 TENANT Principal，用于校验头像归属
     * @return 新建成员 Principal 主键
     */
    @PostMapping
    @Idempotent(operation = "tenant.tenant-members.create-account")
    @Audit(module = TenantAuditOperations.TENANT_MEMBERS, action = TenantAuditOperations.CREATE_ACCOUNT, targetType = "tenant", targetId = "#tenantId")
    @RequirePermission(TenantPermissions.TENANT_MEMBERS_CREATE)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    public @NotNull ApiResponse<Long> createNewAccount(
            @PathVariable @NotNull Long tenantId,
            @Valid @RequestBody @NotNull TenantMemberCreateAccountRequest request,
            @AuthPrincipal @NotNull Principal actor) {
        return ApiResponse.created(tenantMemberService.createNewAccount(
                tenantId,
                request.getIdentifier(),
                request.credential(),
                request.getDisplayName(),
                request.getPhone(),
                request.getEmail(),
                request.getAvatarFileId(),
                request.getAvatarUploadGrant(),
                actor.requirePrincipalId(),
                request.getRoleIds()));
    }

    /** 使用已有 Account 所属 Subject 创建当前租户成员，不修改目标账号认证材料。 */
    @PostMapping("/existing-accounts")
    @Idempotent(operation = "tenant.tenant-members.add-existing-account")
    @Audit(module = TenantAuditOperations.TENANT_MEMBERS, action = TenantAuditOperations.ADD_EXISTING_ACCOUNT, targetType = "tenant", targetId = "#tenantId")
    @RequirePermission(TenantPermissions.TENANT_MEMBERS_CREATE)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    public @NotNull ApiResponse<Long> addExistingAccount(
            @PathVariable @NotNull Long tenantId,
            @Valid @RequestBody @NotNull TenantMemberExistingAccountRequest request) {
        return ApiResponse.created(tenantMemberService.addExistingAccount(
                tenantId,
                request.getAccountId(),
                request.getDisplayName(),
                request.getRoleIds()));
    }

    /**
     * 修改当前租户成员的主体状态并清理该主体的授权缓存。
     *
     * <p>停用后该 TENANT Principal 不能再次进入当前租户；同一 Subject 的其他 Principal 和登录会话不受影响。
     *
     * @param tenantId 已由 TENANT Principal 选择器校验的租户主键
     * @param principalId 成员 TENANT Principal 主键
     * @param request 目标状态和当前成员版本
     * @return 没有响应数据的成功结果
     */
    @PatchMapping("/{principalId}")
    @Audit(module = TenantAuditOperations.TENANT_MEMBERS, action = TenantAuditOperations.CHANGE_STATUS, targetType = "tenant-member", targetId = "#principalId")
    @RequirePermission(TenantPermissions.TENANT_MEMBERS_UPDATE)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.WRITE, resourceId = "#tenantId")
    public @NotNull ApiResponse<Void> changeStatus(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long principalId,
            @Valid @RequestBody @NotNull TenantMemberStatusRequest request) {
        tenantMemberService.changeStatus(tenantId, principalId, request.getStatus(), request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 用新角色集合替换当前租户成员的角色关系。
     *
     * @param tenantId 已由 TENANT Principal 选择器校验的租户主键
     * @param principalId 成员 TENANT Principal 主键
     * @param request 新角色集合
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/{principalId}/roles")
    @Audit(module = TenantAuditOperations.TENANT_MEMBERS, action = TenantAuditOperations.REPLACE_ROLES, targetType = "tenant-member", targetId = "#principalId")
    @RequirePermission(TenantPermissions.TENANT_MEMBERS_UPDATE)
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    public @NotNull ApiResponse<Void> replaceRoles(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long principalId,
            @Valid @RequestBody @NotNull TenantMemberRolesRequest request) {
        tenantMemberService.replaceRoles(tenantId, principalId, request.getRoleIds(), request.getVersion());
        return ApiResponse.ok();
    }
}
