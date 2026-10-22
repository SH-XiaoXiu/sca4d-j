package com.sca4d.app.server.controllers.system;

import com.sca4d.app.server.audit.SystemOperations;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.system.request.EmailResourceStatusRequest;
import com.sca4d.app.server.system.request.SmsBindingRequest;
import com.sca4d.app.server.system.request.SmsProviderRequest;
import com.sca4d.app.server.system.view.SmsBindingView;
import com.sca4d.app.server.system.view.SmsProviderView;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.notification.sms.SmsConfigurationService;
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

/** 管理 SMS Provider 实例和供应商模板绑定，不接收或返回 AccessKey。 */
@RestController
@RequestMapping("/system/sms/providers")
@Tag(name = "SMS Notifications")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class SmsProviderController {

    private static final String BINDING_RESOURCE = "notification:sms:binding";

    private static final String PROVIDER_RESOURCE = "notification:sms:provider";

    private final SmsConfigurationService sms;

    /** 创建 SMS Provider 管理控制器。 */
    public SmsProviderController(@NotNull SmsConfigurationService sms) {
        this.sms = Objects.requireNonNull(sms, "SMS 配置服务不能为空");
    }

    /** @return 全部未删除 SMS Provider */
    @GetMapping
    @RequirePermission(operation = SystemOperations.SMS_READ_VALUE)
    public ApiResponse<List<SmsProviderView>> providers() {
        return ApiResponse.ok(sms.providers().stream().map(SmsProviderView::from).toList());
    }

    /** 创建默认停用 Provider。 */
    @PostMapping
    @RequirePermission(operation = SystemOperations.SMS_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/SMS/PROVIDER/CREATE")
    public ApiResponse<SmsProviderView> createProvider(@RequestBody @Valid SmsProviderRequest request) {
        var created = sms.createProvider(
                request.getCode(),
                request.getType(),
                request.getCredentialRef(),
                request.getPriority());
        return ApiResponse
                .created(SmsProviderView.from(created), URI.create("/system/sms/providers/" + created.providerId()));
    }

    /** 读取 Provider 详情并发布 ETag。 */
    @GetMapping("/{providerId}")
    @RequirePermission(operation = SystemOperations.SMS_READ_VALUE)
    public ApiResponse<SmsProviderView> provider(@PathVariable long providerId) {
        return ApiResponse.ok(SmsProviderView.from(sms.provider(providerId)));
    }

    /** 按 `If-Match` 完整替换停用 Provider。 */
    @PutMapping("/{providerId}")
    @IfMatch
    @RequirePermission(operation = SystemOperations.SMS_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/SMS/PROVIDER/REPLACE")
    public ApiResponse<SmsProviderView> replaceProvider(
            @PathVariable @AuditTarget(resourceType = PROVIDER_RESOURCE) long providerId,
            @RequestBody @Valid SmsProviderRequest request) {
        return ApiResponse.ok(
                SmsProviderView.from(
                        sms.replaceProvider(
                                providerId,
                                request.getCode(),
                                request.getType(),
                                request.getCredentialRef(),
                                request.getPriority())));
    }

    /** 按 `If-Match` 修改 Provider 状态。 */
    @PatchMapping("/{providerId}/status")
    @IfMatch
    @RequirePermission(operation = SystemOperations.SMS_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/SMS/PROVIDER/STATUS/CHANGE")
    public ApiResponse<SmsProviderView> setProviderEnabled(
            @PathVariable @AuditTarget(resourceType = PROVIDER_RESOURCE) long providerId,
            @RequestBody @Valid EmailResourceStatusRequest request) {
        return ApiResponse.ok(SmsProviderView.from(sms.setProviderEnabled(providerId, request.isEnabled())));
    }

    /** 按 `If-Match` 删除停用且没有绑定的 Provider。 */
    @DeleteMapping("/{providerId}")
    @IfMatch
    @RequirePermission(operation = SystemOperations.SMS_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/SMS/PROVIDER/DELETE")
    public ApiResponse<Void> deleteProvider(
            @PathVariable @AuditTarget(resourceType = PROVIDER_RESOURCE) long providerId) {
        sms.deleteProvider(providerId);
        return ApiResponse.noContent();
    }

    /** @return Provider 的全部未删除模板绑定 */
    @GetMapping("/{providerId}/bindings")
    @RequirePermission(operation = SystemOperations.SMS_READ_VALUE)
    public ApiResponse<List<SmsBindingView>> bindings(@PathVariable long providerId) {
        return ApiResponse.ok(sms.bindings(providerId).stream().map(SmsBindingView::from).toList());
    }

    /** 创建默认停用的 Provider 模板绑定。 */
    @PostMapping("/{providerId}/bindings")
    @RequirePermission(operation = SystemOperations.SMS_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/SMS/BINDING/CREATE")
    public ApiResponse<SmsBindingView> createBinding(
            @PathVariable @AuditTarget(resourceType = PROVIDER_RESOURCE) long providerId,
            @RequestBody @Valid SmsBindingRequest request) {
        var created = sms.createBinding(
                providerId,
                request.getNotificationCode(),
                request.getRoute(),
                request.getLanguageTag(),
                request.getSignName(),
                request.getProviderTemplateCode(),
                request.getParameterMapping());
        return ApiResponse.created(
                SmsBindingView.from(created),
                URI.create("/system/sms/providers/" + providerId + "/bindings/" + created.bindingId()));
    }

    /** 读取 Provider 模板绑定详情并发布 ETag。 */
    @GetMapping("/{providerId}/bindings/{bindingId}")
    @RequirePermission(operation = SystemOperations.SMS_READ_VALUE)
    public ApiResponse<SmsBindingView> binding(@PathVariable long providerId, @PathVariable long bindingId) {
        return ApiResponse.ok(SmsBindingView.from(sms.binding(providerId, bindingId)));
    }

    /** 按 `If-Match` 完整替换停用绑定。 */
    @PutMapping("/{providerId}/bindings/{bindingId}")
    @IfMatch
    @RequirePermission(operation = SystemOperations.SMS_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/SMS/BINDING/REPLACE")
    public ApiResponse<SmsBindingView> replaceBinding(
            @PathVariable long providerId,
            @PathVariable @AuditTarget(resourceType = BINDING_RESOURCE) long bindingId,
            @RequestBody @Valid SmsBindingRequest request) {
        return ApiResponse.ok(
                SmsBindingView.from(
                        sms.replaceBinding(
                                providerId,
                                bindingId,
                                request.getNotificationCode(),
                                request.getRoute(),
                                request.getLanguageTag(),
                                request.getSignName(),
                                request.getProviderTemplateCode(),
                                request.getParameterMapping())));
    }

    /** 按 `If-Match` 修改绑定状态。 */
    @PatchMapping("/{providerId}/bindings/{bindingId}/status")
    @IfMatch
    @RequirePermission(operation = SystemOperations.SMS_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/SMS/BINDING/STATUS/CHANGE")
    public ApiResponse<SmsBindingView> setBindingEnabled(
            @PathVariable long providerId,
            @PathVariable @AuditTarget(resourceType = BINDING_RESOURCE) long bindingId,
            @RequestBody @Valid EmailResourceStatusRequest request) {
        return ApiResponse.ok(SmsBindingView.from(sms.setBindingEnabled(providerId, bindingId, request.isEnabled())));
    }

    /** 按 `If-Match` 删除停用绑定。 */
    @DeleteMapping("/{providerId}/bindings/{bindingId}")
    @IfMatch
    @RequirePermission(operation = SystemOperations.SMS_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/SMS/BINDING/DELETE")
    public ApiResponse<Void> deleteBinding(
            @PathVariable long providerId,
            @PathVariable @AuditTarget(resourceType = BINDING_RESOURCE) long bindingId) {
        sms.deleteBinding(providerId, bindingId);
        return ApiResponse.noContent();
    }
}
