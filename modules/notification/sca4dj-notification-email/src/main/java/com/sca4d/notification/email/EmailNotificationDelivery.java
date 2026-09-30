package com.sca4d.notification.email;

import com.sca4d.core.api.delivery.DeliveryOutcome;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.render.TemplateVariables;
import com.sca4d.notification.email.sender.EmailGateway;
import com.sca4d.notification.email.sender.EmailSenderConfiguration;
import com.sca4d.notification.email.sender.EmailSenderRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 把渠道无关通知解析为收件邮箱、Email 模板和 SMTP 配置并执行投递。
 */
public class EmailNotificationDelivery implements NotificationDelivery {

    private final @NotNull EmailSenderRepository senders;

    private final @NotNull EmailTemplateRepository templates;

    private final @NotNull EmailGateway transport;

    private final @NotNull EmailRecipientResolver recipients;

    private final @NotNull EmailRenderer renderer;

    /**
     * @param recipients 收件邮箱解析器
     * @param senders SMTP 发送方配置 Repository
     * @param templates Email 模板 Repository
     * @param renderer 模板渲染器
     * @param transport SMTP 传输入口
     */
    public EmailNotificationDelivery(
            @NotNull EmailRecipientResolver recipients,
            @NotNull EmailSenderRepository senders,
            @NotNull EmailTemplateRepository templates,
            @NotNull EmailRenderer renderer,
            @NotNull EmailGateway transport) {
        this.recipients = Objects.requireNonNull(recipients, "EmailRecipientResolver 不能为空");
        this.senders = Objects.requireNonNull(senders, "EmailSenderRepository 不能为空");
        this.templates = Objects.requireNonNull(templates, "EmailTemplateRepository 不能为空");
        this.renderer = Objects.requireNonNull(renderer, "EmailRenderer 不能为空");
        this.transport = Objects.requireNonNull(transport, "EmailGateway 不能为空");
    }

    @Override
    public @NotNull TreeCode channel() {
        return EmailChannels.EMAIL;
    }

    @Override
    public boolean available(@NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        return recipients.resolve(checked.recipient()).isPresent()
                && senders.findEnabledConfiguration().isPresent()
                && templates.findEnabled(checked.code()).isPresent()
                && checked.contents().isEmpty();
    }

    @Override
    public @NotNull DeliveryOutcome deliver(
            @NotNull Id messageId,
            @NotNull Notification notification) {
        Objects.requireNonNull(messageId, "通知消息标识不能为空");
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        if (!checked.contents().isEmpty()) {
            return DeliveryOutcome.rejected("EMAIL_CONTENT_UNSUPPORTED");
        }
        String recipient = recipients.resolve(checked.recipient())
                .map(EmailAddresses::require)
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE));
        EmailSenderConfiguration sender = senders.findEnabledConfiguration()
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE));
        EmailTemplate template = templates.findEnabled(checked.code())
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE));
        EmailTemplateSource source = template.source(checked.locale());
        RenderedEmail rendered;
        try {
            rendered = renderer.render(
                    source,
                    checked.locale(),
                    TemplateVariables.require(renderer.inspect(source).variables(), checked.variables()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(EmailNotificationErrorCode.TEMPLATE_INVALID, exception);
        }
        transport.send(sender, recipient, rendered);
        return DeliveryOutcome.confirmed();
    }
}
