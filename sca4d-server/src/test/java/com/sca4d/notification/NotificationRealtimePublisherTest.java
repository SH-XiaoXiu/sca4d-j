package com.sca4d.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.realtime.RealtimePublishRequest;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.common.realtime.RealtimeTargetType;
import com.sca4d.notification.realtime.NotificationRealtimePublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** 验证通知在事务提交后发送给在线接收人。 */
class NotificationRealtimePublisherTest {

    @Test
    void shouldPreferPrincipalTargetForWorkspaceNotification() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RealtimePublisher> provider = mock(ObjectProvider.class);
        RealtimePublisher publisher = mock(RealtimePublisher.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        NotificationRealtimePublisher notificationPublisher = new NotificationRealtimePublisher(provider);

        notificationPublisher.created(
                1L, "title", "content", NotificationRecipient.of(2L, PrincipalType.TENANT, 3L));

        ArgumentCaptor<RealtimePublishRequest> request = ArgumentCaptor.forClass(RealtimePublishRequest.class);
        verify(publisher).publish(request.capture());
        assertThat(request.getValue().getTarget().getType()).isEqualTo(RealtimeTargetType.PRINCIPAL);
        assertThat(request.getValue().getTarget().getValue()).isEqualTo("tenant.3");
    }
}
