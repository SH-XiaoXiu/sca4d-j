package com.sca4d.app.iot.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** 模拟字段完整、时间可靠且使用稳定 JSON 信封的规范温控器固件。 */
public class StandardClimateDeviceSimulator extends MqttDeviceSimulator {

    private static final Logger LOG = LoggerFactory.getLogger(StandardClimateDeviceSimulator.class);

    private final @NotNull String clientId;

    private final @NotNull ObjectMapper objectMapper = new ObjectMapper();

    private final @NotNull AtomicInteger reportSequence = new AtomicInteger();

    private volatile boolean power = true;

    private volatile @NotNull String mode = "cool";

    /** 创建一台使用规范 JSON v1 协议的模拟设备。 */
    public StandardClimateDeviceSimulator(@NotNull SimulatorConfiguration configuration) {
        super(configuration, configuration.standardClientId(), configuration.standardSecret());
        this.clientId = configuration.standardClientId();
    }

    /** @return 规范设备固定下行 Topic */
    @Override
    protected @NotNull String downlinkTopic() {
        return "demo/standard/" + clientId + "/downlink";
    }

    /** 发布包含全部物模型属性和可靠设备时间的 JSON 快照。 */
    @Override
    protected void publishReported() {
        int sequence = reportSequence.incrementAndGet();
        BigDecimal temperature = BigDecimal.valueOf(235L + sequence % 7 - 3, 1)
                .setScale(1, RoundingMode.UNNECESSARY);
        ObjectNode properties = objectMapper.createObjectNode();
        properties.put("power", power);
        properties.put("temperature", temperature);
        properties.put("mode", mode);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("sequence", sequence);
        root.put("reportedAt", Instant.now().toString());
        root.set("properties", properties);
        publish("demo/standard/" + clientId + "/reported", encode(root));
        LOG.info(
                "规范设备状态已上报 | clientId={} sequence={} power={} mode={}",
                clientId,
                sequence,
                power,
                mode);
    }

    /** 解析标准下行信封、修改固件状态、发布业务 ACK 并立即回报执行后状态。 */
    @Override
    protected void receiveDownlink(byte @NotNull [] payload) {
        JsonNode root = readObject(payload);
        if (requiredInteger(root, "schemaVersion") != 1) {
            throw new IllegalArgumentException("规范设备不支持下行 schemaVersion");
        }
        long messageId = requiredLong(root, "messageId");
        String kind = requiredText(root, "kind");
        String targetCode = requiredText(root, "targetCode");
        JsonNode data = requiredObject(root, "data");
        try {
            if (kind.equals("desired") && targetCode.equals("power")) {
                power = requiredBoolean(data, "power");
            } else if (kind.equals("command") && targetCode.equals("set-mode")) {
                mode = requireMode(requiredText(data, "mode"));
            } else {
                throw new IllegalArgumentException("规范设备不支持下行目标: " + kind + '/' + targetCode);
            }
            publishAcknowledgement(messageId, "success", targetCode, null);
            publishReported();
            LOG.info(
                    "规范设备指令已执行 | clientId={} messageId={} targetCode={}",
                    clientId,
                    messageId,
                    targetCode);
        } catch (RuntimeException exception) {
            publishAcknowledgement(messageId, "failed", targetCode, exception.getMessage());
            throw exception;
        }
    }

    /** 发布包含稳定 ackId 和设备发生时间的 JSON 业务回执。 */
    private void publishAcknowledgement(
            long messageId,
            @NotNull String ackCode,
            @NotNull String targetCode,
            String reason) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("targetCode", targetCode);
        if (reason != null)
            data.put("reason", reason);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("messageId", messageId);
        root.put("ackId", clientId + "-ack-" + messageId);
        root.put("ackCode", ackCode);
        root.put("acknowledgedAt", Instant.now().toString());
        root.set("data", data);
        publish("demo/standard/" + clientId + "/ack", encode(root));
    }

    /** 把 JSON object 编码为 UTF-8 payload。 */
    private byte @NotNull [] encode(@NotNull ObjectNode root) {
        try {
            return objectMapper.writeValueAsBytes(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("规范设备无法编码 JSON", exception);
        }
    }

    /** 解析并要求下行 payload 根节点为 JSON object。 */
    private @NotNull JsonNode readObject(byte @NotNull [] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject())
                throw new IllegalArgumentException("下行 payload 必须是 JSON object");
            return root;
        } catch (IOException exception) {
            throw new IllegalArgumentException("下行 payload 不是合法 JSON", exception);
        }
    }

    /** 读取必须存在的 object 字段。 */
    private static @NotNull JsonNode requiredObject(@NotNull JsonNode node, @NotNull String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject())
            throw new IllegalArgumentException("JSON 字段必须是 object: " + field);
        return value;
    }

    /** 读取必须存在的文本字段。 */
    private static @NotNull String requiredText(@NotNull JsonNode node, @NotNull String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("JSON 字段必须是非空文本: " + field);
        }
        return value.textValue();
    }

    /** 读取必须存在的 boolean 字段。 */
    private static boolean requiredBoolean(@NotNull JsonNode node, @NotNull String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean())
            throw new IllegalArgumentException("JSON 字段必须是 boolean: " + field);
        return value.booleanValue();
    }

    /** 读取必须存在且可以精确表示为 int 的字段。 */
    private static int requiredInteger(@NotNull JsonNode node, @NotNull String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt())
            throw new IllegalArgumentException("JSON 字段必须是 int: " + field);
        return value.intValue();
    }

    /** 读取必须存在且可以精确表示为 long 的字段。 */
    private static long requiredLong(@NotNull JsonNode node, @NotNull String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong())
            throw new IllegalArgumentException("JSON 字段必须是 long: " + field);
        return value.longValue();
    }

    /** 要求指令 mode 属于固件实际支持的集合。 */
    private static @NotNull String requireMode(@NotNull String value) {
        String mode = Objects.requireNonNull(value, "mode 不能为空");
        if (!mode.equals("cool") && !mode.equals("fan") && !mode.equals("defrost")) {
            throw new IllegalArgumentException("规范设备不支持 mode: " + mode);
        }
        return mode;
    }
}
