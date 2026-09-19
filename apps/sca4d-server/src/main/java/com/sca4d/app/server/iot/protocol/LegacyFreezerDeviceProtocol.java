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
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 处理旧冷柜的二进制 TLV、缺字段 patch、位标志枚举和不对称寄存器下行帧。
 * 厂商字段在该类中转换为物模型属性和标准业务 ACK。
 */
public final class LegacyFreezerDeviceProtocol {

    /** 示例 Product code。 */
    public static final String PRODUCT_CODE = "legacy-freezer-controller";

    /** 具体应用内协议 code。 */
    public static final String PROTOCOL_CODE = "legacy-freezer-chaotic-v1";

    private static final int STATE_SYNC = 0x7E;

    private static final int ACK_SYNC = 0x6B;

    private static final int DOWNLINK_SYNC_HIGH = 0x55;

    private static final int DOWNLINK_SYNC_LOW = 0xAA;

    private static final int TAG_POWER = 0x31;

    private static final int TAG_TEMPERATURE = 0x07;

    private static final int TAG_MODE = 0x44;

    private static final int OP_WRITE_POWER = 0x19;

    private static final int OP_SET_MODE = 0xC3;

    private static final DataKey<Boolean> POWER = DataKey.of("power", Boolean.class);

    private static final DataKey<BigDecimal> TEMPERATURE = DataKey.of("temperature", BigDecimal.class);

    private static final DataKey<String> MODE = DataKey.of("mode", String.class);

    private LegacyFreezerDeviceProtocol() {
    }

    /**
     * 创建旧冷柜二进制字段映射后的物模型版本。
     *
     * @return 设备能够稳定表达的属性和命令
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
     * 创建旧冷柜的 MQTT Topic 路由和 Core Pipeline。
     *
     * @return reported、ACK、desired 和 command 完整协议
     */
    @CheckReturnValue
    public static @NotNull MqttDeviceProtocol protocol() {
        Pipeline<byte[], DataValues> reported = Pipeline
                .of(
                        PipelineStage.of(
                                "read-legacy-reported-frame",
                                byte[].class,
                                DataOps.BytePipe.class,
                                DataOps::bytes))
                .then(
                        PipelineStage.of(
                                "decode-legacy-reported-frame",
                                DataOps.BytePipe.class,
                                DataValues.class,
                                LegacyFreezerDeviceProtocol::decodeReported));
        Pipeline<byte[], DecodedMqttAcknowledgement> acknowledgement = Pipeline
                .of(
                        PipelineStage.of(
                                "read-legacy-ack-frame",
                                byte[].class,
                                DataOps.BytePipe.class,
                                DataOps::bytes))
                .then(
                        PipelineStage.of(
                                "decode-legacy-ack-frame",
                                DataOps.BytePipe.class,
                                DecodedMqttAcknowledgement.class,
                                LegacyFreezerDeviceProtocol::decodeAcknowledgement));
        Pipeline<MqttDownlinkFrame, byte[]> downlink = Pipeline.of(
                PipelineStage.of(
                        "encode-legacy-downlink-frame",
                        MqttDownlinkFrame.class,
                        byte[].class,
                        LegacyFreezerDeviceProtocol::encodeDownlink));

        return MqttDeviceProtocol.builder(PROTOCOL_CODE)
                .reported(
                        MqttUplinkRoute.topic("factory/raw/+/S"),
                        "legacy-registers",
                        ReportedMergeMode.PATCH,
                        reported,
                        POWER,
                        TEMPERATURE,
                        MODE)
                .acknowledgement(
                        MqttUplinkRoute.topic("factory/raw/+/R"),
                        acknowledgement)
                .downlink(
                        MqttDownlinkRoute.desired("power", "factory/cmd/{identity}"),
                        downlink,
                        POWER)
                .downlink(
                        MqttDownlinkRoute.command("set-mode", "factory/cmd/{identity}"),
                        downlink,
                        MODE)
                .build();
    }

    /**
     * 解析字段顺序不固定、字段可缺失且可能出现未知 tag 的旧设备状态包。
     * 已出现的标准属性组成 patch，未出现的属性继续保留上次 reported 值。
     */
    private static @NotNull DataValues decodeReported(@NotNull DataOps.BytePipe frame) {
        frame.requireSizeBetween(5, 260, "legacy-reported-frame");
        if (frame.unsignedByte(0, "sync") != STATE_SYNC) {
            throw new IllegalArgumentException("旧冷柜状态包同步字错误");
        }
        int bodyLength = frame.unsignedByte(3, "bodyLength");
        frame.requireSize(bodyLength + 5, "legacy-reported-frame");
        if (frame.xor8(1, frame.size() - 2, "checksum") != frame.unsignedByte(frame.size() - 1, "checksum")) {
            throw new IllegalArgumentException("旧冷柜状态包 XOR 校验失败");
        }

        // 厂商固件允许任意字段顺序和未知 tag；这里只读取能映射到物模型的字段。
        DataValues.Builder values = DataValues.builder();
        boolean knownField = false;
        int cursor = 4;
        int bodyEnd = 4 + bodyLength;
        while (cursor < bodyEnd) {
            if (cursor + 2 > bodyEnd) {
                throw new IllegalArgumentException("旧冷柜 TLV 头不完整");
            }
            int tag = frame.unsignedByte(cursor++, "tag");
            int length = frame.unsignedByte(cursor++, "length");
            if (cursor + length > bodyEnd) {
                throw new IllegalArgumentException("旧冷柜 TLV 值越界");
            }
            switch (tag) {
                case TAG_POWER -> {
                    requireLength(tag, length, 1);
                    values.put(POWER, decodePower(frame.unsignedByte(cursor, "power")));
                    knownField = true;
                }
                case TAG_TEMPERATURE -> {
                    requireLength(tag, length, 2);
                    short raw = frame.readShort(cursor, ByteOrder.LITTLE_ENDIAN, "temperature");
                    values.put(TEMPERATURE, BigDecimal.valueOf(raw, 1));
                    knownField = true;
                }
                case TAG_MODE -> {
                    requireLength(tag, length, 1);
                    values.put(MODE, decodeMode(frame.unsignedByte(cursor, "mode")));
                    knownField = true;
                }
                default -> {
                    // 未登记 tag 仍按声明长度跳过，后续已知字段可以继续解析。
                }
            }
            cursor += length;
        }
        if (!knownField) {
            throw new IllegalArgumentException("旧冷柜状态包没有可识别字段");
        }
        return values.build();
    }

