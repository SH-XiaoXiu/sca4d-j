package com.sca4d.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sca4d.common.realtime.sse.SseConnectionRegistry;
import com.sca4d.common.realtime.sse.SseMessagePublisher;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 验证实时消息只在事务提交后发送，并保留当前请求追踪信息。 */
class DefaultRealtimePublisherTest {

    @Test
    void shouldPublishOnlyAfterActiveTransactionCommits() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        SseConnectionRegistry sseRegistry = mock(SseConnectionRegistry.class);
        DefaultRealtimePublisher publisher = new DefaultRealtimePublisher(
                messagingTemplate, sseRegistry, null, new Sca4djRealtimeProperties());

        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish(RealtimePublishRequest.sse(
                    RealtimeTarget.subject(7L), "notification.created", "data"));

            verify(sseRegistry, never()).send(any(), any());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(sseRegistry).send(eq("subject.7"), any(RealtimeEnvelope.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldPublishImmediatelyWhileTransactionSynchronizationIsStillActive() {
        SseConnectionRegistry sseRegistry = mock(SseConnectionRegistry.class);
        DefaultRealtimePublisher publisher = new DefaultRealtimePublisher(
                mock(SimpMessagingTemplate.class), sseRegistry, null, new Sca4djRealtimeProperties());

        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishNow(RealtimePublishRequest.sse(
                    RealtimeTarget.subject(7L), "authentication.session.changed", null));

            verify(sseRegistry).send(eq("subject.7"), any(RealtimeEnvelope.class));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldPublishWebsocketUserQueueWithoutRabbitDependency() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        SseConnectionRegistry sseRegistry = mock(SseConnectionRegistry.class);
        DefaultRealtimePublisher publisher = new DefaultRealtimePublisher(messagingTemplate, sseRegistry, null, new Sca4djRealtimeProperties());

        publisher.publish(RealtimePublishRequest.websocketUserQueue(
                RealtimeTarget.subject(7L), "/queue/im", "im.message.created", "data"));

        verify(messagingTemplate).convertAndSendToUser(eq("subject:7"), eq("/queue/im"), any(RealtimeEnvelope.class));
        verify(sseRegistry, never()).send(any(), any());
    }

    @Test
    void shouldSendSseToLocalRegistry() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        SseConnectionRegistry sseRegistry = mock(SseConnectionRegistry.class);
        DefaultRealtimePublisher publisher = new DefaultRealtimePublisher(messagingTemplate, sseRegistry, null, new Sca4djRealtimeProperties());
        RealtimeTarget target = RealtimeTarget.subject(7L);

        publisher.publish(RealtimePublishRequest.sse(target, "notification.created", "data"));

        verify(sseRegistry).send(eq("subject.7"), any(RealtimeEnvelope.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void shouldSendSseToCrossNodePublisherWhenAvailable() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        SseConnectionRegistry sseRegistry = mock(SseConnectionRegistry.class);
        SseMessagePublisher sseMessagePublisher = mock(SseMessagePublisher.class);
        DefaultRealtimePublisher publisher = new DefaultRealtimePublisher(
                messagingTemplate, sseRegistry, sseMessagePublisher, new Sca4djRealtimeProperties());
        RealtimeTarget target = RealtimeTarget.subject(7L);

        publisher.publish(RealtimePublishRequest.sse(target, "notification.created", "data"));

        verify(sseMessagePublisher).publish(eq(target), any(RealtimeEnvelope.class));
        verify(sseRegistry, never()).send(any(), any());
    }

    @Test
    void shouldRejectSsePublicationWithoutAvailableTransport() {
        DefaultRealtimePublisher publisher = new DefaultRealtimePublisher(
                mock(SimpMessagingTemplate.class), null, null, new Sca4djRealtimeProperties());

        assertThatThrownBy(() -> publisher.publish(RealtimePublishRequest.sse(
                RealtimeTarget.subject(7L), "notification.created", "data")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SSE 已启用但未注册消息发布器或本机连接注册表");
    }
}
