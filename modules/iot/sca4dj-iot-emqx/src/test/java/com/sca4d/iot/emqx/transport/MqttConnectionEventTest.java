package com.sca4d.iot.emqx.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/** 验证连接事件的输入约束、观察事实及跨观察器的 Payload 隔离。 */
class MqttConnectionEventTest {

    private static final String TOPIC = "$SYS/brokers/node/clients/client/connected";

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    @DisplayName("事件保留连接事实并在构造与读取时分别复制 Payload")
    void protectsEventAcrossObservers() {
        byte[] payload = {1, 2};
        MqttConnectionEvent event = new MqttConnectionEvent(TOPIC, "client", true, payload, OBSERVED_AT);
        payload[0] = 3;
        byte[] observed = event.payload();
        observed[1] = 4;

        assertThat(event.payload()).containsExactly(1, 2);
        assertThat(event.topic()).isEqualTo(TOPIC);
        assertThat(event.clientId()).isEqualTo("client");
        assertThat(event.connected()).isTrue();
        assertThat(event.observedAt()).isEqualTo(OBSERVED_AT);
        assertThat(new MqttConnectionEvent(TOPIC, "client", false, new byte[0], OBSERVED_AT).connected())
                .isFalse();
    }

    @Test
    @DisplayName("连接事件拒绝空白标识和缺失引用参数")
    void rejectsMissingFacts() {
        assertThatThrownBy(() -> new MqttConnectionEvent(" ", "client", true, new byte[0], OBSERVED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MqttConnectionEvent(TOPIC, " ", true, new byte[0], OBSERVED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MqttConnectionEvent(null, "client", true, new byte[0], OBSERVED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MqttConnectionEvent(TOPIC, null, true, new byte[0], OBSERVED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MqttConnectionEvent(TOPIC, "client", true, null, OBSERVED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MqttConnectionEvent(TOPIC, "client", true, new byte[0], null))
                .isInstanceOf(NullPointerException.class);
    }
}
