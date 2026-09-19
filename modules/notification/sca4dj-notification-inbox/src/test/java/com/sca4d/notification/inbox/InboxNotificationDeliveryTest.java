package com.sca4d.notification.inbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationChannel;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.notification.template.NotificationTemplate;
import com.sca4d.notification.template.NotificationTemplateRenderers;
import com.sca4d.notification.template.NotificationTemplateRepository;
import com.sca4d.notification.template.RenderedNotification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 验证站内信按原始 MessageId 幂等保存，并只在首次新增后发布刷新事件。 */
class InboxNotificationDeliveryTest {

    @Test
    @DisplayName("重复消息只发布一次 Inbox changed")
    void publishesChangeOnlyForFirstSnapshot() {
        NotificationTemplateRepository templates = mock(NotificationTemplateRepository.class);
        NotificationTemplateRenderers renderers = mock(NotificationTemplateRenderers.class);
        InboxRepository repository = mock(InboxRepository.class);
        InboxService service = mock(InboxService.class);
        IdGenerator ids = mock(IdGenerator.class);
        Notification notification = new Notification(
                new NotificationCode("security.changed"),
                NotificationRecipient.account(7),
                Locale.SIMPLIFIED_CHINESE,
                Map.of(),
                null,
                Set.of(NotificationChannel.INBOX));
        NotificationTemplate template = new NotificationTemplate(
                10,
                notification.code(),
                new ObjectMapper().createObjectNode(),
                true,
                new Version(0));
        RenderedNotification rendered = new RenderedNotification("安全提醒", "正文", "<p>正文</p>");
        when(templates.findEnabled(notification.code())).thenReturn(Optional.of(template));
        when(renderers.renderDefaults(template.content(), notification.locale(), notification.variables()))
                .thenReturn(rendered);
        when(ids.nextId()).thenReturn(11L, 12L);
        when(repository.add(any(Long.class), eq(new MessageId(20)), eq(7L), eq(notification), eq(rendered)))
                .thenReturn(true, false);
        InboxNotificationDelivery delivery = new InboxNotificationDelivery(
                templates,
                renderers,
                repository,
                service,
                ids);

        delivery.deliver(new MessageId(20), notification);
        delivery.deliver(new MessageId(20), notification);

        verify(service, times(1)).publish(7L);
    }
}
