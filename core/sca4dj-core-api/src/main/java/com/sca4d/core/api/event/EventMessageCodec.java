package com.sca4d.core.api.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

/** 使用固定 ObjectMapper 编解码一种 EventType 的业务键、发生时间和 payload。 */
public class EventMessageCodec<T> implements MessageCodec<Event<T>> {

    private final @NotNull ObjectMapper json;

    private final @NotNull EventType<T> type;

    public EventMessageCodec(@NotNull EventType<T> type, @NotNull ObjectMapper json) {
        this.type = Objects.requireNonNull(type, "EventType 不能为空");
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return type.code();
    }

    @Override
    public int version() {
        return type.protocolVersion();
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull Class<Event<T>> valueType() {
        return (Class<Event<T>>) (Class<?>) Event.class;
    }

    @Override
    public byte @NotNull [] encode(@NotNull Event<T> value) {
        Event<T> checked = Objects.requireNonNull(value, "Event 不能为空");
        if (!type.equals(checked.type())) {
            throw new IllegalArgumentException("Event 与 Codec 的 EventType 不一致");
        }
        ObjectNode root = json.createObjectNode();
        if (checked.businessKey() == null) {
            root.putNull("businessKey");
        } else {
            root.put("businessKey", checked.businessKey());
        }
        root.put("occurredAt", checked.occurredAt().toString());
        root.set("payload", json.valueToTree(checked.payload()));
        try {
            return json.writeValueAsBytes(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Event 无法编码", exception);
        }
    }

    @Override
    public @NotNull Event<T> decode(byte @NotNull [] wire) {
        try {
            JsonNode root = json.readTree(Objects.requireNonNull(wire, "Event 编码不能为空"));
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Event 编码必须是 JSON object");
            }
            JsonNode businessKeyNode = root.get("businessKey");
            String businessKey = businessKeyNode == null || businessKeyNode.isNull()
                    ? null
                    : businessKeyNode.textValue();
            Instant occurredAt = Instant.parse(root.required("occurredAt").textValue());
            T payload = json.treeToValue(root.required("payload"), type.payloadType());
            return new Event<>(type, businessKey, occurredAt, payload);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Event 无法解码", exception);
        }
    }
}
