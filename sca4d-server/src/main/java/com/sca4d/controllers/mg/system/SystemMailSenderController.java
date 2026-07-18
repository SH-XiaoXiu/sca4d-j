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
import com.sca4d.mail.MailSenderService;
import com.sca4d.mail.MailService;
import com.sca4d.mail.request.MailSenderCreateRequest;
import com.sca4d.mail.request.MailSenderStatusRequest;
import com.sca4d.mail.request.MailSenderUpdateRequest;
import com.sca4d.mail.view.MailSenderView;
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

/** 平台系统管理端的 SMTP 邮件发送配置接口。 */
@RestController
@RequestMapping("/mg/system/mail-senders")
@AuthPrincipal(types = PrincipalType.PLATFORM, selection = PlatformPrincipalSelection.class)
@RequiredArgsConstructor
public class SystemMailSenderController {

    private final MailSenderService senderService;
    private final MailService mailService;

    /** @return 全部未删除配置，不包含任何密码加密数据 */
    @GetMapping
    @RequirePermission(SystemPermissions.MAIL_SENDERS_READ)
    public @NotNull ApiResponse<List<MailSenderView>> senders() {
        return ApiResponse.ok(senderService.senders().stream().map(MailSenderView::from).toList());
    }

    /** @return 指定安全配置，不包含任何密码加密数据 */
    @GetMapping("/{senderId}")
    @RequirePermission(SystemPermissions.MAIL_SENDERS_READ)
    public @NotNull ApiResponse<MailSenderView> detail(
            @PathVariable @NotNull Long senderId) {
        MailSenderView sender = MailSenderView.from(senderService.require(senderId));
        return ApiResponse.ok(sender, sender.getVersion());
    }

    /**
     * 创建默认停用的 SMTP 配置，密码只写入 AES-GCM 密文。
     *
     * @return 201、ETag 和新配置完整表示
     */
    @PostMapping
    @Idempotent(operation = "system.mail-senders.create", key = "#idempotencyKey")
    @Audit(module = SystemAuditOperations.MAIL_SENDERS, action = SystemAuditOperations.CREATE, targetType = MailResourceTypes.MAIL_SENDER, targetId = "#result?.data?.senderId")
    @RequirePermission(SystemPermissions.MAIL_SENDERS_MANAGE)
    public @NotNull ApiResponse<MailSenderView> create(
            @Valid @RequestBody @NotNull MailSenderCreateRequest request,
            @RequestHeader("Idempotency-Key") @NotNull String idempotencyKey) {
        Long senderId = senderService.create(
                request.getName(),
                request.getHost(),
                request.getPort(),
                request.getSecurity(),
                request.getUsername(),
                request.getPassword(),
                request.getFromAddress(),
                request.getFromName(),
                request.getReplyTo());
        MailSenderView sender = MailSenderView.from(senderService.require(senderId));
        return ApiResponse.created(sender, sender.getVersion());
    }

    /** 修改 SMTP 地址、认证信息和默认发件资料。 */
    @PutMapping("/{senderId}")
    @Audit(module = SystemAuditOperations.MAIL_SENDERS, action = SystemAuditOperations.UPDATE, targetType = MailResourceTypes.MAIL_SENDER, targetId = "#senderId")
    @RequirePermission(SystemPermissions.MAIL_SENDERS_MANAGE)
    public @NotNull ApiResponse<MailSenderView> update(
            @PathVariable @NotNull Long senderId,
            @Valid @RequestBody @NotNull MailSenderUpdateRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        senderService.update(
                senderId,
                request.getName(),
                request.getHost(),
                request.getPort(),
                request.getSecurity(),
                request.getUsername(),
                request.getPassword(),
                request.getClearPassword(),
                request.getFromAddress(),
                request.getFromName(),
                request.getReplyTo(),
                IfMatchVersion.require(ifMatch, MailErrorCode.SENDER_IF_MATCH_REQUIRED, MailErrorCode.SENDER_CHANGED));
        MailSenderView sender = MailSenderView.from(senderService.require(senderId));
        return ApiResponse.ok(sender, sender.getVersion());
    }

    /** 启用或停用 SMTP 配置。 */
    @PatchMapping("/{senderId}")
    @Audit(module = SystemAuditOperations.MAIL_SENDERS, action = SystemAuditOperations.CHANGE_STATUS, targetType = MailResourceTypes.MAIL_SENDER, targetId = "#senderId")
    @RequirePermission(SystemPermissions.MAIL_SENDERS_MANAGE)
    public @NotNull ApiResponse<MailSenderView> changeStatus(
            @PathVariable @NotNull Long senderId,
            @Valid @RequestBody @NotNull MailSenderStatusRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        senderService.changeStatus(
                senderId,
                request.getStatus(),
                IfMatchVersion.require(ifMatch, MailErrorCode.SENDER_IF_MATCH_REQUIRED, MailErrorCode.SENDER_CHANGED));
        MailSenderView sender = MailSenderView.from(senderService.require(senderId));
        return ApiResponse.ok(sender, sender.getVersion());
    }

    /** 使用当前配置建立并关闭一次 SMTP 连接，不发送邮件。 */
    @PostMapping("/{senderId}/connection-tests")
    @Audit(module = SystemAuditOperations.MAIL_SENDERS, action = SystemAuditOperations.TEST_CONNECTION, targetType = MailResourceTypes.MAIL_SENDER, targetId = "#senderId")
    @RequirePermission(SystemPermissions.MAIL_SENDERS_MANAGE)
    public @NotNull ResponseEntity<Void> testConnection(
            @PathVariable @NotNull Long senderId) {
        mailService.testConnection(senderId);
        return ResponseEntity.noContent().build();
    }

    /** 删除已经停用的 SMTP 配置。 */
    @DeleteMapping("/{senderId}")
    @Audit(module = SystemAuditOperations.MAIL_SENDERS, action = SystemAuditOperations.DELETE, targetType = MailResourceTypes.MAIL_SENDER, targetId = "#senderId")
    @RequirePermission(SystemPermissions.MAIL_SENDERS_MANAGE)
    public @NotNull ResponseEntity<Void> delete(
            @PathVariable @NotNull Long senderId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        senderService.delete(
                senderId,
                IfMatchVersion.require(ifMatch, MailErrorCode.SENDER_IF_MATCH_REQUIRED, MailErrorCode.SENDER_CHANGED));
        return ResponseEntity.noContent().build();
    }
}
