package com.sca4d.notification.email;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.render.TemplateVariables;
import com.sca4d.notification.email.sender.EmailGateway;
import com.sca4d.notification.email.sender.EmailSenderRepository;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 使用 Email 模板和当前 SMTP 配置同步发送带可选附件的邮件。
 */
public final class EmailMessageService {

    private final @NotNull EmailSenderRepository senders;

    private final @NotNull EmailTemplateRepository templates;

    private final @NotNull EmailRenderer renderer;

    private final @NotNull EmailGateway transport;

    private final long maxAttachmentBytes;

    public EmailMessageService(
            @NotNull EmailSenderRepository senders,
            @NotNull EmailTemplateRepository templates,
            @NotNull EmailRenderer renderer,
            @NotNull EmailGateway transport,
            long maxAttachmentBytes) {
        this.senders = Objects.requireNonNull(senders, "Email Sender Repository 不能为空");
        this.templates = Objects.requireNonNull(templates, "Email Template Repository 不能为空");
        this.renderer = Objects.requireNonNull(renderer, "Email Renderer 不能为空");
        this.transport = Objects.requireNonNull(transport, "Email Transport 不能为空");
        if (maxAttachmentBytes < 1) {
            throw new IllegalArgumentException("Email 附件总大小上限必须为正数");
        }
        this.maxAttachmentBytes = maxAttachmentBytes;
    }

    /**
     * 渲染指定模板并向已校验邮箱同步发送附件。
     */
    public void sendNow(
            @NotNull TreeCode code,
            @NotNull String recipient,
            @NotNull Locale locale,
            @NotNull Map<String, String> variables,
            @NotNull List<EmailAttachment> attachments) {
        List<EmailAttachment> checkedAttachments = List.copyOf(Objects.requireNonNull(attachments, "附件不能为空"));
        long total = checkedAttachments.stream().mapToLong(EmailAttachment::size).sum();
        if (total > maxAttachmentBytes) {
            throw new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE);
        }
        var sender = senders.findEnabledConfiguration()
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE));
        EmailTemplate template = templates.findEnabled(Objects.requireNonNull(code, "通知编码不能为空"))
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE));
        EmailTemplateSource source = template.source(locale);
        RenderedEmail rendered = renderer.render(
                source,
                locale,
                TemplateVariables.require(renderer.inspect(source).variables(), variables));
        transport.send(sender, EmailAddresses.require(recipient), rendered, checkedAttachments);
    }
}
