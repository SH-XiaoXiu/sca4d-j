package com.sca4d.controllers.mg.platform;

import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.platform.PlatformPermissions;
import com.sca4d.platform.audit.PlatformAuditOperations;
import com.sca4d.tenant.TenantAccessService;
import com.sca4d.tenant.policy.TenantAccessGrant;
import com.sca4d.tenant.request.TenantAccessReplaceRequest;
import com.sca4d.tenant.view.PlatformTenantAccessView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLATFORM Principal 的租户访问授权集合资源。 */
@RestController
@RequestMapping("/mg/platform/operators/{principalId}/tenant-access-grants")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class PlatformPrincipalTenantAccessController {

    private final TenantAccessService tenantAccessService;

    /**
     * 读取目标 PLATFORM Principal 的完整租户访问授权集合。
     *
     * @param principalId 目标 PLATFORM Principal 主键
     * @return 默认等级、租户覆盖规则和当前版本
     */
    @GetMapping
    @RequirePermission({PlatformPermissions.TENANT_ACCESS_READ, PlatformPermissions.OPERATORS_READ})
    public @NotNull ApiResponse<PlatformTenantAccessView> configuration(
            @PathVariable @NotNull Long principalId) {
        return ApiResponse.ok(PlatformTenantAccessView.from(
                tenantAccessService.configuration(principalId)));
    }

    /**
     * 原子替换目标 PLATFORM Principal 的完整租户访问授权集合。
     *
     * @param principalId 目标 PLATFORM Principal 主键
     * @param request 默认等级、租户覆盖规则、当前版本和幂等键
     * @param actor 当前执行操作的 PLATFORM Principal
     * @return 没有响应数据的成功结果
     */
    @PutMapping
    @Idempotent(operation = "platform.principal-tenant-access.replace")
    @Audit(module = PlatformAuditOperations.TENANT_ACCESS, action = PlatformAuditOperations.REPLACE, targetType = "principal", targetId = "#principalId")
    @RequirePermission({PlatformPermissions.TENANT_ACCESS_MANAGE, PlatformPermissions.OPERATORS_MANAGE})
    public @NotNull ApiResponse<Void> replace(
            @PathVariable @NotNull Long principalId,
            @Valid @RequestBody @NotNull TenantAccessReplaceRequest request,
            @AuthPrincipal @NotNull Principal actor) {
        tenantAccessService.replace(
                actor.requirePrincipalId(),
                principalId,
                request.getDefaultAction(),
                request.getGrants().stream()
                        .map(grant -> new TenantAccessGrant(grant.getTenantId(), grant.getAccessAction()))
                        .toList(),
                request.getVersion());
        return ApiResponse.ok();
    }
}
