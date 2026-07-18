package com.sca4d.controllers.mg.tenant;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.iam.principal.TenantPrincipalSelection;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderResourceTypes;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderService;
import com.sca4d.identity.request.IdentityProviderCreateRequest;
import com.sca4d.identity.request.IdentityProviderStatusRequest;
import com.sca4d.identity.request.IdentityProviderUpdateRequest;
import com.sca4d.identity.view.IdentityProviderView;
import com.sca4d.tenant.TenantPermissions;
import com.sca4d.tenant.audit.TenantAuditOperations;
import com.sca4d.tenant.policy.TenantAccessPolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** TENANT Principal 管理当前租户 IdentityProvider 通用字段的 REST 接口。 */
@RestController
@RequestMapping("/mg/tenant/tenants/{tenantId}/auth/providers")
@AuthPrincipal(types = PrincipalType.TENANT, selection = TenantPrincipalSelection.class, tenantId = "#path.tenantId")
@RequiredArgsConstructor
public class TenantIdentityProviderController {

    private final IdentityProviderService service;

    /** @return 当前租户全部未删除 Provider，不包含协议秘密 */
    @GetMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_READ)
    public @NotNull ApiResponse<List<IdentityProviderView>> providers(
            @PathVariable @NotNull Long tenantId) {
        return ApiResponse.ok(service.providers(IdentityProviderScope.tenant(tenantId)).stream()
                .map(IdentityProviderView::from)
                .toList());
    }

    /** @return 当前租户的指定 Provider，不包含协议秘密 */
    @GetMapping("/{providerId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_READ)
    public @NotNull ApiResponse<IdentityProviderView> detail(
            @PathVariable @NotNull Long tenantId, @PathVariable @NotNull Long providerId) {
        IdentityProviderView provider = IdentityProviderView.from(
                service.require(IdentityProviderScope.tenant(tenantId), providerId));
        return ApiResponse.ok(provider, provider.getVersion());
    }

    /** 创建协议类型和 code 不可变、默认停用的租户 Provider。 */
    @PostMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Idempotent(operation = "tenant.identity-providers.create", key = "#idempotencyKey")
    @Audit(module = TenantAuditOperations.IDENTITY_PROVIDERS, action = TenantAuditOperations.CREATE, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#result?.data?.providerId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<IdentityProviderView> create(
            @PathVariable @NotNull Long tenantId,
            @Valid @RequestBody @NotNull IdentityProviderCreateRequest request,
            @RequestHeader("Idempotency-Key") @NotNull String idempotencyKey) {
        IdentityProviderScope scope = IdentityProviderScope.tenant(tenantId);
        Long providerId = service.create(
                scope,
                request.getCode(),
                request.getName(),
                request.getProtocol(),
                request.getRegistrationPolicy(),
                request.isEmailAutoLink());
        IdentityProviderView provider = IdentityProviderView.from(service.require(scope, providerId));
        return ApiResponse.created(provider, provider.getVersion());
    }

    /** 修改租户 Provider 的名称、注册策略和邮箱关联开关。 */
    @PutMapping("/{providerId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Audit(module = TenantAuditOperations.IDENTITY_PROVIDERS, action = TenantAuditOperations.UPDATE, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<IdentityProviderView> update(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long providerId,
            @Valid @RequestBody @NotNull IdentityProviderUpdateRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        IdentityProviderScope scope = IdentityProviderScope.tenant(tenantId);
        service.update(
                scope,
                providerId,
                request.getName(),
                request.getRegistrationPolicy(),
                request.isEmailAutoLink(),
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.PROVIDER_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.PROVIDER_CHANGED));
        IdentityProviderView provider = IdentityProviderView.from(service.require(scope, providerId));
        return ApiResponse.ok(provider, provider.getVersion());
    }

    /** 启用或停用租户 Provider；启用前对应协议配置必须完整。 */
    @PatchMapping("/{providerId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Audit(module = TenantAuditOperations.IDENTITY_PROVIDERS, action = TenantAuditOperations.CHANGE_STATUS, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<IdentityProviderView> changeStatus(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long providerId,
            @Valid @RequestBody @NotNull IdentityProviderStatusRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        IdentityProviderScope scope = IdentityProviderScope.tenant(tenantId);
        service.changeStatus(
                scope,
                providerId,
                request.getStatus(),
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.PROVIDER_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.PROVIDER_CHANGED));
        IdentityProviderView provider = IdentityProviderView.from(service.require(scope, providerId));
        return ApiResponse.ok(provider, provider.getVersion());
    }

    /** 删除已经停用且没有 ExternalIdentity 关联的租户 Provider。 */
    @DeleteMapping("/{providerId}")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Audit(module = TenantAuditOperations.IDENTITY_PROVIDERS, action = TenantAuditOperations.DELETE, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ResponseEntity<Void> delete(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long providerId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        service.delete(
                IdentityProviderScope.tenant(tenantId),
                providerId,
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.PROVIDER_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.PROVIDER_CHANGED));
        return ResponseEntity.noContent().build();
    }
}
