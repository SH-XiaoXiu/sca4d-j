package com.sca4d.app.server.iot.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sca4d.core.api.data.DataOps;
import com.sca4d.core.api.pipeline.PipelineExecutionException;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.message.DeviceUplink;
import com.sca4d.iot.api.protocol.DeviceProtocolMessage;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import com.sca4d.iot.emqx.connection.MqttDeviceConnection;
import com.sca4d.iot.emqx.protocol.EncodedMqttMessage;
import com.sca4d.iot.emqx.protocol.MqttDeviceProtocol;
import com.sca4d.iot.message.policy.DefaultCommandRetryPolicy;
import com.sca4d.iot.message.policy.DefaultStateSyncRetryPolicy;
import com.sca4d.iot.message.policy.DeviceResultAcknowledgementPolicy;
import com.sca4d.iot.message.policy.ReportedEqualsDesiredAcknowledgementPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** 验证示例应用的规范 JSON 设备和旧式二进制设备协议。 */
class ExampleDeviceProtocolTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    @Test
    @DisplayName("规范设备把完整 JSON 快照映射为物模型属性")
    void decodesStandardReportedSnapshot() {
        MqttDeviceProtocol protocol = StandardClimateDeviceProtocol.protocol();
        protocol.validate(StandardClimateDeviceProtocol.thingModel());
        byte[] payload = ("""
                {
                  "schemaVersion": 1,
                  "sequence": 42,
                  "reportedAt": "2026-08-02T11:59:58Z",
                  "properties": {
                    "power": true,
                    "temperature": 23.4,
                    "mode": "cool"
                  }
                }
                """).getBytes(StandardCharsets.UTF_8);

        ReportedStateUpdate update = decode(
                protocol,
                "demo/standard/+/reported",
                payload,
                "climate-1")
                .reportedState()
                .orElseThrow();

        assertThat(update.groupCode()).isEqualTo("reported");
        assertThat(update.mergeMode()).isEqualTo(ReportedMergeMode.SNAPSHOT);
        assertThat(update.coveredProperties())
                .containsExactlyInAnyOrder("power", "temperature", "mode");
        assertThat(update.values())
                .containsEntry("power", true)
                .containsEntry("temperature", new BigDecimal("23.4"))
                .containsEntry("mode", "cool");
        assertThat(update.observedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("规范设备解析业务 ACK 并编码 desired 和 command")
    void processesStandardAcknowledgementAndDownlinks() {
        MqttDeviceProtocol protocol = StandardClimateDeviceProtocol.protocol();
        byte[] acknowledgementPayload = ("""
                {
                  "schemaVersion": 1,
                  "messageId": 101,
                  "ackId": "climate-1-ack-101",
                  "ackCode": "success",
                  "acknowledgedAt": "2026-08-02T12:00:01Z",
                  "data": {"targetCode": "power"}
                }
                """).getBytes(StandardCharsets.UTF_8);

        DeviceAcknowledgement acknowledgement = decode(
                protocol,
                "demo/standard/+/ack",
                acknowledgementPayload,
                "climate-1")
                .acknowledgement()
                .orElseThrow();
        EncodedMqttMessage desired = protocol.encode(desiredMessage(), "climate-1");
        EncodedMqttMessage command = protocol.encode(commandMessage(102, "fan"), "climate-1");

        assertThat(acknowledgement.messageId()).isEqualTo(101);
        assertThat(acknowledgement.ackCode()).isEqualTo("success");
        assertThat(acknowledgement.sourceAcknowledgementId())
                .isEqualTo("climate-1-ack-101");
        assertThat(acknowledgement.acknowledgedAt())
                .isEqualTo(Instant.parse("2026-08-02T12:00:01Z"));
        assertThat(acknowledgement.data()).containsEntry("targetCode", "power");

        assertThat(desired.topic()).isEqualTo("demo/standard/climate-1/downlink");
        DataOps.JsonPipe desiredJson = DataOps.json(desired.payload()).req("desired-downlink");
        assertThat(desiredJson.requiredLong("messageId")).isEqualTo(101);
        assertThat(desiredJson.requiredText("kind")).isEqualTo("desired");
        assertThat(desiredJson.requiredText("targetCode")).isEqualTo("power");
        assertThat(desiredJson.requiredLong("desiredSequence")).isEqualTo(7);
        assertThat(desiredJson.requiredBoolean("data.power")).isFalse();

        assertThat(command.topic()).isEqualTo("demo/standard/climate-1/downlink");
        DataOps.JsonPipe commandJson = DataOps.json(command.payload()).req("command-downlink");
        assertThat(commandJson.requiredLong("messageId")).isEqualTo(102);
        assertThat(commandJson.requiredText("kind")).isEqualTo("command");
        assertThat(commandJson.requiredText("targetCode")).isEqualTo("set-mode");
        assertThat(commandJson.requiredText("data.mode")).isEqualTo("fan");
        assertThat(commandJson.objectValues()).doesNotContainKey("desiredSequence");
    }

    @Test
    @DisplayName("旧冷柜跳过未知 tag 并把残缺 TLV 作为属性 patch")
    void decodesLegacyPartialReportedFrame() {
        MqttDeviceProtocol protocol = LegacyFreezerDeviceProtocol.protocol();
        protocol.validate(LegacyFreezerDeviceProtocol.thingModel());

        ReportedStateUpdate update = decode(
                protocol,
                "factory/raw/+/S",
                legacyTemperatureFrame(),
                "freezer-1")
                .reportedState()
                .orElseThrow();

        assertThat(update.groupCode()).isEqualTo("legacy-registers");
        assertThat(update.mergeMode()).isEqualTo(ReportedMergeMode.PATCH);
        assertThat(update.coveredProperties())
                .containsExactlyInAnyOrder("power", "temperature", "mode");
        assertThat(update.values())
                .containsOnly(Map.entry("temperature", new BigDecimal("-18.7")));
    }

    @Test
    @DisplayName("旧冷柜解析二进制 ACK 并编码固定寄存器下行帧")
    void processesLegacyAcknowledgementAndDownlink() {
        MqttDeviceProtocol protocol = LegacyFreezerDeviceProtocol.protocol();

        DeviceAcknowledgement acknowledgement = decode(
                protocol,
                "factory/raw/+/R",
                legacyAcknowledgement(),
                "freezer-1")
                .acknowledgement()
                .orElseThrow();
        EncodedMqttMessage command = protocol.encode(commandMessage(202, "defrost"), "freezer-1");
        byte[] packet = command.payload();

        assertThat(acknowledgement.messageId()).isEqualTo(201);
        assertThat(acknowledgement.ackCode()).isEqualTo("success");
        assertThat(acknowledgement.data())
                .containsEntry("vendorResult", 0x11)
                .containsEntry("responseSequence", 23);
        assertThat(acknowledgement.sourceAcknowledgementId())
                .isEqualTo("legacy-201-23");
        assertThat(acknowledgement.acknowledgedAt()).isEqualTo(NOW);

        assertThat(command.topic()).isEqualTo("factory/cmd/freezer-1");
        assertThat(packet).hasSize(14);
        assertThat(unsigned(packet[0])).isEqualTo(0x55);
        assertThat(unsigned(packet[1])).isEqualTo(0xAA);
        assertThat(unsigned(packet[2])).isEqualTo(0xC3);
        assertThat(
                ByteBuffer.wrap(packet, 3, Long.BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getLong())
                .isEqualTo(202);
        assertThat(unsigned(packet[11])).isEqualTo(1);
        assertThat(unsigned(packet[12])).isEqualTo(12);
        assertThat(unsigned(packet[13])).isEqualTo(complementSum(packet));
    }

    @Test
    @DisplayName("旧冷柜坏帧报告失败的 Core Pipeline 阶段")
    void reportsLegacyPipelineStageForMalformedFrame() {
        MqttDeviceProtocol protocol = LegacyFreezerDeviceProtocol.protocol();
        Device device = device(protocol, "freezer-1");

        assertThatExceptionOfType(PipelineExecutionException.class)
                .isThrownBy(
                        () -> protocol
                                .decode(
                                        device,
                                        new DeviceProtocolMessage(
                                                device,
                                                "factory/raw/+/S",
                                                new byte[] {0, 0, 0, 0, 0},
                                                NOW)))
                .satisfies(
                        failure -> assertThat(failure.stageCode())
                                .isEqualTo("decode-legacy-reported-frame"));
    }

    /** 使用传输路由已经定位的 Device 和消息类型调用设备协议。 */
    private static DeviceUplink decode(
            MqttDeviceProtocol protocol,
            String messageCode,
            byte[] payload,
            String clientId) {
        Device device = device(protocol, clientId);
        return protocol.decode(
                device,
                new DeviceProtocolMessage(device, messageCode, payload, NOW));
    }

    /** 创建与待测 MQTT 协议绑定的直接连接 Device。 */
    private static Device device(MqttDeviceProtocol protocol, String clientId) {
        return new Device(
                1,
                ProductBinding.code("example-product"),
                "example-device",
                null,
                "示例 Device",
                null,
                1,
                1,
                protocol.code(),
                MqttDeviceConnection.clientId(clientId),
                Set.of(DeviceAccessMethod.anonymous()));
    }

    /** 创建一个等待发送的 desired 属性消息。 */
    private static DeviceDownlink desiredMessage() {
        return new DeviceDownlink(
                101,
                1,
                1,
                DeviceDownlinkKind.DESIRED,
                "power",
                7L,
                null,
                null,
                Map.of("value", false),
                new ReportedEqualsDesiredAcknowledgementPolicy(),
                Duration.ofSeconds(30),
                new DefaultStateSyncRetryPolicy(),
                DeviceDownlinkStatus.PENDING,
                0,
                null,
                NOW);
    }

    /** 创建一个等待发送的 set-mode command。 */
    private static DeviceDownlink commandMessage(long messageId, String mode) {
        return new DeviceDownlink(
                messageId,
                1,
                1,
                DeviceDownlinkKind.COMMAND,
                "set-mode",
                null,
                null,
                null,
                Map.of("mode", mode),
                new DeviceResultAcknowledgementPolicy(),
                Duration.ofSeconds(30),
                new DefaultCommandRetryPolicy(),
                DeviceDownlinkStatus.PENDING,
                0,
                null,
                NOW);
    }

    /** 构造包含未知 tag 和温度字段的旧冷柜状态帧。 */
    private static byte[] legacyTemperatureFrame() {
        byte[] packet = new byte[12];
        packet[0] = 0x7E;
        packet[1] = 0x13;
        packet[2] = 0x01;
        packet[3] = 7;
        packet[4] = 0x77;
        packet[5] = 1;
        packet[6] = 9;
        packet[7] = 0x07;
        packet[8] = 2;
        ByteBuffer.wrap(packet, 9, Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) -187);
        packet[11] = (byte) xor(packet, 1, 11);
        return packet;
    }

    /** 构造包含小端 messageId、结果码和响应序号的旧冷柜 ACK。 */
    private static byte[] legacyAcknowledgement() {
        byte[] packet = new byte[13];
        packet[0] = 0x6B;
        ByteBuffer.wrap(packet, 1, Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(201);
        packet[9] = 0x11;
        ByteBuffer.wrap(packet, 10, Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 23);
        packet[12] = (byte) xor(packet, 0, 12);
        return packet;
    }

    /** 计算指定半开区间字节的异或值。 */
    private static int xor(byte[] values, int startInclusive, int endExclusive) {
        int result = 0;
        for (int index = startInclusive; index < endExclusive; index++) {
            result ^= unsigned(values[index]);
        }
        return result;
    }

    /** 计算指定半开区间字节累加和的八位反码。 */
    private static int complementSum(byte[] values) {
        int sum = 0;
        for (int index = 0; index < 13; index++) {
            sum = (sum + unsigned(values[index])) & 0xFF;
        }
        return (~sum) & 0xFF;
    }

    /** 返回一个 byte 的无符号值。 */
    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }
}
