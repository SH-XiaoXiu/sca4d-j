package com.sca4d.app.iot.simulator;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

/** 模拟字段残缺、顺序不定且上下行格式不对称的旧冷柜固件。 */
public class LegacyFreezerDeviceSimulator extends MqttDeviceSimulator {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyFreezerDeviceSimulator.class);

    private static final int STATE_SYNC = 0x7E;

    private static final int ACK_SYNC = 0x6B;

    private static final int DOWNLINK_SYNC_HIGH = 0x55;

    private static final int DOWNLINK_SYNC_LOW = 0xAA;

    private static final int TAG_POWER = 0x31;

    private static final int TAG_TEMPERATURE = 0x07;

    private static final int TAG_MODE = 0x44;

    private static final int OP_WRITE_POWER = 0x19;

    private static final int OP_SET_MODE = 0xC3;

    private final @NotNull String clientId;

    private final @NotNull AtomicInteger reportSequence = new AtomicInteger();

    private final @NotNull AtomicInteger acknowledgementSequence = new AtomicInteger();

    private volatile boolean power = true;

    private volatile @NotNull String mode = "cool";

    /** 创建一台使用旧式二进制协议的模拟设备。 */
    public LegacyFreezerDeviceSimulator(@NotNull SimulatorConfiguration configuration) {
        super(configuration, configuration.legacyClientId(), configuration.legacySecret());
        this.clientId = configuration.legacyClientId();
    }

    /** @return 旧冷柜固定寄存器下行 Topic */
    @Override
    protected @NotNull String downlinkTopic() {
        return "factory/cmd/" + clientId;
    }

    /** 周期发布字段可缺失、顺序不固定且夹带未知 tag 的二进制状态 patch。 */
    @Override
    protected void publishReported() {
        publishState(false);
    }

    /** 校验固定寄存器下行帧、执行命令、发布二进制 ACK 和执行后的完整状态。 */
    @Override
    protected void receiveDownlink(byte @NotNull [] packet) {
        if (packet.length != 14
                || unsigned(packet[0]) != DOWNLINK_SYNC_HIGH
                || unsigned(packet[1]) != DOWNLINK_SYNC_LOW
                || unsigned(packet[11]) != 1) {
            throw new IllegalArgumentException("旧冷柜下行帧形状错误");
        }
        if (complementSum(packet, 0, 13) != unsigned(packet[13])) {
            throw new IllegalArgumentException("旧冷柜下行累加和反码错误");
        }
        int operation = unsigned(packet[2]);
        long messageId = ByteBuffer.wrap(packet, 3, 8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getLong();
        int value = unsigned(packet[12]);
        try {
            if (operation == OP_WRITE_POWER) {
                power = switch (value) {
                    case 0x0F -> true;
                    case 0xF0 -> false;
                    default -> throw new IllegalArgumentException("旧冷柜电源寄存器值未知");
                };
            } else if (operation == OP_SET_MODE) {
                mode = switch (value) {
                    case 7 -> "cool";
                    case 3 -> "fan";
                    case 12 -> "defrost";
                    default -> throw new IllegalArgumentException("旧冷柜 mode 命令值未知");
                };
            } else {
                throw new IllegalArgumentException("旧冷柜操作码未知");
            }
            publishAcknowledgement(messageId, true);
            publishState(true);
            LOG.info(
                    "旧冷柜指令已执行 | clientId={} messageId={} operation={}",
                    clientId,
                    messageId,
                    operation);
        } catch (RuntimeException exception) {
            publishAcknowledgement(messageId, false);
            throw exception;
        }
    }

    /** 根据周期轮次选择残缺字段；指令执行后强制发送一次完整状态。 */
    private void publishState(boolean complete) {
        int sequence = reportSequence.incrementAndGet();
        int shape = complete ? 0 : sequence % 4;
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        // 固件故意使用不稳定字段顺序，并在部分报文里加入平台不认识的厂商诊断 tag。
        if (shape == 0 || shape == 2)
            appendTlv(body, 0x77, new byte[] {(byte) sequence});
        if (shape == 0 || shape == 2)
            appendTlv(body, TAG_MODE, new byte[] {(byte) encodeReportedMode(mode)});
        if (shape == 0 || shape == 3)
            appendTlv(body, TAG_POWER, new byte[] {(byte) (power ? 0xA5 : 0x5A)});
        if (shape == 0 || shape == 1 || shape == 3) {
            short temperature = (short) (-185 + sequence % 9 - 4);
            byte[] raw = ByteBuffer.allocate(2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(temperature)
                    .array();
            appendTlv(body, TAG_TEMPERATURE, raw);
        }

        byte[] bodyBytes = body.toByteArray();
        byte[] packet = new byte[bodyBytes.length + 5];
        packet[0] = (byte) STATE_SYNC;
        packet[1] = 0x13;
        packet[2] = (byte) encodePackedDecimal(sequence % 100);
        packet[3] = (byte) bodyBytes.length;
        System.arraycopy(bodyBytes, 0, packet, 4, bodyBytes.length);
        packet[packet.length - 1] = (byte) xor(packet, 1, packet.length - 1);
        publish("factory/raw/" + clientId + "/S", packet);
        LOG.info(
                "旧冷柜状态已上报 | clientId={} sequence={} shape={} power={} mode={}",
                clientId,
                sequence,
                shape,
                power,
                mode);
    }

    /** 发布不带时间、使用小端 messageId 和设备响应序号的二进制业务 ACK。 */
    private void publishAcknowledgement(long messageId, boolean succeeded) {
        int sequence = acknowledgementSequence.updateAndGet(value -> (value + 1) & 0xFFFF);
        byte[] packet = new byte[13];
        packet[0] = (byte) ACK_SYNC;
        ByteBuffer.wrap(packet, 1, 8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(messageId);
        packet[9] = (byte) (succeeded ? 0x11 : 0xEE);
        ByteBuffer.wrap(packet, 10, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) sequence);
        packet[12] = (byte) xor(packet, 0, 12);
        publish("factory/raw/" + clientId + "/R", packet);
    }

    /** 向旧设备状态 body 写入一个 tag、长度和值。 */
    private static void appendTlv(
            @NotNull ByteArrayOutputStream body,
            int tag,
            byte @NotNull [] value) {
        body.write(tag);
        body.write(value.length);
        body.writeBytes(value);
    }

    /** 把标准 mode code 转成状态上报使用的厂商位标志。 */
    private static int encodeReportedMode(@NotNull String mode) {
        return switch (mode) {
            case "cool" -> 0x02;
            case "fan" -> 0x08;
            case "defrost" -> 0x20;
            default -> throw new IllegalStateException("旧冷柜内部 mode 非法");
        };
    }

    /** 把零到 99 的序号编码为固件历史遗留的 BCD 字节。 */
    private static int encodePackedDecimal(int value) {
        return ((value / 10) << 4) | value % 10;
    }

    /** @return 无符号 byte 值 */
    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    /** 计算从 startInclusive 到 endExclusive 的逐字节 XOR。 */
    private static int xor(byte[] values, int startInclusive, int endExclusive) {
        int result = 0;
        for (int index = startInclusive; index < endExclusive; index++) {
            result ^= unsigned(values[index]);
        }
        return result;
    }

    /** 计算旧设备下行帧使用的八位累加和反码。 */
    private static int complementSum(byte[] values, int startInclusive, int endExclusive) {
        int sum = 0;
        for (int index = startInclusive; index < endExclusive; index++) {
            sum = (sum + unsigned(values[index])) & 0xFF;
        }
        return (~sum) & 0xFF;
    }
}
