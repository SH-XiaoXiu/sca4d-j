package com.sca4d.notification.email;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.render.TemplateVariables;
import com.sca4d.notification.email.sender.EmailGateway;
import com.sca4d.notification.email.sender.EmailSenderRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 使用 Email 模板和当前 SMTP 配置同步发送带可选附件的邮件。
 */
public class EmailMessageService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailMessageService.class);

    private final @NotNull EmailSenderRepository senders;

    private final @NotNull EmailTemplateRepository templates;

    private final @NotNull EmailGateway transport;

    private final @NotNull EmailRenderer renderer;

    private final long maxAttachmentBytes;

    /**
     * @param senders SMTP 发送方配置 Repository
     * @param templates Email 模板 Repository
     * @param renderer 模板渲染器
     * @param transport SMTP 传输入口
     * @param maxAttachmentBytes 单次邮件附件总字节上限
     */
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
        TreeCode checkedCode = Objects.requireNonNull(code, "通知编码不能为空");
        List<EmailAttachment> checkedAttachments = List.copyOf(Objects.requireNonNull(attachments, "附件不能为空"));
        long total = checkedAttachments.stream().mapToLong(EmailAttachment::size).sum();
        if (total > maxAttachmentBytes) {
            LOG.warn(
                    "Email 投递不可用 | code={} reason=ATTACHMENT_LIMIT totalBytes={} maxBytes={}",
                    checkedCode.format(NameFormat.SLASH),
                    total,
                    maxAttachmentBytes);
            throw new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE);
        }
        var sender = senders.findEnabledConfiguration().orElse(null);
        if (sender == null) {
            LOG.warn("Email 投递不可用 | code={} reason=NO_ENABLED_SENDER", checkedCode.format(NameFormat.SLASH));
            throw new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE);
        }
        EmailTemplate template = templates.findEnabled(checkedCode).orElse(null);
        if (template == null) {
            LOG.warn("Email 投递不可用 | code={} reason=NO_ENABLED_TEMPLATE", checkedCode.format(NameFormat.SLASH));
            throw new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE);
        }
        EmailTemplateSource source = template.source(locale);
        RenderedEmail rendered = renderer.render(
                source,
                locale,
                TemplateVariables.require(renderer.inspect(source).variables(), variables));
        String checkedRecipient;
        try {
            checkedRecipient = EmailAddresses.require(recipient);
        } catch (IllegalArgumentException exception) {
            LOG.warn("Email 投递不可用 | code={} reason=INVALID_RECIPIENT", checkedCode.format(NameFormat.SLASH));
            throw new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE, exception);
        }
        transport.send(sender, checkedRecipient, rendered, checkedAttachments);
    }
}
