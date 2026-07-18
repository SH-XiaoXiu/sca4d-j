package com.sca4d.controllers.mg.system;

import com.sca4d.common.audit.Audit;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.concurrent.idempotency.Idempotent;
import com.sca4d.common.permission.annotation.RequirePermission;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.common.web.IfMatchVersion;
import com.sca4d.iam.principal.PlatformPrincipalSelection;
import com.sca4d.mail.MailErrorCode;
import com.sca4d.mail.MailResourceTypes;
import com.sca4d.mail.MailTemplate;
import com.sca4d.mail.MailTemplateService;
import com.sca4d.mail.request.MailTemplateCreateRequest;
import com.sca4d.mail.request.MailTemplatePreviewRequest;
import com.sca4d.mail.request.MailTemplateStatusRequest;
import com.sca4d.mail.request.MailTemplateTestSendRequest;
import com.sca4d.mail.request.MailTemplateUpdateRequest;
import com.sca4d.mail.view.MailTemplateView;
import com.sca4d.mail.view.RenderedMailView;
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

/** 平台系统管理端的邮件模板、预览和测试发送接口。 */
@RestController
@RequestMapping("/mg/system")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class SystemMailTemplateController {

    private final MailTemplateService templateService;

    /** @return 全部未删除邮件模板及其变量名 */
    @GetMapping("/mail-templates")
    @RequirePermission(SystemPermissions.MAIL_TEMPLATES_READ)
    public @NotNull ApiResponse<List<MailTemplateView>> templates() {
        return ApiResponse.ok(templateService.templates().stream()
                .map(template -> MailTemplateView.from(template, templateService.variables(template)))
                .toList());
    }

    /** @return 指定邮件模板及其变量名 */
    @GetMapping("/mail-templates/{templateId}")
    @RequirePermission(SystemPermissions.MAIL_TEMPLATES_READ)
    public @NotNull ApiResponse<MailTemplateView> detail(
            @PathVariable @NotNull Long templateId) {
        MailTemplate current = templateService.require(templateId);
        MailTemplateView template = MailTemplateView.from(current, templateService.variables(current));
        return ApiResponse.ok(template, template.getVersion());
    }

    /** @return 新邮件模板主键 */
    @PostMapping("/mail-templates")
    @Idempotent(operation = "system.mail-templates.create", key = "#idempotencyKey")
    @Audit(module = SystemAuditOperations.MAIL_TEMPLATES, action = SystemAuditOperations.CREATE, targetType = MailResourceTypes.MAIL_TEMPLATE, targetId = "#result?.data?.templateId")
    @RequirePermission(SystemPermissions.MAIL_TEMPLATES_MANAGE)
    public @NotNull ApiResponse<MailTemplateView> create(
            @Valid @RequestBody @NotNull MailTemplateCreateRequest request,
            @RequestHeader("Idempotency-Key") @NotNull String idempotencyKey) {
        Long templateId = templateService.create(
                request.getFamily(),
                request.getLocale(),
                request.getSubjectTemplate(),
                request.getTextTemplate(),
                request.getHtmlTemplate());
        MailTemplate current = templateService.require(templateId);
        MailTemplateView template = MailTemplateView.from(current, templateService.variables(current));
        return ApiResponse.created(template, template.getVersion());
    }

    /** 修改邮件模板标识和内容。 */
    @PutMapping("/mail-templates/{templateId}")
    @Audit(module = SystemAuditOperations.MAIL_TEMPLATES, action = SystemAuditOperations.UPDATE, targetType = MailResourceTypes.MAIL_TEMPLATE, targetId = "#templateId")
    @RequirePermission(SystemPermissions.MAIL_TEMPLATES_MANAGE)
    public @NotNull ApiResponse<MailTemplateView> update(
            @PathVariable @NotNull Long templateId,
            @Valid @RequestBody @NotNull MailTemplateUpdateRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        templateService.update(
                templateId,
                request.getFamily(),
                request.getLocale(),
                request.getSubjectTemplate(),
                request.getTextTemplate(),
                request.getHtmlTemplate(),
                IfMatchVersion.require(
                        ifMatch, MailErrorCode.TEMPLATE_IF_MATCH_REQUIRED, MailErrorCode.TEMPLATE_CHANGED));
        MailTemplate current = templateService.require(templateId);
        MailTemplateView template = MailTemplateView.from(current, templateService.variables(current));
        return ApiResponse.ok(template, template.getVersion());
    }

    /** 启用或停用邮件模板。 */
    @PatchMapping("/mail-templates/{templateId}")
    @Audit(module = SystemAuditOperations.MAIL_TEMPLATES, action = SystemAuditOperations.CHANGE_STATUS, targetType = MailResourceTypes.MAIL_TEMPLATE, targetId = "#templateId")
    @RequirePermission(SystemPermissions.MAIL_TEMPLATES_MANAGE)
    public @NotNull ApiResponse<MailTemplateView> changeStatus(
            @PathVariable @NotNull Long templateId,
            @Valid @RequestBody @NotNull MailTemplateStatusRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        templateService.changeStatus(
                templateId,
                request.getStatus(),
                IfMatchVersion.require(
                        ifMatch, MailErrorCode.TEMPLATE_IF_MATCH_REQUIRED, MailErrorCode.TEMPLATE_CHANGED));
        MailTemplate current = templateService.require(templateId);
        MailTemplateView template = MailTemplateView.from(current, templateService.variables(current));
        return ApiResponse.ok(template, template.getVersion());
    }

    /** 渲染未保存草稿，返回已清洗的安全 HTML。 */
    @PostMapping("/mail-template-previews")
    @RequirePermission(SystemPermissions.MAIL_TEMPLATES_MANAGE)
    public @NotNull ApiResponse<RenderedMailView> preview(
            @Valid @RequestBody @NotNull MailTemplatePreviewRequest request) {
        return ApiResponse.ok(RenderedMailView.from(templateService.preview(
                request.getSubjectTemplate(),
                request.getTextTemplate(),
                request.getHtmlTemplate(),
                request.getVariables())));
    }

    /** 使用已保存内容和指定 SMTP 配置发送一封测试邮件。 */
    @PostMapping("/mail-templates/{templateId}/test-messages")
    @Audit(module = SystemAuditOperations.MAIL_TEMPLATES, action = SystemAuditOperations.TEST_SEND, targetType = MailResourceTypes.MAIL_TEMPLATE, targetId = "#templateId")
    @RequirePermission(SystemPermissions.MAIL_TEMPLATES_MANAGE)
    public @NotNull ResponseEntity<Void> sendTest(
            @PathVariable @NotNull Long templateId,
            @Valid @RequestBody @NotNull MailTemplateTestSendRequest request) {
        templateService.sendTest(
                templateId,
                request.getSenderId(),
                request.getRecipient(),
                request.getVariables());
        return ResponseEntity.noContent().build();
    }

    /** 删除已经停用的邮件模板。 */
    @DeleteMapping("/mail-templates/{templateId}")
    @Audit(module = SystemAuditOperations.MAIL_TEMPLATES, action = SystemAuditOperations.DELETE, targetType = MailResourceTypes.MAIL_TEMPLATE, targetId = "#templateId")
    @RequirePermission(SystemPermissions.MAIL_TEMPLATES_MANAGE)
    public @NotNull ResponseEntity<Void> delete(
            @PathVariable @NotNull Long templateId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        templateService.delete(
                templateId,
                IfMatchVersion.require(
                        ifMatch, MailErrorCode.TEMPLATE_IF_MATCH_REQUIRED, MailErrorCode.TEMPLATE_CHANGED));
        return ResponseEntity.noContent().build();
    }

}
