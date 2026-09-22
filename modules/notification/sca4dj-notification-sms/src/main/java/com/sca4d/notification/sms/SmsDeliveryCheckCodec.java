package com.sca4d.notification.sms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.notification.NotificationMessage;
import com.sca4d.notification.NotificationMessageCodec;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

/** 编解码延迟 SMS 送达查询消息。 */
public final class SmsDeliveryCheckCodec implements MessageCodec<SmsDeliveryCheck> {
    private final @NotNull ObjectMapper json;

    private final @NotNull NotificationMessageCodec notifications;

    public SmsDeliveryCheckCodec(@NotNull ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
        this.notifications = new NotificationMessageCodec(json);
    }

    @Override
    public @NotNull TreeCode code() {
        return SmsDeliveryCheck.CODE;
    }

    @Override
    public int version() {
        return SmsDeliveryCheck.PROTOCOL_VERSION;
    }

    @Override
    public @NotNull Class<SmsDeliveryCheck> valueType() {
        return SmsDeliveryCheck.class;
    }

    @Override
    public byte @NotNull [] encode(@NotNull SmsDeliveryCheck value) {
        SmsDeliveryCheck checked = Objects.requireNonNull(value, "SMS 查询消息不能为空");
        ObjectNode root = json.createObjectNode();
        root.put("providerId", checked.providerId());
        root.put("messageId", checked.messageId().toString());
        root.put("attempt", checked.attempt());
        if (checked.requestId() == null)
            root.putNull("requestId");
        else
            root.put("requestId", checked.requestId());
        if (checked.bizId() == null)
            root.putNull("bizId");
        else
            root.put("bizId", checked.bizId());
        root.put(
                "notification",
                Base64.getEncoder()
                        .encodeToString(
                                notifications.encode(new NotificationMessage(checked.notification()))));
        try {
            return json.writeValueAsBytes(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SMS 查询消息无法编码", exception);
        }
    }

    @Override
    public @NotNull SmsDeliveryCheck decode(byte @NotNull [] wire) {
        try {
            JsonNode root = json.readTree(Objects.requireNonNull(wire, "SMS 查询消息编码不能为空"));
            NotificationMessage message = notifications.decode(
                    Base64.getDecoder().decode(root.required("notification").textValue()));
            JsonNode requestId = root.get("requestId");
            JsonNode bizId = root.get("bizId");
            return new SmsDeliveryCheck(
                    message.notification(),
                    new MessageId(root.required("messageId").longValue()),
                    root.required("providerId").longValue(),
                    requestId == null || requestId.isNull() ? null : requestId.textValue(),
                    bizId == null || bizId.isNull() ? null : bizId.textValue(),
                    root.required("attempt").intValue());
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal)
                throw illegal;
            throw new IllegalArgumentException("SMS 查询消息无法解码", exception);
        }
    }
}
