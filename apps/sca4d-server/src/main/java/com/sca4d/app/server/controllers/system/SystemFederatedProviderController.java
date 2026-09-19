package com.sca4d.app.server.controllers.system;

import com.sca4d.app.server.authentication.view.FederatedProviderSettingsView;
import com.sca4d.app.server.authentication.view.FederatedProviderView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.platform.request.CreateProviderRequest;
import com.sca4d.app.server.platform.request.ProviderSettingsRequest;
import com.sca4d.app.server.platform.request.ProviderStatusRequest;
import com.sca4d.app.server.platform.request.ReplaceProviderRequest;
import com.sca4d.auth.federation.FederatedProvider;
import com.sca4d.auth.federation.FederatedProviderService;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditPayload;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.iam.base.rbac.PlatformOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** 提供系统管理员配置 OIDC/OAuth2 联邦登录 Provider 的资源接口。 */
@RestController
@RequestMapping("/system/authentication/federated-providers")
@Tag(name = "System Federated Providers")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class SystemFederatedProviderController {

    private final @NotNull FederatedProviderService providers;

    /** 创建联邦 Provider 管理接口。 */
    public SystemFederatedProviderController(@NotNull FederatedProviderService providers) {
        this.providers = Objects.requireNonNull(providers, "Provider 服务不能为空");
    }

    /** 查询全部未删除 Provider。 */
    @GetMapping
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_READ)
    @Operation(summary = "查询联邦登录 Provider")
    public @NotNull ApiResponse<List<FederatedProviderView>> findAll() {
        return ApiResponse.ok(providers.findAll().stream().map(FederatedProviderView::from).toList());
    }

    /** 查询一个 Provider 的基本信息。 */
    @GetMapping("/{code}")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_READ)
    @Operation(summary = "查询联邦 Provider 详情")
    public @NotNull ApiResponse<FederatedProviderView> find(
            @PathVariable @AuditTarget(resourceType = FederatedProvider.RESOURCE_TYPE) @NotNull String code) {
        FederatedProvider provider = providers.loadByCode(code);
        return ApiResponse.ok(FederatedProviderView.from(provider));
    }

    /** 创建默认停用的 Provider。 */
    @PostMapping
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE)
    @Audit(operation = "system/authentication/federated-provider/create")
    @Operation(summary = "创建联邦 Provider")
    public @NotNull ApiResponse<FederatedProviderView> create(
            @RequestBody @Valid @AuditPayload @NotNull CreateProviderRequest request) {
        FederatedProvider created = providers.create(
                request.getCode(),
                request.getProtocol(),
                request.isLoginPageVisible(),
                request.getDisplayOrder(),
                request.getUnlinkedIdentityAction(),
                request.isAutoLinkVerifiedEmail(),
                request.getI18n(),
                request.getIconReference());
        return ApiResponse.created(
                FederatedProviderView.from(created),
                URI.create("/system/authentication/federated-providers/" + created.code()));
    }

    /** 按 If-Match 替换停用 Provider 的展示和绑定策略。 */
    @PutMapping("/{code}")
    @IfMatch
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE)
    @Audit(operation = "system/authentication/federated-provider/replace")
    @Operation(summary = "替换联邦 Provider")
    public @NotNull ApiResponse<FederatedProviderView> replace(
            @PathVariable @AuditTarget(resourceType = FederatedProvider.RESOURCE_TYPE) @NotNull String code,
            @RequestBody @Valid @AuditPayload @NotNull ReplaceProviderRequest request) {
        FederatedProvider updated = providers.replace(
                code,
                request.isLoginPageVisible(),
                request.getDisplayOrder(),
                request.getUnlinkedIdentityAction(),
                request.isAutoLinkVerifiedEmail(),
                request.getI18n(),
                request.getIconReference());
        return ApiResponse.ok(FederatedProviderView.from(updated));
    }

    /** 查询协议配置摘要，不返回 Client Secret。 */
    @GetMapping("/{code}/settings")
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_READ)
    @Operation(summary = "查询联邦 Provider 协议配置")
    public @NotNull ApiResponse<FederatedProviderSettingsView> settings(
            @PathVariable @AuditTarget(resourceType = FederatedProvider.RESOURCE_TYPE) @NotNull String code) {
        FederatedProvider provider = providers.loadByCode(code);
        return ApiResponse.ok(
                FederatedProviderSettingsView.from(
                        providers.configurationByCode(code),
                        provider.version()));
    }

    /** 按 If-Match 替换停用 Provider 的协议配置；空 Secret 保留原值。 */
    @PutMapping("/{code}/settings")
    @IfMatch
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE)
    @Audit(operation = "system/authentication/federated-provider/settings/replace")
    @Operation(summary = "替换联邦 Provider 协议配置")
    public @NotNull ApiResponse<Void> replaceSettings(
            @PathVariable @AuditTarget(resourceType = FederatedProvider.RESOURCE_TYPE) @NotNull String code,
            @RequestBody @Valid @NotNull ProviderSettingsRequest request) {
        FederatedProvider current = providers.loadByCode(code);
        FederatedProvider updated = providers.replaceConfiguration(
                code,
                request.toConfiguration(current.protocol()));
        return ApiResponse.noContent(updated.version());
    }

    /** 按 If-Match 修改 Provider 状态。 */
    @PatchMapping("/{code}/status")
    @IfMatch
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE)
    @Audit(operation = "system/authentication/federated-provider/status/replace")
    @Operation(summary = "修改联邦 Provider 状态")
    public @NotNull ApiResponse<FederatedProviderView> replaceStatus(
            @PathVariable @AuditTarget(resourceType = FederatedProvider.RESOURCE_TYPE) @NotNull String code,
            @RequestBody @Valid @NotNull ProviderStatusRequest request) {
        FederatedProvider updated = providers.changeStatus(code, request.getStatus());
        return ApiResponse.ok(FederatedProviderView.from(updated));
    }

    /** 按 If-Match 删除停用且没有身份绑定的 Provider。 */
    @DeleteMapping("/{code}")
    @IfMatch
    @RequirePermission(operation = PlatformOperations.ACCOUNTS_MANAGE)
    @Audit(operation = "system/authentication/federated-provider/delete")
    @Operation(summary = "删除联邦 Provider")
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @AuditTarget(resourceType = FederatedProvider.RESOURCE_TYPE) @NotNull String code) {
        providers.delete(code);
        return ApiResponse.noContent();
    }
}
