package com.sca4d.iot.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.core.api.connection.ConnectionRegistry;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.JacksonMessageCodec;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.retry.RetryContext;
import com.sca4d.core.api.retry.RetryPolicy;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.api.connection.DeviceConnectionReceiver;
import com.sca4d.iot.api.connection.DeviceDisconnectMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Instant;

/** 验证可靠断连同时终止协议连接和全部通用连接投影。 */
class DeviceDisconnectHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-24T11:00:00Z");

    private static final DeviceConnectionProtocol MQTT = new DeviceConnectionProtocol("mqtt");

    private static final DeviceConnectionId CONNECTION_ID = new DeviceConnectionId("connection-71");

    private final DeviceConnectionTerminatorRouter terminators = mock(DeviceConnectionTerminatorRouter.class);

    private final DeviceConnectionReceiver connections = mock(DeviceConnectionReceiver.class);

    private final ConnectionRegistry commonConnections = mock(ConnectionRegistry.class);

    private final DeviceDisconnectHandler consumer = new DeviceDisconnectHandler(
            terminators,
            connections,
            commonConnections,
            new JacksonMessageCodec<>(
                    DeviceDisconnectMessage.CODE,
                    DeviceDisconnectMessage.PROTOCOL_VERSION,
                    DeviceDisconnectMessage.class,
                    new ObjectMapper()));

    @Test
    @DisplayName("协议连接终止后立即删除 Device 和公共连接投影")
    void removesEveryProjectionAfterProtocolTermination() {
        when(connections.disconnected(MQTT, CONNECTION_ID)).thenReturn(true);

        consumer.handle(delivery());

        InOrder order = inOrder(terminators, connections, commonConnections);
        order.verify(terminators).disconnect(MQTT, CONNECTION_ID, "identity-unavailable");
        order.verify(connections).disconnected(MQTT, CONNECTION_ID);
        order.verify(commonConnections).remove(new ConnectionId(CONNECTION_ID.value()));
    }

    @Test
    @DisplayName("重复断连消息可以重复清理已经不存在的投影")
    void keepsRepeatedDisconnectIdempotent() {
        when(connections.disconnected(MQTT, CONNECTION_ID)).thenReturn(false);

        consumer.handle(delivery());

        verify(terminators).disconnect(MQTT, CONNECTION_ID, "identity-unavailable");
        verify(connections).disconnected(MQTT, CONNECTION_ID);
        verify(commonConnections).remove(new ConnectionId(CONNECTION_ID.value()));
    }

    @Test
    @DisplayName("协议终止失败时保留投影，并由 Messaging 决定下一次投递")
    void retriesWithoutRemovingProjectionWhenTerminationFails() {
        Mockito.doThrow(new IllegalStateException("offline"))
                .when(terminators)
                .disconnect(MQTT, CONNECTION_ID, "identity-unavailable");
        var decision = consumer.handle(delivery());
        assertThat(decision.status()).isEqualTo(HandlingDecision.Status.RETRY);
        verify(connections, never()).disconnected(any(), any());
        verify(commonConnections, never()).remove(any());
    }

    @Test
    @DisplayName("旧消息的尝试次数继续计入断连额度")
    void preservesLegacyAttemptCount() {
        Mockito.doThrow(new IllegalStateException("offline"))
                .when(terminators)
                .disconnect(MQTT, CONNECTION_ID, "identity-unavailable");
        var original = delivery();
        var legacy = new DeviceDisconnectMessage(71L, CONNECTION_ID, MQTT, "identity-unavailable", 20);
        var decision = consumer.handle(
                new IncomingMessage<>(original.messageId(), legacy, original.sourceApplication(), NOW, false, 1));
        assertThat(decision.completedAttempts()).isEqualTo(20);
        assertThat(
                RetryPolicy.always(consumer.retryRule())
                        .evaluate(RetryContext.failed(decision.completedAttempts(), "offline"))
                        .retrying())
                .isFalse();
    }

    /** 创建第一次断连尝试使用的可靠消息投递。 */
    private static IncomingMessage<DeviceDisconnectMessage> delivery() {
        DeviceDisconnectMessage payload = new DeviceDisconnectMessage(
                71L,
                CONNECTION_ID,
                MQTT,
                "identity-unavailable",
                1);
        return new IncomingMessage<>(
                Id.of(80L),
                payload,
                new Name("TEST_APP"),
                NOW,
                false,
                1);
    }
}
