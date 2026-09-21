package com.sca4d.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 验证通用业务消息信封的固定元数据和 Event 契约检查。 */
class RabbitMessageEnvelopeCodecTest {

    @Test
    @DisplayName("Event 通过 v2 信封往返并保留投递元数据")
    void roundTripsEventEnvelope() {
        Instant occurredAt = Instant.parse("2026-07-28T00:00:00Z");
        RabbitMessageEnvelopeCodec codec = new RabbitMessageEnvelopeCodec(new ObjectMapper(), List.of());
        EventType<Map> type = new EventType<>("INVOICE/CREATED", 2, Map.class);
        byte[] encoded = codec.encode(
                new MessageId(101),
                new Event<>(type, "invoice:8", occurredAt, Map.of("invoiceId", 8)),
                occurredAt.plusSeconds(1),
                "test-app");

        IncomingMessage<Event<Map>> decoded = codec.decodeEvent(encoded, type, true, 2);

        assertThat(new String(encoded, StandardCharsets.UTF_8))
                .contains("\"envelopeVersion\":2", "\"messageId\":\"101\"", "\"messageCode\":\"INVOICE/CREATED\"");
        assertThat(decoded.messageId()).isEqualTo(new MessageId(101));
        assertThat(decoded.message().businessKey()).isEqualTo("invoice:8");
        assertThat(decoded.message().payload()).containsEntry("invoiceId", 8);
        assertThat(decoded.sourceApplication()).isEqualTo("test-app");
        assertThat(decoded.publishedAt()).isEqualTo(occurredAt.plusSeconds(1));
        assertThat(decoded.redelivered()).isTrue();
        assertThat(decoded.attempt()).isEqualTo(2);

        assertThatThrownBy(
                () -> codec.decodeEvent(
                        encoded,
                        new EventType<>("INVOICE/CREATED", 3, Map.class),
                        false,
                        1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不一致");
        assertThatThrownBy(
                () -> codec.decodeEvent(
                        "{\"envelopeVersion\":2,\"unexpected\":true}".getBytes(StandardCharsets.UTF_8),
                        type,
                        false,
                        1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知字段");
        assertThatThrownBy(
                () -> codec.decodeEvent(
                        new String(encoded, StandardCharsets.UTF_8)
                                .replace("\"messageId\":\"101\"", "\"messageId\":101")
                                .getBytes(StandardCharsets.UTF_8),
                        type,
                        false,
                        1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("字段类型错误: messageId");
    }
}