    /** 解析没有时间字段、使用小端 messageId 和设备响应序号的旧设备 ACK。 */
    private static @NotNull DecodedMqttAcknowledgement decodeAcknowledgement(
            @NotNull DataOps.BytePipe frame) {
        frame.requireSize(13, "legacy-ack-frame");
        if (frame.unsignedByte(0, "sync") != ACK_SYNC) {
            throw new IllegalArgumentException("旧冷柜 ACK 同步字错误");
        }
        if (frame.xor8(0, 12, "checksum") != frame.unsignedByte(12, "checksum")) {
            throw new IllegalArgumentException("旧冷柜 ACK XOR 校验失败");
        }
        long messageId = frame.readLong(1, ByteOrder.LITTLE_ENDIAN, "messageId");
        int result = frame.unsignedByte(9, "result");
        String ackCode = switch (result) {
            case 0x11 -> "success";
            case 0xEE -> "failed";
            default -> "vendor-" + Integer.toHexString(result);
        };
        int responseSequence = frame.readUnsignedShort(
                10,
                ByteOrder.LITTLE_ENDIAN,
                "responseSequence");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vendorResult", result);
        data.put("responseSequence", responseSequence);
        return new DecodedMqttAcknowledgement(
                messageId,
                ackCode,
                data,
                "legacy-" + Long.toUnsignedString(messageId) + '-' + responseSequence,
                null);
    }

    /**
     * 把 desired 和 command 编成与上行 TLV 不同的固定寄存器帧。
     * messageId 使用小端字节序，设备在 ACK 中返回相同值。
     */
    private static byte @NotNull [] encodeDownlink(@NotNull MqttDownlinkFrame frame) {
        int operation;
        int vendorValue;
        if (frame.message().kind() == DeviceDownlinkKind.DESIRED) {
            operation = OP_WRITE_POWER;
            vendorValue = frame.values().get(POWER) ? 0x0F : 0xF0;
        } else {
            operation = OP_SET_MODE;
            vendorValue = encodeMode(frame.values().get(MODE));
        }

        DataOps.ByteBuilder packet = DataOps.bytes(14)
                .writeUnsignedByte(0, DOWNLINK_SYNC_HIGH, "sync")
                .writeUnsignedByte(1, DOWNLINK_SYNC_LOW, "sync")
                .writeUnsignedByte(2, operation, "operation")
                .writeLong(3, frame.message().messageId(), ByteOrder.LITTLE_ENDIAN, "messageId")
                .writeUnsignedByte(11, 1, "length")
                .writeUnsignedByte(12, vendorValue, "value");
        int checksum = (~packet.sum8(0, 13, "checksum")) & 0xFF;
        return packet.writeUnsignedByte(13, checksum, "checksum").get();
    }

    /** 把厂商电源字节转换为标准 boolean。 */
    private static @NotNull Boolean decodePower(int value) {
        return switch (value) {
            case 0xA5 -> Boolean.TRUE;
            case 0x5A -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("旧冷柜电源字节未知");
        };
    }

    /** 把厂商位标志转换为标准 mode code。 */
    private static @NotNull String decodeMode(int flags) {
        if ((flags & 0x20) != 0) {
            return "defrost";
        }
        if ((flags & 0x08) != 0) {
            return "fan";
        }
        if ((flags & 0x02) != 0) {
            return "cool";
        }
        throw new IllegalArgumentException("旧冷柜 mode 位标志未知");
    }

    /** 把标准 mode code 转换为厂商命令值。 */
    private static int encodeMode(@NotNull String mode) {
        return switch (mode) {
            case "cool" -> 7;
            case "fan" -> 3;
            case "defrost" -> 12;
            default -> throw new IllegalArgumentException("旧冷柜不支持 mode: " + mode);
        };
    }

    /** 校验一个已知 TLV tag 使用固件规定的固定长度。 */
    private static void requireLength(int tag, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException("旧冷柜 TLV 长度错误: tag=" + tag);
        }
    }
}
