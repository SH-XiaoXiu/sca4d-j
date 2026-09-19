package com.sca4d.iot.connection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.messaging.MessagePublisher;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceConnectionReceiver;
import com.sca4d.iot.api.connection.DeviceDisconnectMessage;
import com.sca4d.iot.api.message.IotMessageTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** 验证可靠断连同时终止协议连接和全部通用连接投影。 */
class DeviceDisconnectConsumerTest {

    private static final Instant NOW = Instant.parse("2026-08-24T11:00:00Z");

    private static final DeviceConnectionProtocol MQTT = new DeviceConnectionProtocol("mqtt");

    private static final DeviceConnectionId CONNECTION_ID = new DeviceConnectionId("connection-71");

    private final DeviceConnectionTerminatorRouter terminators = mock(DeviceConnectionTerminatorRouter.class);

    private final DeviceConnectionReceiver connections = mock(DeviceConnectionReceiver.class);

    private final MessagePublisher messages = mock(MessagePublisher.class);

    private final ConnectionRegistry commonConnections = mock(ConnectionRegistry.class);

    private final DeviceDisconnectConsumer consumer = new DeviceDisconnectConsumer(
            terminators,
            connections,
            messages,
            Clock.fixed(NOW, ZoneOffset.UTC),
            commonConnections);

    @Test
    @DisplayName("协议连接终止后立即删除 Device 和公共连接投影")
    void removesEveryProjectionAfterProtocolTermination() {
        when(connections.disconnected(MQTT, CONNECTION_ID)).thenReturn(true);

        consumer.consume(delivery());

        InOrder order = inOrder(terminators, connections, commonConnections);
        order.verify(terminators).disconnect(MQTT, CONNECTION_ID, "identity-unavailable");
        order.verify(connections).disconnected(MQTT, CONNECTION_ID);
        order.verify(commonConnections).remove(new ConnectionId(CONNECTION_ID.value()));
        verify(messages, never()).publishReliably(any(), any());
    }

    @Test
    @DisplayName("重复断连消息可以重复清理已经不存在的投影")
    void keepsRepeatedDisconnectIdempotent() {
        when(connections.disconnected(MQTT, CONNECTION_ID)).thenReturn(false);

        consumer.consume(delivery());

        verify(terminators).disconnect(MQTT, CONNECTION_ID, "identity-unavailable");
        verify(connections).disconnected(MQTT, CONNECTION_ID);
        verify(commonConnections).remove(new ConnectionId(CONNECTION_ID.value()));
        verify(messages, never()).publishReliably(any(), any());
    }

    @Test
    @DisplayName("协议终止失败时不提前删除投影并登记延迟重试")
    void retriesWithoutRemovingProjectionWhenTerminationFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(terminators)
                .disconnect(MQTT, CONNECTION_ID, "identity-unavailable");
        when(messages.publishReliably(any(), eq(NOW.plusSeconds(1))))
                .thenReturn(new MessageId(90L));

        consumer.consume(delivery());

        verify(connections, never()).disconnected(MQTT, CONNECTION_ID);
        verify(commonConnections, never()).remove(any());
        verify(messages).publishReliably(any(), eq(NOW.plusSeconds(1)));
    }

    @Test
    @DisplayName("投影删除失败时重新执行协议终止和投影删除")
    void retriesTerminationAndProjectionRemovalWhenProjectionRemovalFails() {
        when(connections.disconnected(MQTT, CONNECTION_ID))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(messages.publishReliably(any(), eq(NOW.plusSeconds(1))))
                .thenReturn(new MessageId(91L));

        consumer.consume(delivery());

        verify(terminators).disconnect(MQTT, CONNECTION_ID, "identity-unavailable");
        verify(commonConnections, never()).remove(any());
        verify(messages).publishReliably(any(), eq(NOW.plusSeconds(1)));
    }

    /** 创建第一次断连尝试使用的可靠消息投递。 */
    private static MessageDelivery<DeviceDisconnectMessage> delivery() {
        DeviceDisconnectMessage payload = new DeviceDisconnectMessage(
                71L,
                CONNECTION_ID,
                MQTT,
                "identity-unavailable",
                1);
        return new MessageDelivery<>(
                new MessageId(80L),
                new Event<>(IotMessageTypes.DEVICE_DISCONNECT, "disconnect:71", NOW, payload),
                "test-app",
                NOW,
                false);
    }
}
