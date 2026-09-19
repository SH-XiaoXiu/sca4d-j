package com.sca4d.iot.emqx.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.pipeline.PipelineStage;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.connection.DeviceConnectionResolver;
import com.sca4d.iot.api.protocol.DeviceProtocolMessage;
import com.sca4d.iot.api.protocol.DeviceUplinkDispatcher;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.emqx.connection.MqttDeviceConnection;
import com.sca4d.iot.emqx.protocol.MqttDeviceProtocol;
import com.sca4d.iot.emqx.protocol.MqttProtocolCatalog;
import com.sca4d.iot.emqx.protocol.MqttUplinkRoute;
import com.sca4d.iot.emqx.protocol.PrefixedMqttTopicIdentityMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 验证 MQTT 路由只定位 Device，并把实际协议选择留给 IoT 分发器。 */
class MqttMessageRouterTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-15T05:00:00Z");

    @Test
    @DisplayName("标准 MQTT 路由把 Topic 身份转换为 Client ID 后只提交 Device 标识")
    void routesDeviceIdWithoutSelectingDeviceProtocol() {
        DataKey<Boolean> online = DataKey.of("online", Boolean.class);
        MqttDeviceProtocol mqttProtocol = MqttDeviceProtocol.builder("gateway-mqtt")
                .topicIdentity(new PrefixedMqttTopicIdentityMapper("gateway-"))
                .reported(
                        MqttUplinkRoute.topic("vendor/gateways/+/info"),
                        "info",
                        ReportedMergeMode.PATCH,
                        Pipeline.of(
                                PipelineStage.of(
                                        "decode-info",
                                        byte[].class,
                                        DataValues.class,
                                        payload -> DataValues.builder().put(online, true).build())),
                        online)
                .build();
        Device device = new Device(
                91,
                ProductBinding.code("gateway"),
                "00124b0001a2b3c4",
                null,
                "Gateway",
                null,
                1,
                1,
                "device-protocol-from-ledger",
                MqttDeviceConnection.clientId("gateway-00124b0001a2b3c4"),
                Set.of(DeviceAccessMethod.anonymous()));
        DeviceConnectionResolver devices = mock(DeviceConnectionResolver.class);
        when(devices.findDevice(MqttDeviceConnection.clientId("gateway-00124b0001a2b3c4")))
                .thenReturn(Optional.of(device));
        DeviceUplinkDispatcher uplinks = mock(DeviceUplinkDispatcher.class);
        MqttMessageRouter router = new MqttMessageRouter(
                new MqttProtocolCatalog(List.of(mqttProtocol)),
                devices,
                uplinks,
                List.of());

        router.dispatch(
                new MqttMessage(
                        "vendor/gateways/00124b0001a2b3c4/info",
                        new byte[] {0x01},
                        OBSERVED_AT,
                        1,
                        false));

        ArgumentCaptor<DeviceProtocolMessage> message = ArgumentCaptor.forClass(DeviceProtocolMessage.class);
        verify(uplinks).dispatch(message.capture());
        assertThat(message.getValue().device()).isSameAs(device);
        assertThat(message.getValue().deviceId()).isEqualTo(91);
        assertThat(message.getValue().code()).isEqualTo("vendor/gateways/+/info");
        assertThat(message.getValue().payload()).containsExactly(0x01);
        assertThat(message.getValue().observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    @DisplayName("应用 MQTT 路由接收匹配 Topic 的原始消息")
    void dispatchesApplicationRoute() {
        MqttMessageRoute applicationRoute = mock(MqttMessageRoute.class);
        when(applicationRoute.topicFilter()).thenReturn("vendor/gateways/+/frame");
        MqttMessageRouter router = new MqttMessageRouter(
                new MqttProtocolCatalog(List.of()),
                mock(DeviceConnectionResolver.class),
                mock(DeviceUplinkDispatcher.class),
                List.of(applicationRoute));
        MqttMessage message = new MqttMessage(
                "vendor/gateways/gateway-1/frame",
                new byte[] {0x01, 0x02},
                OBSERVED_AT,
                2,
                true);

        router.dispatch(message);

        verify(applicationRoute).route(message);
    }

    @Test
    @DisplayName("重叠 Topic Filter 在安装订阅前拒绝启动")
    void rejectsOverlappingTopicFilters() {
        MqttMessageRoute broad = mock(MqttMessageRoute.class);
        MqttMessageRoute specific = mock(MqttMessageRoute.class);
        when(broad.topicFilter()).thenReturn("vendor/+/frame");
        when(specific.topicFilter()).thenReturn("vendor/gateways/#");

        assertThatThrownBy(
                () -> new MqttMessageRouter(
                        new MqttProtocolCatalog(List.of()),
                        mock(DeviceConnectionResolver.class),
                        mock(DeviceUplinkDispatcher.class),
                        List.of(broad, specific)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("存在重叠路由");
    }

    @Test
    @DisplayName("应用路由必须明确声明输入拒绝而不是由 Router 猜测异常")
    void propagatesUnclassifiedApplicationFailure() {
        MqttMessageRoute applicationRoute = mock(MqttMessageRoute.class);
        when(applicationRoute.topicFilter()).thenReturn("vendor/gateways/+/frame");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("application failure"))
                .when(applicationRoute)
                .route(org.mockito.ArgumentMatchers.any());
        MqttMessageRouter router = new MqttMessageRouter(
                new MqttProtocolCatalog(List.of()),
                mock(DeviceConnectionResolver.class),
                mock(DeviceUplinkDispatcher.class),
                List.of(applicationRoute));

        assertThatThrownBy(
                () -> router.dispatch(
                        new MqttMessage(
                                "vendor/gateways/gateway-1/frame",
                                new byte[] {0x01},
                                OBSERVED_AT,
                                1,
                                false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("application failure");
    }
}
