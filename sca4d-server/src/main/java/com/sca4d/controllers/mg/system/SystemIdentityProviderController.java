package com.sca4d.controllers.mg.system;

import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.identity.IdentityProviderErrorCode;
import com.sca4d.identity.IdentityProviderResourceTypes;
import com.sca4d.identity.IdentityProviderScope;
import com.sca4d.identity.IdentityProviderService;
import com.sca4d.identity.request.IdentityProviderCreateRequest;
import com.sca4d.identity.request.IdentityProviderStatusRequest;
import com.sca4d.identity.request.IdentityProviderUpdateRequest;
import com.sca4d.identity.view.IdentityProviderView;
import com.sca4d.system.SystemPermissions;
import com.sca4d.system.audit.SystemAuditOperations;
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

/** PLATFORM Principal 管理 GLOBAL IdentityProvider 通用字段的 REST 接口。 */
@RestController
@RequestMapping("/mg/system/auth/providers")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class SystemIdentityProviderController {

    private final IdentityProviderService service;

    /** @return 全部未删除 GLOBAL Provider，不包含协议秘密 */
    @GetMapping
    @RequirePermission(SystemPermissions.IDENTITY_PROVIDERS_READ)
    public @NotNull ApiResponse<List<IdentityProviderView>> providers() {
        return ApiResponse.ok(service.providers(IdentityProviderScope.global()).stream()
                .map(IdentityProviderView::from)
                .toList());
    }

    /** @return 指定 GLOBAL Provider，不包含协议秘密 */
    @GetMapping("/{providerId}")
    @RequirePermission(SystemPermissions.IDENTITY_PROVIDERS_READ)
    public @NotNull ApiResponse<IdentityProviderView> detail(
            @PathVariable @NotNull Long providerId) {
        IdentityProviderView provider = IdentityProviderView.from(
                service.require(IdentityProviderScope.global(), providerId));
        return ApiResponse.ok(provider, provider.getVersion());
    }

    /** 创建协议类型和 code 不可变、默认停用的 GLOBAL Provider。 */
    @PostMapping
    @Idempotent(operation = "system.identity-providers.create", key = "#idempotencyKey")
    @Audit(module = SystemAuditOperations.IDENTITY_PROVIDERS, action = SystemAuditOperations.CREATE, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#result?.data?.providerId")
    @RequirePermission(SystemPermissions.IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<IdentityProviderView> create(
            @Valid @RequestBody @NotNull IdentityProviderCreateRequest request,
            @RequestHeader("Idempotency-Key") @NotNull String idempotencyKey) {
        Long providerId = service.create(
                IdentityProviderScope.global(),
                request.getCode(),
                request.getName(),
                request.getProtocol(),
                request.getRegistrationPolicy(),
                request.isEmailAutoLink());
        IdentityProviderView provider = IdentityProviderView.from(
                service.require(IdentityProviderScope.global(), providerId));
        return ApiResponse.created(provider, provider.getVersion());
    }

    /** 修改 GLOBAL Provider 的名称、注册策略和邮箱关联开关。 */
    @PutMapping("/{providerId}")
    @Audit(module = SystemAuditOperations.IDENTITY_PROVIDERS, action = SystemAuditOperations.UPDATE, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(SystemPermissions.IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<IdentityProviderView> update(
            @PathVariable @NotNull Long providerId,
            @Valid @RequestBody @NotNull IdentityProviderUpdateRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        service.update(
                IdentityProviderScope.global(),
                providerId,
                request.getName(),
                request.getRegistrationPolicy(),
                request.isEmailAutoLink(),
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.PROVIDER_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.PROVIDER_CHANGED));
        IdentityProviderView provider = IdentityProviderView.from(
                service.require(IdentityProviderScope.global(), providerId));
        return ApiResponse.ok(provider, provider.getVersion());
    }

    /** 启用或停用 GLOBAL Provider；启用前对应协议配置必须完整。 */
    @PatchMapping("/{providerId}")
    @Audit(module = SystemAuditOperations.IDENTITY_PROVIDERS, action = SystemAuditOperations.CHANGE_STATUS, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(SystemPermissions.IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ApiResponse<IdentityProviderView> changeStatus(
            @PathVariable @NotNull Long providerId,
            @Valid @RequestBody @NotNull IdentityProviderStatusRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        service.changeStatus(
                IdentityProviderScope.global(),
                providerId,
                request.getStatus(),
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.PROVIDER_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.PROVIDER_CHANGED));
        IdentityProviderView provider = IdentityProviderView.from(
                service.require(IdentityProviderScope.global(), providerId));
        return ApiResponse.ok(provider, provider.getVersion());
    }

    /** 删除已经停用且没有 ExternalIdentity 关联的 GLOBAL Provider。 */
    @DeleteMapping("/{providerId}")
    @Audit(module = SystemAuditOperations.IDENTITY_PROVIDERS, action = SystemAuditOperations.DELETE, targetType = IdentityProviderResourceTypes.IDENTITY_PROVIDER, targetId = "#providerId")
    @RequirePermission(SystemPermissions.IDENTITY_PROVIDERS_MANAGE)
    public @NotNull ResponseEntity<Void> delete(
            @PathVariable @NotNull Long providerId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        service.delete(
                IdentityProviderScope.global(),
                providerId,
                IfMatchVersion.require(
                        ifMatch,
                        IdentityProviderErrorCode.PROVIDER_IF_MATCH_REQUIRED,
                        IdentityProviderErrorCode.PROVIDER_CHANGED));
        return ResponseEntity.noContent().build();
    }
}
