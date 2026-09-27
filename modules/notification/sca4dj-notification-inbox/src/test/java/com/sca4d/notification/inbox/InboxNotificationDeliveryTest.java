package com.sca4d.notification.inbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.notification.inbox.template.InboxTemplate;
import com.sca4d.notification.inbox.template.InboxTemplateRenderer;
import com.sca4d.notification.inbox.template.InboxTemplateRepository;
import com.sca4d.notification.inbox.template.RenderedInboxMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 验证站内信按原始 Id 幂等保存，并只在首次新增后发布刷新事件。
 */
class InboxNotificationDeliveryTest {

    @Test
    @DisplayName("重复消息只发布一次 Inbox changed")
    void publishesChangeOnlyForFirstSnapshot() {
        InboxTemplateRepository templates = mock(InboxTemplateRepository.class);
        InboxTemplateRenderer renderers = mock(InboxTemplateRenderer.class);
        InboxRepository repository = mock(InboxRepository.class);
        InboxService service = mock(InboxService.class);
        IdGenerator ids = mock(IdGenerator.class);
        Notification notification = new Notification(
                TreeCode.of("SECURITY/CHANGED"),
                NotificationRecipient.account(7),
                Locale.SIMPLIFIED_CHINESE,
                Map.of(),
                null,
                Set.of(InboxChannels.INBOX));
        InboxTemplate template = new InboxTemplate(
                10,
                notification.code(),
                new ObjectMapper().createObjectNode(),
                true,
                new Version(0));
        RenderedInboxMessage rendered = new RenderedInboxMessage("安全提醒", "正文", "<p>正文</p>");
        when(templates.findEnabled(notification.code())).thenReturn(Optional.of(template));
        when(renderers.render(template.content(), notification.locale(), notification.variables()))
                .thenReturn(rendered);
        when(ids.nextId()).thenReturn(Id.of(11), Id.of(12));
        when(repository.add(any(Long.class), eq(Id.of(20)), eq(7L), eq(notification), eq(rendered)))
                .thenReturn(true, false);
        InboxNotificationDelivery delivery = new InboxNotificationDelivery(
                templates,
                renderers,
                repository,
                service,
                ids);

        delivery.deliver(Id.of(20), notification);
        delivery.deliver(Id.of(20), notification);

        verify(service, times(1)).publish(7L);
    }
}
