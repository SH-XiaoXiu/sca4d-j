package com.sca4d.web.realtime.connection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.webrealtime.WebRealtimeMessage;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

/** 把 WebRealtimeMessage 编解码为 RabbitMQ 与 Outbox 使用的稳定 JSON payload。 */
public final class WebRealtimeMessageCodec implements MessageCodec<WebRealtimeMessage> {

    private final @NotNull ObjectMapper json;

    /** 创建当前对象。 */
    public WebRealtimeMessageCodec(@NotNull ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return WebRealtimeMessage.CODE;
    }

    @Override
    public int version() {
        return WebRealtimeMessage.PROTOCOL_VERSION;
    }

    @Override
    public @NotNull Class<WebRealtimeMessage> valueType() {
        return WebRealtimeMessage.class;
    }

    @Override
    public byte @NotNull [] encode(@NotNull WebRealtimeMessage value) {
        WebRealtimeMessage checked = Objects.requireNonNull(value, "WebRealtimeMessage 不能为空");
        Event<?> event = checked.browserEvent();
        ObjectNode root = json.createObjectNode();
        root.put("targetType", checked.target().type().name());
        if (checked.target().qualifier() == null) {
            root.putNull("targetQualifier");
        } else {
            root.put("targetQualifier", checked.target().qualifier());
        }
        root.put("targetId", checked.target().id());
        root.put("eventCode", event.type().code());
        root.put("eventVersion", event.type().protocolVersion());
        if (event.businessKey() == null) {
            root.putNull("businessKey");
        } else {
            root.put("businessKey", event.businessKey());
        }
        root.put("occurredAt", event.occurredAt().toString());
        root.set("payload", json.valueToTree(event.payload()));
        root.put("closeAfter", checked.closeAfter());
        root.put("expiresAt", checked.expiresAt().toString());
        try {
            return json.writeValueAsBytes(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("WebRealtimeMessage 无法编码", exception);
        }
    }

    @Override
    public @NotNull WebRealtimeMessage decode(byte @NotNull [] wire) {
        try {
            JsonNode root = json.readTree(Objects.requireNonNull(wire, "WebRealtimeMessage 编码不能为空"));
            WebRealtimeTarget target = WebRealtimeTarget.of(
                    WebRealtimeTarget.Type.valueOf(root.required("targetType").textValue()),
                    root.required("targetQualifier").isNull()
                            ? null
                            : root.required("targetQualifier").textValue(),
                    root.required("targetId").textValue());
            Event<JsonNode> event = new Event<>(
                    new EventType<>(
                            root.required("eventCode").textValue(),
                            root.required("eventVersion").intValue(),
                            JsonNode.class),
                    root.required("businessKey").isNull()
                            ? null
                            : root.required("businessKey").textValue(),
                    Instant.parse(root.required("occurredAt").textValue()),
                    root.required("payload"));
            return new WebRealtimeMessage(
                    target,
                    event,
                    root.required("closeAfter").booleanValue(),
                    Instant.parse(root.required("expiresAt").textValue()));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("WebRealtimeMessage 无法解码", exception);
        }
    }
}
