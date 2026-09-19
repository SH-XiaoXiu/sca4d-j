package com.sca4d.iot.emqx.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 验证 MQTT Runtime 消息保留实际发布标志并保护原始 payload。 */
class MqttMessageTest {

    @Test
    @DisplayName("消息保留 QoS 和 Retain 且不暴露可变 payload")
    void keepsDeliveryFactsAndProtectsPayload() {
        byte[] payload = new byte[] {0x01, 0x02};
        MqttMessage message = new MqttMessage(
                "devices/1/up",
                payload,
                Instant.parse("2026-08-15T12:00:00Z"),
                2,
                true);

        payload[0] = 0x03;

        assertThat(message.payload()).containsExactly(0x01, 0x02);
        assertThat(message.qos()).isEqualTo(2);
        assertThat(message.retained()).isTrue();
    }

    @Test
    @DisplayName("消息拒绝 MQTT 范围外的 QoS")
    void rejectsInvalidQos() {
        assertThatThrownBy(
                () -> new MqttMessage(
                        "devices/1/up",
                        new byte[0],
                        Instant.parse("2026-08-15T12:00:00Z"),
                        3,
                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QoS");
    }
}
