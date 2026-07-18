package com.sca4d.controllers.mg.tenant;

import com.sca4d.common.access.AccessAction;
import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.permission.annotation.Access;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.iam.principal.TenantPrincipalSelection;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderResourceTypes;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.oidc.OidcDiscovery;
import com.sca4d.identity.oidc.OidcProviderConfiguration;
import com.sca4d.identity.oidc.OidcProviderService;
import com.sca4d.identity.oidc.request.OidcProviderConfigurationRequest;
import com.sca4d.identity.oidc.view.OidcDiscoveryView;
import com.sca4d.identity.oidc.view.OidcProviderConfigurationView;
import com.sca4d.tenant.TenantPermissions;
import com.sca4d.tenant.audit.TenantAuditOperations;
import com.sca4d.tenant.policy.TenantAccessPolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TENANT Principal 管理当前租户 Provider 的 OIDC 协议配置子资源。 */
@RestController
@RequestMapping("/mg/tenant/tenants/{tenantId}/auth/providers/{providerId}/oidc")
@AuthPrincipal(types = PrincipalType.TENANT, selection = TenantPrincipalSelection.class, tenantId = "#path.tenantId")
@RequiredArgsConstructor
public class TenantOidcProviderController {

    private final OidcProviderService service;

    /** @return 当前租户 Provider 的 OIDC 配置，不包含 Client Secret */
    @GetMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_READ)
    public @NotNull ApiResponse<OidcProviderConfigurationView> configuration(
            @PathVariable @NotNull Long tenantId, @PathVariable @NotNull Long providerId) {
        OidcProviderConfiguration configuration = service.configuration(IdentityProviderScope.tenant(tenantId), providerId);
        return ApiResponse.ok(
                OidcProviderConfigurationView.from(configuration), configuration.getVersion());
    }

    /** 从已保存 Issuer 读取并校验一次当前租户的 OIDC Discovery 文档。 */
    @PostMapping("/discoveries")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Audit(module = TenantAuditOperations.IDENTITY_PROVIDERS, action = TenantAuditOperations.TEST_CONNECTION, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<OidcDiscoveryView> discover(
            @PathVariable @NotNull Long tenantId, @PathVariable @NotNull Long providerId) {
        OidcDiscovery discovery = service.discover(IdentityProviderScope.tenant(tenantId), providerId);
        return ApiResponse.ok(OidcDiscoveryView.from(discovery));
    }

    /** 使用父 Provider 强 ETag 创建或完整替换当前租户的 OIDC 配置。 */
    @PutMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Audit(module = TenantAuditOperations.IDENTITY_PROVIDERS, action = TenantAuditOperations.UPDATE, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<OidcProviderConfigurationView> replace(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long providerId,
            @Valid @RequestBody @NotNull OidcProviderConfigurationRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        OidcProviderConfiguration configuration = service.replace(
                IdentityProviderScope.tenant(tenantId),
                providerId,
                request.getIssuer(),
                request.getClientId(),
                request.getClientSecret(),
                request.getScopes(),
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.PROVIDER_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.PROVIDER_CHANGED));
        return ApiResponse.ok(
                OidcProviderConfigurationView.from(configuration), configuration.getVersion());
    }
}
