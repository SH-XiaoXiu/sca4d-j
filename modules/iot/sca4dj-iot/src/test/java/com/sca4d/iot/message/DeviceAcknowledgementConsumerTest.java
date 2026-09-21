package com.sca4d.iot.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.IotMessageTypes;
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
        DeviceAcknowledgementHandler consumer = new DeviceAcknowledgementHandler(acknowledgements);
        DeviceAcknowledgement acknowledgement = new DeviceAcknowledgement(
                10,
                "reported-equals-desired",
                Map.of("propertyCode", "power"),
                "REPORTED",
                "status:1:power",
                NOW,
                NOW);
        MessageDelivery<DeviceAcknowledgement> delivery = new MessageDelivery<>(
                new MessageId(20),
                new Event<>(IotMessageTypes.ACKNOWLEDGEMENT_REQUESTED, "ack:10", NOW, acknowledgement),
                "test-app",
                NOW,
                false);

        consumer.consume(delivery);

        assertThat(consumer.eventType()).isSameAs(IotMessageTypes.ACKNOWLEDGEMENT_REQUESTED);
        assertThat(consumer.consumerCode()).isEqualTo("iot.downlink.ack-requested");
        verify(acknowledgements).acknowledge(acknowledgement);
    }
}
