package com.sca4d.notification.email;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.notification.email.sender.EmailSenderConfiguration;
import com.sca4d.notification.email.sender.EmailSenderRepository;
import com.sca4d.notification.email.sender.EmailTransport;
import com.sca4d.notification.template.NotificationTemplateRenderers;
import com.sca4d.notification.template.NotificationTemplateRepository;
import com.sca4d.notification.template.RenderedNotification;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 把渠道无关通知解析为收件邮箱、多语言模板和当前 SMTP 配置并执行投递。 */
public final class EmailNotificationDelivery implements NotificationDelivery {

    private final @NotNull EmailRecipientResolver recipients;

    private final @NotNull EmailSenderRepository senders;

    private final @NotNull NotificationTemplateRepository templates;

    private final @NotNull NotificationTemplateRenderers renderers;

    private final @NotNull EmailTransport transport;

    /**
     * @param recipients 内部接收人邮箱解析入口
     * @param senders SMTP 配置持久化语义
     * @param templates 平台无关通知模板持久化语义
     * @param renderers 统一 Tiptap JSON 模板渲染入口
     * @param transport SMTP 操作
     */
    public EmailNotificationDelivery(
            @NotNull EmailRecipientResolver recipients,
            @NotNull EmailSenderRepository senders,
            @NotNull NotificationTemplateRepository templates,
            @NotNull NotificationTemplateRenderers renderers,
            @NotNull EmailTransport transport) {
        this.recipients = Objects.requireNonNull(recipients, "EmailRecipientResolver 不能为空");
        this.senders = Objects.requireNonNull(senders, "EmailSenderRepository 不能为空");
        this.templates = Objects.requireNonNull(templates, "NotificationTemplateRepository 不能为空");
        this.renderers = Objects.requireNonNull(renderers, "NotificationTemplateRenderers 不能为空");
        this.transport = Objects.requireNonNull(transport, "EmailTransport 不能为空");
    }

    @Override
    public @NotNull NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean available(@NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        return recipients.resolve(checked.recipient()).isPresent()
                && senders.findEnabledConfiguration().isPresent()
                && templates.findEnabled(checked.code()).isPresent();
    }

    @Override
    public void deliver(@NotNull MessageId messageId, @NotNull Notification notification) {
        Objects.requireNonNull(messageId, "通知消息标识不能为空");
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        String recipient = recipients.resolve(checked.recipient())
                .map(EmailAddresses::require)
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE));
        EmailSenderConfiguration sender = senders.findEnabledConfiguration()
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE));
        var template = templates.findEnabled(checked.code())
                .orElseThrow(() -> new BusinessException(EmailNotificationErrorCode.DELIVERY_UNAVAILABLE));
        RenderedNotification rendered;
        try {
            rendered = renderers.renderDefaults(template.content(), checked.locale(), checked.variables());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(EmailNotificationErrorCode.TEMPLATE_INVALID, exception);
        }
        transport.send(sender, recipient, new RenderedEmail(rendered.title(), rendered.text(), rendered.html()));
    }
}
