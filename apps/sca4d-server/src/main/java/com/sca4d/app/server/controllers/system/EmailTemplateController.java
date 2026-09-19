package com.sca4d.app.server.controllers.system;

import com.sca4d.app.server.audit.SystemOperations;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.system.request.EmailResourceStatusRequest;
import com.sca4d.app.server.system.request.EmailTemplateRequest;
import com.sca4d.app.server.system.request.PreviewEmailTemplateRequest;
import com.sca4d.app.server.system.view.EmailTemplateView;
import com.sca4d.app.server.system.view.RenderedEmailView;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.notification.template.NotificationTemplate;
import com.sca4d.notification.template.NotificationTemplateService;
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

/** 管理多语言 Email 模板，并提供草稿预览和真实测试发送。 */
@RestController
@RequestMapping("/system/email/templates")
@Tag(name = "Email Notifications")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class EmailTemplateController {

    private static final String RESOURCE_TYPE = "notification:email:template";

    private final @NotNull NotificationTemplateService templates;

    /** @param templates Email 模板管理、预览和测试发送服务 */
    public EmailTemplateController(@NotNull NotificationTemplateService templates) {
        this.templates = Objects.requireNonNull(templates, "Email Template 服务不能为空");
    }

    /** @return 全部未删除模板及其变量集合 */
    @GetMapping
    @Operation(summary = "查询 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_READ)
    public @NotNull ApiResponse<List<EmailTemplateView>> findAll() {
        return ApiResponse.ok(templates.findAll().stream().map(this::view).toList());
    }

    /** 创建默认停用的完整多语言 Email 模板。 */
    @PostMapping
    @Operation(summary = "创建 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE)
    @Audit(operation = "system/email/template/create")
    public @NotNull ApiResponse<EmailTemplateView> create(
            @RequestBody @Valid @NotNull EmailTemplateRequest request) {
        NotificationTemplate created = templates.create(
                new NotificationCode(request.getNotificationCode()),
                request.getContent());
        return ApiResponse.created(
                view(created),
                URI.create("/system/email/templates/" + created.templateId()));
    }

    /** 读取一个完整 Email 模板，并发布 ETag。 */
    @GetMapping("/{templateId}")
    @Operation(summary = "查询 Email 模板详情")
    @RequirePermission(operation = SystemOperations.EMAIL_READ)
    public @NotNull ApiResponse<EmailTemplateView> get(@PathVariable long templateId) {
        NotificationTemplate template = templates.loadById(templateId);
        return ApiResponse.ok(view(template));
    }

    /** 按 `If-Match` 完整替换通知编码和全部 Locale 字段。 */
    @PutMapping("/{templateId}")
    @IfMatch
    @Operation(summary = "替换 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE)
    @Audit(operation = "system/email/template/replace")
    public @NotNull ApiResponse<EmailTemplateView> replace(
            @PathVariable @AuditTarget(resourceType = RESOURCE_TYPE) long templateId,
            @RequestBody @Valid @NotNull EmailTemplateRequest request) {
        NotificationTemplate updated = templates.replace(
                templateId,
                new NotificationCode(request.getNotificationCode()),
                request.getContent());
        return ApiResponse.ok(view(updated));
    }

    /** 按 `If-Match` 启用或停用模板。 */
    @PatchMapping("/{templateId}/status")
    @IfMatch
    @Operation(summary = "修改 Email 模板状态")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE)
    @Audit(operation = "system/email/template/status/change")
    public @NotNull ApiResponse<EmailTemplateView> setEnabled(
            @PathVariable @AuditTarget(resourceType = RESOURCE_TYPE) long templateId,
            @RequestBody @Valid @NotNull EmailResourceStatusRequest request) {
        NotificationTemplate updated = templates.setEnabled(templateId, request.isEnabled());
        return ApiResponse.ok(view(updated));
    }

    /** 渲染未保存草稿，并返回完成主题检查与 HTML 清洗的内容。 */
    @PostMapping("/previews")
    @Operation(summary = "预览 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE)
    public @NotNull ApiResponse<RenderedEmailView> preview(
            @RequestBody @Valid @NotNull PreviewEmailTemplateRequest request) {
        return ApiResponse.ok(
                RenderedEmailView.from(
                        templates.preview(
                                request.getContent(),
                                request.locale(),
                                request.variables())));
    }

    /** 按 `If-Match` 删除停用模板。 */
    @DeleteMapping("/{templateId}")
    @IfMatch
    @Operation(summary = "删除 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE)
    @Audit(operation = "system/email/template/delete")
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @AuditTarget(resourceType = RESOURCE_TYPE) long templateId) {
        templates.delete(templateId);
        return ApiResponse.noContent();
    }

    /** 组合模板持久化结果和库无关解析器发现的变量集合。 */
    private @NotNull EmailTemplateView view(@NotNull NotificationTemplate template) {
        NotificationTemplate checked = Objects.requireNonNull(template, "通知模板不能为空");
        return EmailTemplateView.from(checked, templates.variables(checked.content()));
    }
}
