package com.sca4d.app.server.iot.protocol;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.pipeline.Pipeline;
import com.sca4d.core.api.pipeline.PipelineStage;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.model.ThingCommand;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.model.ThingPropertyAccess;
import com.sca4d.iot.api.model.ThingValueType;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.emqx.protocol.DecodedMqttAcknowledgement;
import com.sca4d.iot.emqx.protocol.MqttDeviceProtocol;
import com.sca4d.iot.emqx.protocol.MqttDownlinkFrame;
import com.sca4d.iot.emqx.protocol.MqttDownlinkRoute;
import com.sca4d.iot.emqx.protocol.MqttUplinkRoute;
import com.sca4d.iot.message.policy.DefaultCommandRetryPolicy;
import com.sca4d.iot.message.policy.DefaultStateSyncRetryPolicy;
import com.sca4d.iot.message.policy.DeviceResultAcknowledgementPolicy;
import com.sca4d.iot.message.policy.ReportedEqualsDesiredAcknowledgementPolicy;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * 定义规范温控器的稳定 JSON 信封，并把 JSON 字段映射为标准物模型 code。
 * 当前协议负责该设备的 Topic、字段名称和信封版本。
 */
public class StandardClimateDeviceProtocol {

    /** 示例 Product code。 */
    public static final String PRODUCT_CODE = "standard-climate-controller";

    /** 具体应用内协议 code。 */
    public static final String PROTOCOL_CODE = "standard-climate-json-v1";

    private static final DataKey<Boolean> POWER = DataKey.of("power", Boolean.class);

    private static final DataKey<BigDecimal> TEMPERATURE = DataKey.of("temperature", BigDecimal.class);

    private static final DataKey<String> MODE = DataKey.of("mode", String.class);

    private StandardClimateDeviceProtocol() {
    }

    /**
     * 创建规范温控器的物模型版本。
     *
     * @return 与数据库 JSON 发布结果结构相同的物模型
     */
    @CheckReturnValue
    public static @NotNull ThingModel thingModel() {
        return ThingModel.builder(PRODUCT_CODE, 1)
                .property(
                        ThingProperty.builder("power", ThingValueType.booleanType())
                                .access(ThingPropertyAccess.READ_WRITE)
                                .desiredState(
                                        new ReportedEqualsDesiredAcknowledgementPolicy(),
                                        new DefaultStateSyncRetryPolicy())
                                .build())
                .property(
                        ThingProperty.builder(
                                "temperature",
                                ThingValueType.decimalType()
                                        .minimum("-40")
                                        .maximum("100")
                                        .scale(1)
                                        .unit("celsius")
                                        .build())
                                .access(ThingPropertyAccess.READ_ONLY)
                                .telemetry(true)
                                .build())
                .property(
                        ThingProperty.builder(
                                "mode",
                                ThingValueType.enumType()
                                        .value("cool")
                                        .value("fan")
                                        .value("defrost")
                                        .build())
                                .access(ThingPropertyAccess.READ_ONLY)
                                .build())
                .command(
                        ThingCommand.builder("set-mode")
                                .input(
                                        MODE,
                                        ThingValueType.enumType()
                                                .value("cool")
                                                .value("fan")
                                                .value("defrost")
                                                .build())
                                .acknowledgementPolicy(new DeviceResultAcknowledgementPolicy())
                                .retryPolicy(new DefaultCommandRetryPolicy())
                                .build())
                .build();
    }

