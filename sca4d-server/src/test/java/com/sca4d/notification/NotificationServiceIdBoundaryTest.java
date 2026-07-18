package com.sca4d.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.entity.NotificationDeliveryEntity;
import com.sca4d.entity.NotificationMessageEntity;
import com.sca4d.notification.codec.NotificationPayloadCodec;
import com.sca4d.notification.realtime.NotificationRealtimePublisher;
import com.sca4d.notification.repository.NotificationRepository;
import com.sca4d.notification.service.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

/** 验证通知消息和投递记录在批量插入前都已生成分布式 ID。 */
class NotificationServiceIdBoundaryTest {

    @Test
    void shouldCreateDeliveriesAfterRepositoryReturnsNotificationId() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationPayloadCodec payloadCodec = mock(NotificationPayloadCodec.class);
        when(payloadCodec.encode(any())).thenReturn("{}");
        doAnswer(invocation -> {
            invocation.<NotificationMessageEntity>getArgument(0).setId(10L);
            return null;
        })
                .when(repository)
                .createMessage(any(), anyString());
        when(repository.countUnread(any())).thenReturn(List.of(1L));
        NotificationServiceImpl service = new NotificationServiceImpl(
                repository, payloadCodec, mock(NotificationRealtimePublisher.class));

        Long notificationId = service.create(
                "标题",
                "内容",
                NotificationCategory.SYSTEM,
                null,
                null,
                null,
                List.of(NotificationRecipient.subject(20L)));

        ArgumentCaptor<List<NotificationDeliveryEntity>> deliveriesCaptor = ArgumentCaptor.captor();
        verify(repository).createDeliveries(deliveriesCaptor.capture());
        assertThat(notificationId).isEqualTo(10L);
        assertThat(deliveriesCaptor.getValue())
                .extracting(NotificationDeliveryEntity::getNotificationId)
                .containsOnly(10L);
        assertThat(deliveriesCaptor.getValue())
                .extracting(NotificationDeliveryEntity::getId)
                .containsOnlyNulls();
    }
}
