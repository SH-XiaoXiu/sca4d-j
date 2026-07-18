package com.sca4d.common.messaging;

import static java.time.Duration.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.messaging.outbox.MessageOutboxRepository;
import com.sca4d.common.messaging.rabbit.RabbitMessageSender;
import com.sca4d.config.properties.Sca4djMessagingProperties;
import com.sca4d.entity.MessageOutboxEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 验证消息在事务提交后写入 Outbox，无事务时立即保存。 */
class DefaultMessagePublisherTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldEnqueueReliableMessageIntoOutbox() {
        RabbitMessageSender sender = mock(RabbitMessageSender.class);
        MessageOutboxRepository repository = mock(MessageOutboxRepository.class);
        ObjectProvider<MessageOutboxRepository> repositoryProvider = mock();
        when(repositoryProvider.getIfAvailable()).thenReturn(repository);
        IdGenerator idGenerator = new FixedIdGenerator();
        DefaultMessagePublisher publisher = new DefaultMessagePublisher(
                sender, repositoryProvider, new ObjectMapper(), idGenerator, new Sca4djMessagingProperties());

        MessageCommand command = MessageCommand.of(
                "order.event", "order.created", "order.created", Map.of("id", 1))
                .setMode(MessagePublishMode.OUTBOX)
                .setBusinessKey("order:1:created");
        publisher.publish(command);

        ArgumentCaptor<MessageOutboxEntity> captor = ArgumentCaptor.forClass(MessageOutboxEntity.class);
        verify(repository).enqueue(captor.capture());
        verify(sender, never()).send(any(), any(), any(), any());
        MessageOutboxEntity entity = captor.getValue();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getMessageId()).isEqualTo("100");
        assertThat(entity.getBusinessKey()).isEqualTo("order:1:created");
        assertThat(entity.getExchangeName()).isEqualTo("order.event");
        assertThat(entity.getRoutingKey()).isEqualTo("order.created");
        assertThat(entity.getEventName()).isEqualTo("order.created");
        assertThat(entity.getStatus().name()).isEqualTo("PENDING");
        assertThat(entity.getPayloadJson()).contains("\"id\":1");
    }

    @Test
    void shouldCopyImmutableHeadersBeforeAddingTransportMetadata() {
        RabbitMessageSender sender = mock(RabbitMessageSender.class);
        ObjectProvider<MessageOutboxRepository> repositoryProvider = mock();
        DefaultMessagePublisher publisher = new DefaultMessagePublisher(
                sender,
                repositoryProvider,
                new ObjectMapper(),
                new FixedIdGenerator(),
                new Sca4djMessagingProperties());
        MessageCommand command = MessageCommand.of(" events ", " created ", " order.created ", Map.of("id", 1))
                .setHeaders(Map.of("custom", "value"));

        publisher.publish(command);

        ArgumentCaptor<MessageEnvelope<Object>> envelopeCaptor = ArgumentCaptor.captor();
        verify(sender).send(
                ArgumentMatchers.eq("events"),
                ArgumentMatchers.eq("created"),
                envelopeCaptor.capture(),
                ArgumentMatchers.eq(ZERO));
        assertThat(envelopeCaptor.getValue().getHeaders())
                .containsEntry("custom", "value")
                .containsEntry(DefaultMessagePublisher.HEADER_MESSAGE_ID, "100")
                .containsEntry(DefaultMessagePublisher.HEADER_EVENT, "order.created");
    }

    @Test
    void shouldSendValidatedSnapshotAfterCommit() {
        RabbitMessageSender sender = mock(RabbitMessageSender.class);
        ObjectProvider<MessageOutboxRepository> repositoryProvider = mock();
        DefaultMessagePublisher publisher = new DefaultMessagePublisher(
                sender,
                repositoryProvider,
                new ObjectMapper(),
                new FixedIdGenerator(),
                new Sca4djMessagingProperties());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", 1);
        MessageCommand command = MessageCommand.of("events", "created", "order.created", payload)
                .setMode(MessagePublishMode.AFTER_COMMIT);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(command);
        command.setExchange("changed");
        command.getHeaders().put("late", "value");
        payload.put("id", 2);
        verify(sender, never()).send(any(), any(), any(), any());
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

        ArgumentCaptor<MessageEnvelope<Object>> envelopeCaptor = ArgumentCaptor.captor();
        verify(sender).send(
                ArgumentMatchers.eq("events"),
                ArgumentMatchers.eq("created"),
                envelopeCaptor.capture(),
                ArgumentMatchers.eq(ZERO));
        assertThat(envelopeCaptor.getValue().getPayload().toString()).isEqualTo("{\"id\":1}");
        assertThat(envelopeCaptor.getValue().getHeaders()).doesNotContainKey("late");
    }

    @Test
    void shouldRejectNegativeDelay() {
        DefaultMessagePublisher publisher = new DefaultMessagePublisher(
                mock(RabbitMessageSender.class),
                mock(),
                new ObjectMapper(),
                new FixedIdGenerator(),
                new Sca4djMessagingProperties());
        MessageCommand command = MessageCommand.of("events", "created", "order.created", null)
                .setDelay(Duration.ofSeconds(-1));

        assertThatThrownBy(() -> publisher.publish(command))
                .isInstanceOf(MessagePublishException.class)
                .hasMessage("消息延迟时间不能为负数");
    }

    /** 从 100 开始递增 ID，便于断言消息和 Outbox 主键。 */
    private static class FixedIdGenerator implements IdGenerator {

        private long id = 99;

        @Override
        public long nextId() {
            id++;
            return id;
        }
    }
}
