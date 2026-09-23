package com.sca4d.notification.inbox;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.delivery.DeliveryOutcome;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.notification.inbox.template.InboxTemplateRenderer;
import com.sca4d.notification.inbox.template.InboxTemplateRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 把站内信模板渲染成持久站内信快照。
 */
public class InboxNotificationDelivery implements NotificationDelivery {

    private final InboxTemplateRepository templates;

    private final InboxTemplateRenderer renderers;

    private final InboxRepository inbox;

    private final InboxService service;

    private final IdGenerator ids;

    /**
     * 创建站内信渠道投递实现。
     */
    public InboxNotificationDelivery(
            @NotNull InboxTemplateRepository templates,
            @NotNull InboxTemplateRenderer renderers,
            @NotNull InboxRepository inbox,
            @NotNull InboxService service,
            @NotNull IdGenerator ids) {
        this.templates = Objects.requireNonNull(templates, "站内信模板 Repository 不能为空");
        this.renderers = Objects.requireNonNull(renderers, "站内信模板渲染器不能为空");
        this.inbox = Objects.requireNonNull(inbox, "InboxRepository 不能为空");
        this.service = Objects.requireNonNull(service, "InboxService 不能为空");
        this.ids = Objects.requireNonNull(ids, "IdGenerator 不能为空");
    }

    @Override
    public @NotNull TreeCode channel() {
        return InboxChannels.INBOX;
    }

    @Override
    public boolean available(@NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        return NotificationRecipient.ACCOUNT_TYPE.equals(checked.recipient().type())
                && templates.findEnabled(checked.code()).isPresent();
    }

    @Override
    @Transactional
    public @NotNull DeliveryOutcome deliver(@NotNull MessageId messageId, @NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        long accountId = Long.parseLong(checked.recipient().id());
        var template = templates.findEnabled(checked.code())
                .orElseThrow(() -> new IllegalStateException("站内信缺少启用的站内信模板"));
        var rendered = renderers.render(template.content(), checked.locale(), checked.variables());
        if (inbox.add(ids.nextId().value(), messageId, accountId, checked, rendered)) {
            service.publish(accountId);
        }
        return DeliveryOutcome.confirmed();
    }
}
