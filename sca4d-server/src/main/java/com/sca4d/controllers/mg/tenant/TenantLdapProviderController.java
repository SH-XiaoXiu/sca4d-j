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
import com.sca4d.identity.ldap.LdapProviderConfiguration;
import com.sca4d.identity.ldap.LdapProviderService;
import com.sca4d.identity.ldap.request.LdapProviderConfigurationRequest;
import com.sca4d.identity.ldap.view.LdapProviderConfigurationView;
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

/** TENANT Principal 管理当前租户 Provider 的 LDAP 协议配置子资源。 */
@RestController
@RequestMapping("/mg/tenant/tenants/{tenantId}/auth/providers/{providerId}/ldap")
@AuthPrincipal(types = PrincipalType.TENANT, selection = TenantPrincipalSelection.class, tenantId = "#path.tenantId")
@RequiredArgsConstructor
public class TenantLdapProviderController {

    private final LdapProviderService service;

    /** @return 当前租户 Provider 的 LDAP 配置，不包含服务账号密码 */
    @GetMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.READ, resourceId = "#tenantId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_READ)
    public @NotNull ApiResponse<LdapProviderConfigurationView> configuration(
            @PathVariable @NotNull Long tenantId, @PathVariable @NotNull Long providerId) {
        LdapProviderConfiguration configuration = service.configuration(IdentityProviderScope.tenant(tenantId), providerId);
        return ApiResponse.ok(
                LdapProviderConfigurationView.from(configuration), configuration.getVersion());
    }

    /** 使用父 Provider 强 ETag 创建或完整替换当前租户的 LDAP 配置。 */
    @PutMapping
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Audit(module = TenantAuditOperations.IDENTITY_PROVIDERS, action = TenantAuditOperations.UPDATE, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<LdapProviderConfigurationView> replace(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long providerId,
            @Valid @RequestBody @NotNull LdapProviderConfigurationRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        LdapProviderConfiguration configuration = service.replace(
                IdentityProviderScope.tenant(tenantId),
                providerId,
                request.getUrl(),
                request.getBaseDn(),
                request.getServiceAccountDn(),
                request.getServiceAccountPassword(),
                request.getUserSearchBase(),
                request.getUserSearchFilter(),
                request.getExternalSubjectAttribute(),
                request.getDisplayNameAttribute(),
                request.getEmailAttribute(),
                request.getTransportSecurity(),
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.PROVIDER_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.PROVIDER_CHANGED));
        return ApiResponse.ok(
                LdapProviderConfigurationView.from(configuration), configuration.getVersion());
    }

    /** 验证当前已保存 LDAP 配置可以连接目录。 */
    @PostMapping("/tests")
    @Access(policy = TenantAccessPolicy.class, action = AccessAction.MANAGE, resourceId = "#tenantId")
    @Audit(module = TenantAuditOperations.IDENTITY_PROVIDERS, action = TenantAuditOperations.TEST_CONNECTION, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(TenantPermissions.TENANT_IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<Void> testConnection(
            @PathVariable @NotNull Long tenantId,
            @PathVariable @NotNull Long providerId) {
        service.testConnection(IdentityProviderScope.tenant(tenantId), providerId);
        return ApiResponse.ok();
    }

}
