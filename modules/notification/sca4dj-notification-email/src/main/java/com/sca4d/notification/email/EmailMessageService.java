package com.sca4d.notification.email;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.notification.email.sender.EmailSenderRepository;
import com.sca4d.notification.email.sender.EmailTransport;
import com.sca4d.notification.template.NotificationRichTextRenderer;
import com.sca4d.notification.template.NotificationTemplateRepository;
import com.sca4d.notification.template.RenderedNotification;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 使用当前 Email 模板和 SMTP 配置同步发送带可选附件的邮件。 */
public final class EmailMessageService {
    private final @NotNull EmailSenderRepository senders;

    private final @NotNull NotificationTemplateRepository templates;

    private final @NotNull NotificationRichTextRenderer renderer;

    private final @NotNull EmailTransport transport;

    private final long maxAttachmentBytes;

    /** 创建使用数据库模板、当前启用 Sender 和 SMTP Transport 的同步发送服务。 */
    public EmailMessageService(
            @NotNull EmailSenderRepository senders,
            @NotNull NotificationTemplateRepository templates,
            @NotNull NotificationRichTextRenderer renderer,
            @NotNull EmailTransport transport,
            long maxAttachmentBytes) {
        this.senders = Objects.requireNonNull(senders, "Email Sender Repository 不能为空");
        this.templates = Objects.requireNonNull(templates, "Email Template Repository 不能为空");
        this.renderer = Objects.requireNonNull(renderer, "Email 模板渲染器不能为空");
        this.transport = Objects.requireNonNull(transport, "Email Transport 不能为空");
        if (maxAttachmentBytes < 1) {
            throw new IllegalArgumentException("Email 附件总大小上限必须为正数");
        }
        this.maxAttachmentBytes = maxAttachmentBytes;
    }

    /** 渲染指定模板并向已校验邮箱同步发送附件。 */
    public void sendNow(
            @NotNull NotificationCode code,
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
        var template = templates.findEnabled(Objects.requireNonNull(code, "通知编码不能为空"))
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE));
        RenderedNotification rendered = renderer.render(template.content(), locale, variables);
        RenderedEmail email = new RenderedEmail(rendered.title(), rendered.text(), rendered.html());
        transport.send(sender, EmailAddresses.require(recipient), email, checkedAttachments);
    }
}
