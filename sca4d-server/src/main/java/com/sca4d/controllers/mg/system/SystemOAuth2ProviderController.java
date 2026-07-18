package com.sca4d.controllers.mg.system;

import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderResourceTypes;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.oauth2.OAuth2ProfilePreview;
import com.sca4d.identity.oauth2.OAuth2ProviderConfiguration;
import com.sca4d.identity.oauth2.OAuth2ProviderService;
import com.sca4d.identity.oauth2.request.OAuth2ProfilePreviewRequest;
import com.sca4d.identity.oauth2.request.OAuth2ProviderConfigurationRequest;
import com.sca4d.identity.oauth2.view.OAuth2ProfilePreviewView;
import com.sca4d.identity.oauth2.view.OAuth2ProviderConfigurationView;
import com.sca4d.system.SystemPermissions;
import com.sca4d.system.audit.SystemAuditOperations;
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

/** PLATFORM Principal 管理 GLOBAL Provider 的 OAuth2 协议配置子资源。 */
@RestController
@RequestMapping("/mg/system/auth/providers/{providerId}/oauth2")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class SystemOAuth2ProviderController {

    private final OAuth2ProviderService service;

    /** @return GLOBAL Provider 的 OAuth2 配置，不包含 Client Secret */
    @GetMapping
    @RequirePermission(SystemPermissions.IDENTITY_PROVIDERS_READ)
    public @NotNull ApiResponse<OAuth2ProviderConfigurationView> configuration(
            @PathVariable @NotNull Long providerId) {
        OAuth2ProviderConfiguration configuration = service.configuration(IdentityProviderScope.global(), providerId);
        return ApiResponse.ok(
                OAuth2ProviderConfigurationView.from(configuration), configuration.getVersion());
    }

    /** 使用父 Provider 强 ETag 创建或完整替换 OAuth2 配置。 */
    @PutMapping
    @Audit(module = SystemAuditOperations.IDENTITY_PROVIDERS, action = SystemAuditOperations.UPDATE, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(SystemPermissions.IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<OAuth2ProviderConfigurationView> replace(
            @PathVariable @NotNull Long providerId,
            @Valid @RequestBody @NotNull OAuth2ProviderConfigurationRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        OAuth2ProviderConfiguration configuration = service.replace(
                IdentityProviderScope.global(),
                providerId,
                request.getAuthorizationUri(),
                request.getTokenUri(),
                request.getProfileUri(),
                request.getAuxiliaryProfileUri(),
                request.getClientId(),
                request.getClientSecret(),
                request.getScopes(),
                request.getProfileMapping().toMapping(),
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.PROVIDER_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.PROVIDER_CHANGED));
        return ApiResponse.ok(
                OAuth2ProviderConfigurationView.from(configuration), configuration.getVersion());
    }

    /** 使用当前已保存映射预览示例主 Profile 和辅助 Profile。 */
    @PostMapping("/previews")
    @RequirePermission(SystemPermissions.IDENTITY_PROVIDERS_READ)
    public @NotNull ApiResponse<OAuth2ProfilePreviewView> preview(
            @PathVariable @NotNull Long providerId,
            @Valid @RequestBody @NotNull OAuth2ProfilePreviewRequest request) {
        OAuth2ProfilePreview preview = service.preview(
                IdentityProviderScope.global(),
                providerId,
                request.getProfile(),
                request.getAuxiliaryProfile());
        return ApiResponse.ok(OAuth2ProfilePreviewView.from(preview));
    }

}
