package com.sca4d.app.server.controllers.system;

import com.sca4d.app.server.audit.SystemOperations;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.system.request.CreateEmailSenderRequest;
import com.sca4d.app.server.system.request.EmailResourceStatusRequest;
import com.sca4d.app.server.system.request.ReplaceEmailSenderRequest;
import com.sca4d.app.server.system.view.EmailSenderView;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.core.spring.web.annotation.IfMatch;
import com.sca4d.notification.email.sender.EmailSender;
import com.sca4d.notification.email.sender.EmailSenderService;
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

/** 管理 Email 通知使用的 SMTP 配置，不读取或返回密码与密文。 */
@RestController
@RequestMapping("/system/email/senders")
@Tag(name = "Email Notifications")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class EmailSenderController {

    private final @NotNull EmailSenderService senders;

    /** @param senders SMTP 配置管理服务 */
    public EmailSenderController(@NotNull EmailSenderService senders) {
        this.senders = Objects.requireNonNull(senders, "Email Sender 服务不能为空");
    }

    /** @return 全部未删除且不包含密码的 SMTP 配置 */
    @GetMapping
    @Operation(summary = "查询 SMTP 配置")
    @RequirePermission(operation = SystemOperations.EMAIL_READ_VALUE)
    public @NotNull ApiResponse<List<EmailSenderView>> findAll() {
        return ApiResponse.ok(senders.findAll().stream().map(EmailSenderView::from).toList());
    }

    /** 创建默认停用的 SMTP 配置，并加密保存可选密码。 */
    @PostMapping
    @Operation(summary = "创建 SMTP 配置")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/EMAIL/SENDER/CREATE")
    public @NotNull ApiResponse<EmailSenderView> create(
            @RequestBody @Valid @NotNull CreateEmailSenderRequest request) {
        EmailSender created = senders.create(
                request.getName(),
                request.getHost(),
                request.getPort(),
                request.getSecurity(),
                request.getUsername(),
                request.getPassword(),
                request.getFromAddress(),
                request.getFromName(),
                request.getReplyTo());
        return ApiResponse.created(
                EmailSenderView.from(created),
                URI.create("/system/email/senders/" + created.senderId()));
    }

    /** 读取一个不包含密码的 SMTP 配置，并发布 ETag。 */
    @GetMapping("/{senderId}")
    @Operation(summary = "查询 SMTP 配置详情")
    @RequirePermission(operation = SystemOperations.EMAIL_READ_VALUE)
    public @NotNull ApiResponse<EmailSenderView> get(@PathVariable long senderId) {
        EmailSender sender = senders.loadById(senderId);
        return ApiResponse.ok(EmailSenderView.from(sender));
    }

    /** 按 `If-Match` 完整替换 SMTP 配置，按显式标志保留、替换或清除密码。 */
    @PutMapping("/{senderId}")
    @IfMatch
    @Operation(summary = "替换 SMTP 配置")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/EMAIL/SENDER/REPLACE")
    public @NotNull ApiResponse<EmailSenderView> replace(
            @PathVariable @AuditTarget(resourceType = EmailSender.RESOURCE_TYPE_VALUE) long senderId,
            @RequestBody @Valid @NotNull ReplaceEmailSenderRequest request) {
        EmailSender updated = senders.replace(
                senderId,
                request.getName(),
                request.getHost(),
                request.getPort(),
                request.getSecurity(),
                request.getUsername(),
                request.isPasswordChanged(),
                request.getPassword(),
                request.getFromAddress(),
                request.getFromName(),
                request.getReplyTo());
        return ApiResponse.ok(EmailSenderView.from(updated));
    }

    /** 按 `If-Match` 启用或停用 SMTP 配置；数据库保证最多一个启用配置。 */
    @PatchMapping("/{senderId}/status")
    @IfMatch
    @Operation(summary = "修改 SMTP 配置状态")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/EMAIL/SENDER/STATUS/CHANGE")
    public @NotNull ApiResponse<EmailSenderView> setEnabled(
            @PathVariable @AuditTarget(resourceType = EmailSender.RESOURCE_TYPE_VALUE) long senderId,
            @RequestBody @Valid @NotNull EmailResourceStatusRequest request) {
        EmailSender updated = senders.setEnabled(senderId, request.isEnabled());
        return ApiResponse.ok(EmailSenderView.from(updated));
    }

    /** 使用指定 SMTP 配置建立并关闭连接，不发送邮件。 */
    @PostMapping("/{senderId}/connection-tests")
    @Operation(summary = "测试 SMTP 连接")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/EMAIL/SENDER/CONNECTION/TEST")
    public @NotNull ApiResponse<Void> testConnection(
            @PathVariable @AuditTarget(resourceType = EmailSender.RESOURCE_TYPE_VALUE) long senderId) {
        senders.testConnection(senderId);
        return ApiResponse.noContent();
    }

    /** 按 `If-Match` 删除停用 SMTP 配置。 */
    @DeleteMapping("/{senderId}")
    @IfMatch
    @Operation(summary = "删除 SMTP 配置")
    @RequirePermission(operation = SystemOperations.EMAIL_MANAGE_VALUE)
    @Audit(operation = "SYSTEM/EMAIL/SENDER/DELETE")
    public @NotNull ApiResponse<Void> delete(
            @PathVariable @AuditTarget(resourceType = EmailSender.RESOURCE_TYPE_VALUE) long senderId) {
        senders.delete(senderId);
        return ApiResponse.noContent();
    }
}
