package com.sca4d.app.server.controllers.system;

import com.sca4d.app.server.audit.SystemOperations;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.system.EmailTemplateManagementService;
import com.sca4d.app.server.system.request.EmailResourceStatusRequest;
import com.sca4d.app.server.system.request.EmailTemplateRequest;
import com.sca4d.app.server.system.request.EmailTemplateSourceRequest;
import com.sca4d.app.server.system.request.PreviewEmailTemplateRequest;
import com.sca4d.app.server.system.view.EmailTemplateView;
import com.sca4d.app.server.system.view.RenderedEmailView;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.Sort;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.core.spring.web.list.ListDefinition;
import com.sca4d.core.spring.web.list.ListParameters;
import com.sca4d.notification.email.EmailTemplate;
import com.sca4d.notification.email.EmailTemplateSource;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 管理多语言 Email 模板，并提供草稿预览和真实测试发送。 */
@RestController
@RequestMapping("/system/email/templates")
@Tag(name = "Email Notifications")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class EmailTemplateController {

    private static final Sort NOTIFICATION_CODE = Sort.of("NOTIFICATION_CODE");

    private static final ListDefinition LIST = ListDefinition.builder()
            .fields(NOTIFICATION_CODE, Sort.SERIAL)
            .defaultSort(NOTIFICATION_CODE.asc(), Sort.SERIAL.asc())
            .maxSize(100)
            .build();

    private final @NotNull EmailTemplateManagementService templates;

    /** @param templates Email 模板管理、预览和测试发送服务 */
    public EmailTemplateController(@NotNull EmailTemplateManagementService templates) {
        this.templates = Objects.requireNonNull(templates, "Email Template 服务不能为空");
    }

    /** @return 按分页和安全排序要求读取的模板以及模板变量集合 */
    @GetMapping
    @Operation(summary = "查询 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_READ_VALUE)
    public @NotNull ApiResponse<Page<EmailTemplateView>> find(@NotNull ListParameters parameters) {
        return ApiResponse.ok(templates.find(parameters.resolve(LIST)).map(this::view));
    }

    /** 创建默认停用的完整多语言 Email 模板。 */
    @PostMapping
    @Operation(summary = "创建 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/EMAIL/TEMPLATE/CREATE")
    public @NotNull ApiResponse<EmailTemplateView> create(
            @RequestBody @Valid @NotNull EmailTemplateRequest request) {
        EmailTemplate created = templates.create(
                TreeCode.of(request.getNotificationCode()),
                sources(request.getLocales()));
        return ApiResponse.created(
                view(created),
                URI.create("/system/email/templates/" + created.templateId()));
    }

    /** 读取一个完整 Email 模板，并发布 ETag。 */
    @GetMapping("/{templateId}")
    @Operation(summary = "查询 Email 模板详情")
    @RequirePermission(operation = SystemOperations.EMAIL_READ_VALUE)
    public @NotNull ApiResponse<EmailTemplateView> get(@PathVariable long templateId) {
        EmailTemplate template = templates.loadById(templateId);
        return ApiResponse.ok(view(template));
    }

    /** 按 `If-Match` 完整替换通知编码和全部 Locale 字段。 */
    @PutMapping("/{templateId}")
    @IfMatch
    @Operation(summary = "替换 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/EMAIL/TEMPLATE/REPLACE")
    public @NotNull ApiResponse<EmailTemplateView> replace(
            @PathVariable @AuditTarget(resourceType = EmailTemplate.RESOURCE_TYPE_VALUE) long templateId,
            @RequestBody @Valid @NotNull EmailTemplateRequest request) {
        EmailTemplate updated = templates.replace(
                templateId,
                TreeCode.of(request.getNotificationCode()),
                sources(request.getLocales()));
        return ApiResponse.ok(view(updated));
    }

    /** 按 `If-Match` 启用或停用模板。 */
    @PatchMapping("/{templateId}/status")
    @IfMatch
    @Operation(summary = "修改 Email 模板状态")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/EMAIL/TEMPLATE/STATUS/CHANGE")
    public @NotNull ApiResponse<EmailTemplateView> setEnabled(
            @PathVariable @AuditTarget(resourceType = EmailTemplate.RESOURCE_TYPE_VALUE) long templateId,
            @RequestBody @Valid @NotNull EmailResourceStatusRequest request) {
        EmailTemplate updated = templates.setEnabled(templateId, request.isEnabled());
        return ApiResponse.ok(view(updated));
    }

    /** 渲染未保存草稿，并返回完成主题检查与 HTML 清洗的内容。 */
    @PostMapping("/previews")
    @Operation(summary = "预览 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    public @NotNull ApiResponse<RenderedEmailView> preview(
            @RequestBody @Valid @NotNull PreviewEmailTemplateRequest request) {
        return ApiResponse.ok(
                RenderedEmailView.from(
                        templates.preview(
                                request.getSource().source(),
                                request.locale(),
                                request.variables())));
    }

    /** 按 `If-Match` 删除停用模板。 */
    @DeleteMapping("/{templateId}")
    @IfMatch
    @Operation(summary = "删除 Email 模板")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/EMAIL/TEMPLATE/DELETE")
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @AuditTarget(resourceType = EmailTemplate.RESOURCE_TYPE_VALUE) long templateId) {
        templates.delete(templateId);
        return ApiResponse.noContent();
    }

    /** 组合模板持久化结果和库无关解析器发现的变量集合。 */
    private @NotNull EmailTemplateView view(@NotNull EmailTemplate template) {
        EmailTemplate checked = Objects.requireNonNull(template, "Email 模板不能为空");
        return EmailTemplateView.from(checked, templates.variables(checked.locales()));
    }

    private static @NotNull Map<String, EmailTemplateSource> sources(
            @NotNull Map<String, EmailTemplateSourceRequest> requests) {
        Map<String, EmailTemplateSource> result = new LinkedHashMap<>();
        requests.forEach((locale, source) -> result.put(locale, source.source()));
        return Map.copyOf(result);
    }
}
