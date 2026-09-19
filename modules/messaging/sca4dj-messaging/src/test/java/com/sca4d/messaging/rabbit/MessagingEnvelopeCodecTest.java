package com.sca4d.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.core.api.messaging.MessageId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/** 验证业务消息信封固定元数据和 Consumer 契约检查。 */
class MessagingEnvelopeCodecTest {

    @Test
    @DisplayName("Round Trips Strict Event Envelope And Delivery Metadata")
    void roundTripsMessageIdAndPayloadWithoutExposingRabbitHeaders() {
        Instant occurredAt = Instant.parse("2026-07-28T00:00:00Z");
        MessagingEnvelopeCodec codec = new MessagingEnvelopeCodec(objectMapper());
        EventType<Map> type = new EventType<>("invoice.created", 2, Map.class);
        byte[] encoded = codec.encode(
                new MessageId(101),
                new Event<>(type, "invoice:8", occurredAt, Map.of("invoiceId", 8)),
                occurredAt.plusSeconds(1),
                "test-app");

        MessageDelivery<Map> decoded = codec.decode(encoded, type, true);

        assertThat(new String(encoded, StandardCharsets.UTF_8)).contains("\"messageId\":\"101\"");
        assertThat(decoded.messageId()).isEqualTo(new MessageId(101));
        assertThat(decoded.event().businessKey()).isEqualTo("invoice:8");
        assertThat(decoded.event().payload()).containsEntry("invoiceId", 8);
        assertThat(decoded.sourceApplication()).isEqualTo("test-app");
        assertThat(decoded.publishedAt()).isEqualTo(occurredAt.plusSeconds(1));
        assertThat(decoded.redelivered()).isTrue();
        assertThatThrownBy(
                () -> codec.decode(
                        encoded,
                        new EventType<>("invoice.created", 3, Map.class),
                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EventType");
        assertThatThrownBy(
                () -> codec.decode(
                        "{\"envelopeVersion\":1,\"unexpected\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        type,
                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知字段");
        assertThatThrownBy(
                () -> codec.decode(
                        new String(encoded, StandardCharsets.UTF_8)
                                .replace("\"messageId\":\"101\"", "\"messageId\":101")
                                .getBytes(StandardCharsets.UTF_8),
                        type,
                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("字段类型错误: messageId");
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
