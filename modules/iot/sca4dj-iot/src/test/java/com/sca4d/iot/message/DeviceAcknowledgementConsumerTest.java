package com.sca4d.iot.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.JacksonMessageCodec;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

/** 验证可靠 ACK 请求 Consumer 使用稳定事件类型并委托现有 ACK 服务。 */
class DeviceAcknowledgementHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-18T08:00:00Z");

    @Test
    @DisplayName("消费可靠 ACK 请求时委托消息级状态转换")
    void delegatesAcknowledgementRequest() {
        DeviceAcknowledgementService acknowledgements = mock(DeviceAcknowledgementService.class);
        DeviceAcknowledgementHandler consumer = new DeviceAcknowledgementHandler(
                acknowledgements,
                new JacksonMessageCodec<>(
                        DeviceAcknowledgement.CODE,
                        DeviceAcknowledgement.PROTOCOL_VERSION,
                        DeviceAcknowledgement.class,
                        new ObjectMapper()));
        DeviceAcknowledgement acknowledgement = new DeviceAcknowledgement(
                10,
                "reported-equals-desired",
                Map.of("propertyCode", "power"),
                "REPORTED",
                "status:1:power",
                NOW,
                NOW);
        IncomingMessage<DeviceAcknowledgement> delivery = new IncomingMessage<>(
                new MessageId(20),
                acknowledgement,
                "test-app",
                NOW,
                false,
                1);

        assertThat(consumer.handle(delivery).status()).isEqualTo(HandlingDecision.Status.ACKNOWLEDGE);

        assertThat(consumer.code()).isEqualTo(com.sca4d.core.api.code.TreeCode.of("IOT/DOWNLINK/ACK/HANDLER"));
        verify(acknowledgements).acknowledge(acknowledgement);
    }
}
