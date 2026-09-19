package com.sca4d.notification.inbox;

import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationDelivery;
import com.sca4d.core.api.notification.NotificationRecipientType;
import com.sca4d.notification.template.NotificationRichTextRenderer;
import com.sca4d.notification.template.NotificationTemplateRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 把统一通知模板渲染成持久站内信快照。 */
public class InboxNotificationDelivery implements NotificationDelivery {

    private final NotificationTemplateRepository templates;

    private final NotificationRichTextRenderer renderer;

    private final InboxRepository inbox;

    private final InboxService service;

    private final IdGenerator ids;

    /** 创建站内信渠道投递实现。 */
    public InboxNotificationDelivery(
            @NotNull NotificationTemplateRepository templates,
            @NotNull NotificationRichTextRenderer renderer,
            @NotNull InboxRepository inbox,
            @NotNull InboxService service,
            @NotNull IdGenerator ids) {
        this.templates = Objects.requireNonNull(templates, "通知模板 Repository 不能为空");
        this.renderer = Objects.requireNonNull(renderer, "通知富文本渲染器不能为空");
        this.inbox = Objects.requireNonNull(inbox, "InboxRepository 不能为空");
        this.service = Objects.requireNonNull(service, "InboxService 不能为空");
        this.ids = Objects.requireNonNull(ids, "IdGenerator 不能为空");
    }

    @Override
    public @NotNull NotificationChannel channel() {
        return NotificationChannel.INBOX;
    }

    @Override
    public boolean available(@NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        return NotificationRecipientType.ACCOUNT.equals(checked.recipient().type())
                && templates.findEnabled(checked.code()).isPresent();
    }

    @Override
    @Transactional
    public void deliver(@NotNull MessageId messageId, @NotNull Notification notification) {
        Notification checked = Objects.requireNonNull(notification, "通知不能为空");
        long accountId = Long.parseLong(checked.recipient().id());
        var template = templates.findEnabled(checked.code())
                .orElseThrow(() -> new IllegalStateException("站内信缺少启用的统一通知模板"));
        var rendered = renderer.render(template.content(), checked.locale(), checked.variables());
        if (inbox.add(ids.nextId(), messageId, accountId, checked, rendered)) {
            service.publish(accountId);
        }
    }
}
