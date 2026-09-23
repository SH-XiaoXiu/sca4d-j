package com.sca4d.messaging.rabbit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventMessageCodec;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 编码和严格校验 RabbitMQ 与 Outbox 共用的通用 Message 信封。 */
@ApiStatus.Internal
public final class RabbitMessageEnvelopeCodec {

    private static final int ENVELOPE_VERSION = 2;

    private static final Pattern MESSAGE_ID = Pattern.compile("[1-9][0-9]*");

    private static final Set<String> FIELDS = Set.of(
            "envelopeVersion",
            "messageId",
            "messageCode",
            "protocolVersion",
            "publishedAt",
            "sourceApplication",
            "payload");

    private final @NotNull ObjectMapper json;

    private final @NotNull MessageCodecRegistry codecs;

    public RabbitMessageEnvelopeCodec(
            @NotNull ObjectMapper json,
            @NotNull List<? extends @NotNull MessageCodec<?>> codecs) {
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
        this.codecs = new MessageCodecRegistry(codecs, json);
    }

    byte @NotNull [] encode(
            @NotNull MessageId messageId,
            @NotNull Message message,
            @NotNull Instant publishedAt,
            @NotNull String sourceApplication) {
        Message checked = Objects.requireNonNull(message, "消息不能为空");
        MessageCodec<Message> codec = codecs.codec(checked);
        ObjectNode envelope = json.createObjectNode();
        envelope.put("envelopeVersion", ENVELOPE_VERSION);
        envelope.put("messageId", Objects.requireNonNull(messageId, "MessageId 不能为空").toString());
        envelope.put("messageCode", checked.code().value());
        envelope.put("protocolVersion", checked.protocolVersion());
        envelope.put("publishedAt", Objects.requireNonNull(publishedAt, "消息发布时间不能为空").toString());
        envelope.put("sourceApplication", TextUtils.requireText(sourceApplication, "消息来源应用"));
        envelope.put("payload", Base64.getEncoder().encodeToString(codec.encode(checked)));
        try {
            return json.writeValueAsBytes(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("业务消息信封无法编码", exception);
        }
    }

    <T> @NotNull IncomingMessage<Event<T>> decodeEvent(
            byte @NotNull [] body,
            @NotNull EventType<T> expectedType,
            boolean redelivered,
            int attempt) {
        EventMessageCodec<T> codec = new EventMessageCodec<>(expectedType, json);
        Envelope envelope = envelope(body, expectedType.treeCode(), expectedType.protocolVersion());
        return new IncomingMessage<>(
                envelope.messageId,
                codec.decode(envelope.payload),
                envelope.sourceApplication,
                envelope.publishedAt,
                redelivered,
                attempt);
    }

    <M extends Message> @NotNull IncomingMessage<M> decode(
            byte @NotNull [] body,
            @NotNull MessageCodec<M> codec,
            boolean redelivered,
            int attempt) {
        MessageCodec<M> checkedCodec = Objects.requireNonNull(codec, "MessageCodec 不能为空");
        Envelope envelope = envelope(body, checkedCodec.code(), checkedCodec.version());
        return new IncomingMessage<>(
                envelope.messageId,
                checkedCodec.decode(envelope.payload),
                envelope.sourceApplication,
                envelope.publishedAt,
                redelivered,
                attempt);
    }

    private @NotNull Envelope envelope(byte @NotNull [] body, @NotNull TreeCode code, int version) {
        try {
            JsonNode root = json.readTree(Objects.requireNonNull(body, "消息 body 不能为空"));
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("业务消息信封必须是 JSON object");
            }
            validateFields(root);
            int envelopeVersion = requiredInt(root, "envelopeVersion");
            if (envelopeVersion != ENVELOPE_VERSION) {
                throw new IllegalArgumentException("不支持的业务消息信封版本: " + envelopeVersion);
            }
            String messageCode = requiredText(root, "messageCode");
            int protocolVersion = requiredInt(root, "protocolVersion");
            if (!code.value().equals(messageCode) || version != protocolVersion) {
                throw new IllegalArgumentException("业务消息与 Handler Codec 不一致");
            }
            byte[] payload;
            try {
                payload = Base64.getDecoder().decode(requiredText(root, "payload"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("业务消息 payload 不是有效 Base64", exception);
            }
            return new Envelope(
                    requiredMessageId(root),
                    Instant.parse(requiredText(root, "publishedAt")),
                    requiredText(root, "sourceApplication"),
                    payload);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("业务消息信封无法解码", exception);
        }
    }

    private static void validateFields(@NotNull JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw new IllegalArgumentException("业务消息信封包含未知字段: " + name);
            }
        }
        for (String field : FIELDS) {
            if (!root.has(field)) {
                throw new IllegalArgumentException("业务消息信封缺少字段: " + field);
            }
        }
    }

    private static @NotNull JsonNode required(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = root.get(name);
        if (value == null) {
            throw new IllegalArgumentException("业务消息信封缺少字段: " + name);
        }
        return value;
    }

    private static @NotNull String requiredText(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = required(root, name);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("业务消息信封字段类型错误: " + name);
        }
        return value.textValue();
    }

    private static int requiredInt(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = required(root, name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("业务消息信封字段类型错误: " + name);
        }
        return value.intValue();
    }

    private static @NotNull MessageId requiredMessageId(@NotNull JsonNode root) {
        String value = requiredText(root, "messageId");
        if (!MESSAGE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("业务消息信封 MessageId 必须是正数十进制文本");
        }
        try {
            return new MessageId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("业务消息信封 MessageId 超过 long 范围", exception);
        }
    }

    private static final class Envelope {
        private final @NotNull MessageId messageId;

        private final @NotNull Instant publishedAt;

        private final @NotNull String sourceApplication;

        private final byte @NotNull [] payload;

        private Envelope(
                @NotNull MessageId messageId,
                @NotNull Instant publishedAt,
                @NotNull String sourceApplication,
                byte @NotNull [] payload) {
            this.messageId = messageId;
            this.publishedAt = publishedAt;
            this.sourceApplication = sourceApplication;
            this.payload = payload;
        }
    }
}
