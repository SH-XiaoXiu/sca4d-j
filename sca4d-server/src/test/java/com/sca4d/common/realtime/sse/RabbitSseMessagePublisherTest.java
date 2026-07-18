package com.sca4d.common.realtime.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sca4d.common.messaging.MessageCommand;
import com.sca4d.common.messaging.MessagePublishMode;
import com.sca4d.common.messaging.MessagePublisher;
import com.sca4d.common.realtime.RealtimeEnvelope;
import com.sca4d.common.realtime.RealtimeTarget;
import com.sca4d.config.properties.Sca4djRealtimeProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证 SSE 消息发布到正确的 RabbitMQ exchange 和 routing key。 */
class RabbitSseMessagePublisherTest {

    @Test
    void shouldSendDirectlyAfterRealtimePublisherCrossesCommitBoundary() {
        MessagePublisher messagePublisher = mock(MessagePublisher.class);
        RealtimeEnvelope<String> envelope = RealtimeEnvelope.of("notification.created", "payload");
        RabbitSseMessagePublisher publisher = new RabbitSseMessagePublisher(
                messagePublisher, new Sca4djRealtimeProperties());

        publisher.publish(RealtimeTarget.subject(41L), envelope);

        ArgumentCaptor<MessageCommand> command = ArgumentCaptor.forClass(MessageCommand.class);
        verify(messagePublisher).publish(command.capture());
        assertThat(command.getValue().getMode()).isEqualTo(MessagePublishMode.DIRECT);
        assertThat(command.getValue().getExchange()).isEqualTo("sca4dj.realtime.sse");
        assertThat(command.getValue().getRoutingKey()).isEqualTo("subject.41");
        assertThat(command.getValue().getEvent()).isEqualTo("notification.created");
        assertThat(command.getValue().getMessageId()).isEqualTo(envelope.getId());
    }
}