    /**
     * 创建规范温控器的 MQTT Topic 路由和 Core Pipeline。
     *
     * @return reported、ACK、desired 和 command 完整协议
     */
    @CheckReturnValue
    public static @NotNull MqttDeviceProtocol protocol() {
        Pipeline<byte[], DataValues> reported = Pipeline
                .of(
                        PipelineStage.of(
                                "parse-standard-reported-json",
                                byte[].class,
                                DataOps.JsonPipe.class,
                                payload -> DataOps.json(payload).req("reported")))
                .then(
                        PipelineStage.of(
                                "map-standard-reported-fields",
                                DataOps.JsonPipe.class,
                                DataValues.class,
                                StandardClimateDeviceProtocol::decodeReported));
        Pipeline<byte[], DecodedMqttAcknowledgement> acknowledgement = Pipeline
                .of(
                        PipelineStage.of(
                                "parse-standard-ack-json",
                                byte[].class,
                                DataOps.JsonPipe.class,
                                payload -> DataOps.json(payload).req("acknowledgement")))
                .then(
                        PipelineStage.of(
                                "map-standard-ack-fields",
                                DataOps.JsonPipe.class,
                                DecodedMqttAcknowledgement.class,
                                StandardClimateDeviceProtocol::decodeAcknowledgement));
        Pipeline<MqttDownlinkFrame, byte[]> downlink = Pipeline.of(
                PipelineStage.of(
                        "encode-standard-downlink-json",
                        MqttDownlinkFrame.class,
                        byte[].class,
                        StandardClimateDeviceProtocol::encodeDownlink));

        return MqttDeviceProtocol.builder(PROTOCOL_CODE)
                .reported(
                        MqttUplinkRoute.topic("demo/standard/+/reported"),
                        "reported",
                        ReportedMergeMode.SNAPSHOT,
                        reported,
                        POWER,
                        TEMPERATURE,
                        MODE)
                .acknowledgement(
                        MqttUplinkRoute.topic("demo/standard/+/ack"),
                        acknowledgement)
                .downlink(
                        MqttDownlinkRoute.desired(
                                "power",
                                "demo/standard/{identity}/downlink"),
                        downlink,
                        POWER)
                .downlink(
                        MqttDownlinkRoute.command(
                                "set-mode",
                                "demo/standard/{identity}/downlink"),
                        downlink,
                        MODE)
                .build();
    }

    /** 把 schemaVersion=1 的 reported JSON 快照转换为物模型字段。 */
    private static @NotNull DataValues decodeReported(@NotNull DataOps.JsonPipe root) {
        if (root.requiredInteger("schemaVersion") != 1) {
            throw new IllegalArgumentException("规范设备 reported schemaVersion 不支持");
        }
        requiredInstant(root, "reportedAt");

        // 规范设备声明完整快照，三个标准属性缺少任何一个都拒绝当前报文。
        return DataValues.builder()
                .put(POWER, root.requiredBoolean("properties.power"))
                .put(TEMPERATURE, root.requiredDecimal("properties.temperature"))
                .put(MODE, root.requiredText("properties.mode"))
                .build();
    }

    /** 把规范 JSON 回执转换为标准业务 ACK。 */
    private static @NotNull DecodedMqttAcknowledgement decodeAcknowledgement(
            @NotNull DataOps.JsonPipe root) {
        if (root.requiredInteger("schemaVersion") != 1) {
            throw new IllegalArgumentException("规范设备 ACK schemaVersion 不支持");
        }
        return new DecodedMqttAcknowledgement(
                root.requiredLong("messageId"),
                root.requiredText("ackCode"),
                root.requiredObject("data").objectValues(),
                root.requiredText("ackId"),
                requiredInstant(root, "acknowledgedAt"));
    }

    /** 把标准消息元数据和值编码为上下行字段对称的 JSON 信封。 */
    private static byte @NotNull [] encodeDownlink(@NotNull MqttDownlinkFrame frame) {
        Map<String, Object> data = new LinkedHashMap<>();
        frame.values().asMap().forEach((key, value) -> data.put(key.name(), value));

        DataOps.JsonObjectPipe json = DataOps.obj()
                .put("schemaVersion", 1)
                .put("messageId", frame.message().messageId())
                .put(
                        "kind",
                        frame.message().kind() == DeviceDownlinkKind.DESIRED
                                ? "desired"
                                : "command")
                .put("targetCode", frame.message().targetCode())
                .put("data", data);
        OptionalLong desiredSequence = frame.message().desiredSequence();
        if (desiredSequence.isPresent()) {
            json = json.put("desiredSequence", desiredSequence.getAsLong());
        }
        return json.bytes();
    }

    /** 读取 ISO-8601 时间；字段格式错误时拒绝当前报文。 */
    private static @NotNull Instant requiredInstant(
            @NotNull DataOps.JsonPipe root,
            @NotNull String field) {
        try {
            return Instant.parse(root.requiredText(field));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("JSON 字段必须是 ISO-8601 时间: " + field, exception);
        }
    }
}
