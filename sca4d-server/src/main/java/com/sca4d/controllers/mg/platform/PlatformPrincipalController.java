package com.sca4d.controllers.mg.platform;

import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.PageData;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.principal.request.PlatformOperatorCreateRequest;
import com.sca4d.iam.principal.request.PlatformOperatorPageRequest;
import com.sca4d.iam.principal.request.PlatformPrincipalRolesRequest;
import com.sca4d.iam.principal.request.PlatformPrincipalStatusRequest;
import com.sca4d.iam.principal.view.PlatformPrincipalView;
import com.sca4d.iam.principal.view.PlatformRoleView;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.platform.audit.PlatformAuditOperations;
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
 * PLATFORM Principal 资源管理接口。
 */
@RestController
@RequestMapping("/mg/platform/operators")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class PlatformPrincipalController {

    private final PrincipalService principalService;
    private final AccountService accountService;

    /** 分页读取平台操作员及其平台角色，不返回 Account、Credential 或 Session。 */
    @GetMapping
    @RequirePermission(PlatformPermissions.OPERATORS_READ)
    public @NotNull ApiResponse<PageData<PlatformPrincipalView>> page(
            @NotNull PlatformOperatorPageRequest request) {
        return ApiResponse.ok(principalService.pagePlatformPrincipals(request.page(), request.size())
                .map(PlatformPrincipalView::from));
    }

    /** 读取一个平台操作员及其平台角色，不返回认证材料。 */
    @GetMapping("/{principalId}")
    @RequirePermission(PlatformPermissions.OPERATORS_READ)
    public @NotNull ApiResponse<PlatformPrincipalView> detail(@PathVariable @NotNull Long principalId) {
        return ApiResponse.ok(PlatformPrincipalView.from(principalService.requirePlatformPrincipal(principalId)));
    }

    /** 从已有 GLOBAL Account 创建无角色、无租户 ACL 的平台操作员。 */
    @PostMapping
    @Idempotent(operation = "platform.operators.create")
    @Audit(module = PlatformAuditOperations.OPERATORS, action = PlatformAuditOperations.CREATE, targetType = "principal", targetId = "#result?.data")
    @RequirePermission(PlatformPermissions.OPERATORS_MANAGE)
    public @NotNull ApiResponse<Long> create(@Valid @RequestBody @NotNull PlatformOperatorCreateRequest request) {
        var account = accountService.require(request.getAccountId());
        if (account.getScopeType() != AccountScopeType.GLOBAL) {
            throw new BizException(IamErrorCode.ACCOUNT_NOT_FOUND);
        }
        return ApiResponse.created(principalService.createDelegatedPlatform(
                account.getSubjectId(), request.getDisplayName()).getId());
    }

    /**
     * 读取目标 PLATFORM Principal 当前可以绑定的平台角色候选。
     *
     * @param principalId PLATFORM Principal 主键
     * @return 当前启用的平台角色候选
     */
    @GetMapping("/{principalId}/assignable-roles")
    @RequirePermission({PlatformPermissions.OPERATORS_READ, PlatformPermissions.ROLES_READ})
    public @NotNull ApiResponse<List<PlatformRoleView>> assignableRoles(
            @PathVariable @NotNull Long principalId) {
        return ApiResponse.ok(principalService.assignablePlatformRoles(principalId).stream()
                .map(PlatformRoleView::from)
                .toList());
    }

    /**
     * 修改 PLATFORM Principal 状态，不改变所属 Subject 的登录状态。
     *
     * @param principalId PLATFORM Principal 主键
     * @param request 目标状态和当前版本
     * @return 没有响应数据的成功结果
     */
    @PatchMapping("/{principalId}")
    @Audit(module = PlatformAuditOperations.OPERATORS, action = PlatformAuditOperations.CHANGE_PLATFORM_PRINCIPAL_STATUS, targetType = "principal", targetId = "#principalId")
    @RequirePermission(PlatformPermissions.OPERATORS_MANAGE)
    public @NotNull ApiResponse<Void> changeStatus(
            @PathVariable @NotNull Long principalId,
            @Valid @RequestBody @NotNull PlatformPrincipalStatusRequest request) {
        principalService.changePlatformStatus(principalId, request.getStatus(), request.getVersion());
        return ApiResponse.ok();
    }

    /**
     * 替换 PLATFORM Principal 的全部平台角色绑定。
     *
     * @param principalId PLATFORM Principal 主键
     * @param request 目标平台角色集合和当前 Principal 版本
     * @return 没有响应数据的成功结果
     */
    @PutMapping("/{principalId}/roles")
    @Audit(module = PlatformAuditOperations.OPERATORS, action = PlatformAuditOperations.REPLACE_PLATFORM_PRINCIPAL_ROLES, targetType = "principal", targetId = "#principalId")
    @RequirePermission({PlatformPermissions.OPERATORS_MANAGE, PlatformPermissions.ROLES_MANAGE})
    public @NotNull ApiResponse<Void> replaceRoles(
            @PathVariable @NotNull Long principalId,
            @Valid @RequestBody @NotNull PlatformPrincipalRolesRequest request) {
        principalService.replacePlatformRoles(principalId, request.getRoleIds(), request.getVersion());
        return ApiResponse.ok();
    }
}
