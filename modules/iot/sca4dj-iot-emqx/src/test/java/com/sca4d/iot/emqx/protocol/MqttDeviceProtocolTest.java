package com.sca4d.iot.emqx.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.pipeline.PipelineStage;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.model.ThingValueType;
import com.sca4d.iot.api.protocol.DeviceProtocolMessage;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.emqx.connection.MqttDeviceConnection;
import com.sca4d.iot.emqx.transport.MqttMessageRejectedException;
import com.sca4d.iot.emqx.transport.MqttMessageRejectionReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

/** 验证 MQTT 路由身份提取和 DataKey 到物模型的精确类型检查。 */
class MqttDeviceProtocolTest {

    @Test
    @DisplayName("Client ID 前缀与 Topic 身份可逆转换")
    void mapsClientIdAndTopicIdentity() {
        DataKey<Boolean> power = DataKey.of("power", Boolean.class);
        MqttUplinkRoute route = MqttUplinkRoute.topic("demo/ac/+/reported");
        MqttDeviceProtocol protocol = MqttDeviceProtocol.builder("demo")
                .topicIdentity(new PrefixedMqttTopicIdentityMapper("gateway-"))
                .reported(
                        route,
                        "reported",
                        ReportedMergeMode.PATCH,
                        Pipeline.of(
                                PipelineStage.of(
                                        "decode-reported",
                                        byte[].class,
                                        DataValues.class,
                                        payload -> DataValues.builder().put(power, true).build())),
                        power)
                .build();
        ThingModel model = ThingModel.builder("air-conditioner", 1)
                .property(ThingProperty.builder("power", ThingValueType.booleanType()).build())
                .build();

        protocol.validate(model);

        assertThat(route.topicIdentity("demo/ac/device-7/reported")).isEqualTo("device-7");
        assertThat(protocol.clientId("device-7")).isEqualTo("gateway-device-7");
        assertThat(protocol.deviceUplinkTopicFilters("gateway-device-7"))
                .containsExactly("demo/ac/device-7/reported");
    }

    @Test
    @DisplayName("拒绝含糊身份路由和错误 Java 类型")
    void rejectsAmbiguousIdentityRoutesAndWrongJavaTypes() {
        assertThatThrownBy(() -> assertThat(MqttUplinkRoute.topic("tenant/+/device/+/reported")).isNotNull())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("明确设备身份 level");

        DataKey<String> wrongPower = DataKey.of("power", String.class);
        MqttDeviceProtocol protocol = MqttDeviceProtocol.builder("wrong-type")
                .reported(
                        MqttUplinkRoute.topic("demo/+/reported"),
                        "reported",
                        ReportedMergeMode.PATCH,
                        Pipeline.of(
                                PipelineStage.of(
                                        "decode-reported",
                                        byte[].class,
                                        DataValues.class,
                                        payload -> DataValues.builder().put(wrongPower, "on").build())),
                        wrongPower)
                .build();
        ThingModel model = ThingModel.builder("air-conditioner", 1)
                .property(ThingProperty.builder("power", ThingValueType.booleanType()).build())
                .build();

        assertThatThrownBy(() -> protocol.validate(model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Java 类型与物模型不一致");
    }

    @Test
    @DisplayName("把上行 Pipeline 的 Payload 校验失败标记为不可恢复消息")
    void classifiesPayloadDecodingFailure() {
        DataKey<Boolean> power = DataKey.of("power", Boolean.class);
        MqttUplinkRoute route = MqttUplinkRoute.topic("demo/+/reported");
        MqttDeviceProtocol protocol = MqttDeviceProtocol.builder("demo")
                .reported(
                        route,
                        "reported",
                        ReportedMergeMode.PATCH,
                        Pipeline.of(
                                PipelineStage.of(
                                        "decode-reported",
                                        byte[].class,
                                        DataValues.class,
                                        payload -> {
                                            throw new IllegalArgumentException("invalid payload");
                                        })),
                        power)
                .build();
        Device device = new Device(
                91,
                ProductBinding.code("gateway"),
                "device-91",
                null,
                "Gateway",
                null,
                1,
                1,
                "demo",
                MqttDeviceConnection.clientId("device-91"),
                Set.of(DeviceAccessMethod.anonymous()));

        assertThatThrownBy(
                () -> protocol.decode(
                        device,
                        new DeviceProtocolMessage(
                                device,
                                route.topicFilter(),
                                new byte[] {0x01},
                                Instant.parse("2026-09-08T01:00:00Z"))))
                .isInstanceOfSatisfying(
                        MqttMessageRejectedException.class,
                        failure -> assertThat(failure.reason())
                                .isEqualTo(MqttMessageRejectionReason.MESSAGE_INVALID));
    }
}
