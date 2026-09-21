package com.sca4d.web.realtime.rabbit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.web.realtime.connection.BrowserEnvelope;
import com.sca4d.web.realtime.connection.BrowserWrite;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 编解码 RabbitMQ 节点队列使用的连接列表和浏览器事件信封。 */
@ApiStatus.Internal
public final class NodeMessageCodec {

    private static final int DELIVERY_VERSION = 1;

    private static final Set<String> FIELDS = Set.of(
            "deliveryVersion",
            "connections",
            "eventCode",
            "protocolVersion",
            "occurredAt",
            "payload",
            "closeAfter");

    private final @NotNull ObjectMapper objectMapper;

    /** 创建直接使用脚手架固定 `@Sca4djJson` 映射器的节点消息编解码器。 */
    public NodeMessageCodec(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    /** 编码不包含内部目标、业务键、`MessageId` 和 RabbitMQ 元数据的临时节点消息。 */
    byte @NotNull [] encode(
            @NotNull List<@NotNull ConnectionId> connectionIds,
            @NotNull BrowserWrite delivery) {
        BrowserWrite checked = Objects.requireNonNull(delivery, "连接投递不能为空");
        BrowserEnvelope envelope = checked.envelope();
        if (connectionIds.isEmpty()) {
            throw new IllegalArgumentException("Web Realtime 节点投递至少需要一个 ConnectionId");
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("deliveryVersion", DELIVERY_VERSION);
        ArrayNode connections = root.putArray("connections");
        connectionIds.forEach(id -> connections.add(id.value()));
        root.put("eventCode", envelope.getEventCode());
        root.put("protocolVersion", envelope.getProtocolVersion());
        root.put("occurredAt", envelope.getOccurredAt().toString());
        root.set("payload", envelope.getPayload());
        root.put("closeAfter", checked.closeAfter());
        try {
            return objectMapper.writeValueAsBytes(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Web Realtime 节点消息无法编码", exception);
        }
    }

    /**
     * 严格解码临时节点消息。
     *
     * @throws IllegalArgumentException 字段、类型、版本或连接列表无效时抛出
     */
    @NotNull
    NodeDelivery decode(byte @NotNull [] body) {
        try {
            JsonNode root = objectMapper.readTree(Objects.requireNonNull(body, "节点消息不能为空"));
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Web Realtime 节点消息必须是 JSON 对象");
            }
            validateFields(root);
            int version = requiredInt(root, "deliveryVersion");
            if (version != DELIVERY_VERSION) {
                throw new IllegalArgumentException("不支持的 Web Realtime 节点消息版本: " + version);
            }
            JsonNode connectionNodes = required(root, "connections");
            if (!connectionNodes.isArray() || connectionNodes.isEmpty()) {
                throw new IllegalArgumentException("节点消息 connections 必须是非空数组");
            }
            List<ConnectionId> connections = new ArrayList<>();
            for (JsonNode value : connectionNodes) {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("节点消息 connections 元素必须是文本");
                }
                connections.add(new ConnectionId(value.textValue()));
            }
            JsonNode closeAfter = required(root, "closeAfter");
            if (!closeAfter.isBoolean()) {
                throw new IllegalArgumentException("节点消息字段类型错误: closeAfter");
            }
            JsonNode payload = required(root, "payload");
            if (payload.isNull()) {
                throw new IllegalArgumentException("节点消息字段不能为 null: payload");
            }
            BrowserEnvelope envelope = new BrowserEnvelope(
                    requiredText(root, "eventCode"),
                    requiredInt(root, "protocolVersion"),
                    Instant.parse(requiredText(root, "occurredAt")),
                    payload);
            return new NodeDelivery(
                    List.copyOf(connections),
                    new BrowserWrite(envelope, closeAfter.booleanValue()));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Web Realtime 节点消息无法解码", exception);
        }
    }

    /** 拒绝缺少字段以及当前节点消息版本没有声明的额外字段。 */
    private static void validateFields(@NotNull JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw new IllegalArgumentException("Web Realtime 节点消息包含未知字段: " + name);
            }
        }
        for (String field : FIELDS) {
            if (!root.has(field)) {
                throw new IllegalArgumentException("Web Realtime 节点消息缺少字段: " + field);
            }
        }
    }

    /** 返回必须存在的 JSON 字段。 */
    private static @NotNull JsonNode required(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = root.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Web Realtime 节点消息缺少字段: " + name);
        }
        return value;
    }

    /** 返回必须存在且必须是文本的 JSON 字段。 */
    private static @NotNull String requiredText(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = required(root, name);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Web Realtime 节点消息字段类型错误: " + name);
        }
        return value.textValue();
    }

    /** 返回必须存在且能无损转换为 int 的整数字段。 */
    private static int requiredInt(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = required(root, name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Web Realtime 节点消息字段类型错误: " + name);
        }
        return value.intValue();
    }

    /** 保存一个节点需要投递的连接列表和浏览器内容。 */
    static final class NodeDelivery {
        private final @NotNull List<@NotNull ConnectionId> connections;

        private final @NotNull BrowserWrite delivery;

        /** 创建已完成字段校验的节点投递。 */
        NodeDelivery(
                @NotNull List<@NotNull ConnectionId> connections,
                @NotNull BrowserWrite delivery) {
            this.connections = connections;
            this.delivery = delivery;
        }

        /** @return 当前节点需要匹配的连接标识 */
        @NotNull
        List<@NotNull ConnectionId> connections() {
            return connections;
        }

        /** @return 每个连接收到的相同投递 */
        @NotNull
        BrowserWrite delivery() {
            return delivery;
        }
    }
}
