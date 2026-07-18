package com.sca4d.common.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.messaging.MessageEnvelope;
import com.sca4d.common.messaging.rabbit.RabbitMessageSender;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import com.sca4d.entity.MessageOutboxEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

/** 验证到期 Outbox 消息的领取、发送、重试和最终失败状态。 */
class MessageOutboxDeliveryServiceTest {

    @Test
    void shouldPreserveMissingTraceIdWhenDispatchingClaimedMessage() {
        MessageOutboxRepository repository = mock(MessageOutboxRepository.class);
        RabbitMessageSender sender = mock(RabbitMessageSender.class);
        Sca4djMessagingProperties properties = new Sca4djMessagingProperties();
        MessageOutboxEntity message = new MessageOutboxEntity()
                .setId(10L)
                .setMessageId("message-10")
                .setExchangeName("events")
                .setRoutingKey("orders.created")
                .setEventName("order.created")
                .setPayloadJson("{\"id\":10}")
                .setHeadersJson("{\"custom\":\"value\"}")
                .setVersion(2L);
        when(repository.claimForDispatch(any())).thenReturn(List.of(message));
        when(repository.markPublished(eq(message), any())).thenReturn(true);

        new DefaultMessageOutboxDeliveryService(repository, sender, new ObjectMapper(), properties)
                .dispatchDueMessages();

        ArgumentCaptor<MessageEnvelope<JsonNode>> envelopeCaptor = ArgumentCaptor.captor();
        verify(sender).send(eq("events"), eq("orders.created"), envelopeCaptor.capture(), eq(Duration.ZERO));
        assertThat(envelopeCaptor.getValue().getTraceId()).isNull();
        assertThat(envelopeCaptor.getValue().getHeaders()).containsEntry("custom", "value");
        verify(repository).markPublished(eq(message), any());
    }
}
