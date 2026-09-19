package com.sca4d.messaging.rabbit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 编码和严格校验 RabbitMQ 与 Outbox 共用的业务事件 JSON 信封。 */
@ApiStatus.Internal
public final class MessagingEnvelopeCodec {

    private static final int ENVELOPE_VERSION = 1;

    private static final Pattern MESSAGE_ID = Pattern.compile("[1-9][0-9]*");

    private static final Set<String> FIELDS = Set.of(
            "envelopeVersion",
            "messageId",
            "eventCode",
            "protocolVersion",
            "businessKey",
            "occurredAt",
            "publishedAt",
            "sourceApplication",
            "payload");

    private final @NotNull ObjectMapper objectMapper;

    /** 创建直接使用脚手架固定 `@Sca4djJson` 映射器的信封编解码器。 */
    public MessagingEnvelopeCodec(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    /**
     * 编码已经分配 `MessageId` 和发布元数据的业务事件。
     *
     * @throws IllegalStateException payload 无法编码时抛出
     */
    byte @NotNull [] encode(
            @NotNull MessageId messageId,
            @NotNull Event<?> event,
            @NotNull Instant publishedAt,
            @NotNull String sourceApplication) {
        Event<?> checked = Objects.requireNonNull(event, "业务事件不能为空");
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("envelopeVersion", ENVELOPE_VERSION);
        envelope.put("messageId", Objects.requireNonNull(messageId, "MessageId 不能为空").toString());
        envelope.put("eventCode", checked.type().code());
        envelope.put("protocolVersion", checked.type().protocolVersion());
        if (checked.businessKey() == null) {
            envelope.putNull("businessKey");
        } else {
            envelope.put("businessKey", checked.businessKey());
        }
        envelope.put("occurredAt", checked.occurredAt().toString());
        envelope.put("publishedAt", Objects.requireNonNull(publishedAt, "消息发布时间不能为空").toString());
        envelope.put("sourceApplication", TextUtils.requireText(sourceApplication, "消息来源应用"));
        envelope.set("payload", objectMapper.valueToTree(checked.payload()));
        try {
            return objectMapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("业务事件无法编码", exception);
        }
    }

    /**
     * 按 Consumer 声明的 `EventType` 解码信封并补充 RabbitMQ 重投标记。
     *
     * @throws IllegalArgumentException 信封字段、字段类型或事件契约不符合当前版本时抛出
     */
    <T> @NotNull MessageDelivery<T> decode(
            byte @NotNull [] body,
            @NotNull EventType<T> expectedType,
            boolean redelivered) {
        EventType<T> checkedType = Objects.requireNonNull(expectedType, "Consumer EventType 不能为空");
        try {
            JsonNode root = objectMapper.readTree(Objects.requireNonNull(body, "消息 body 不能为空"));
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("业务消息信封必须是 JSON 对象");
            }
            validateFields(root);
            int envelopeVersion = requiredInt(root, "envelopeVersion");
            if (envelopeVersion != ENVELOPE_VERSION) {
                throw new IllegalArgumentException("不支持的业务消息信封版本: " + envelopeVersion);
            }
            MessageId messageId = requiredMessageId(root);
            String eventCode = requiredText(root, "eventCode");
            int protocolVersion = requiredInt(root, "protocolVersion");
            if (!checkedType.code().equals(eventCode)
                    || checkedType.protocolVersion() != protocolVersion) {
                throw new IllegalArgumentException("业务消息与 Consumer EventType 不一致");
            }
            JsonNode businessKeyNode = required(root, "businessKey");
            String businessKey;
            if (businessKeyNode.isNull()) {
                businessKey = null;
            } else if (businessKeyNode.isTextual()) {
                businessKey = businessKeyNode.textValue();
            } else {
                throw new IllegalArgumentException("业务消息信封字段类型错误: businessKey");
            }
            Instant occurredAt = Instant.parse(requiredText(root, "occurredAt"));
            Instant publishedAt = Instant.parse(requiredText(root, "publishedAt"));
            String sourceApplication = requiredText(root, "sourceApplication");
            JsonNode payloadNode = required(root, "payload");
            if (payloadNode.isNull()) {
                throw new IllegalArgumentException("业务消息信封字段不能为 null: payload");
            }
            T payload = objectMapper.treeToValue(payloadNode, checkedType.payloadType());
            Event<T> event = new Event<>(checkedType, businessKey, occurredAt, payload);
            return new MessageDelivery<>(messageId, event, sourceApplication, publishedAt, redelivered);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("业务消息信封无法解码", exception);
        }
    }

    /** 拒绝缺少字段以及当前信封版本没有声明的额外字段。 */
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

    /** 返回信封中必须存在的字段，包括允许为 null 的字段。 */
    private static @NotNull JsonNode required(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = root.get(name);
        if (value == null) {
            throw new IllegalArgumentException("业务消息信封缺少字段: " + name);
        }
        return value;
    }

    /** 返回信封中必须存在且必须是文本的字段。 */
    private static @NotNull String requiredText(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = required(root, name);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("业务消息信封字段类型错误: " + name);
        }
        return value.textValue();
    }

    /** 返回信封中必须存在且能无损转换为 int 的整数字段。 */
    private static int requiredInt(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = required(root, name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("业务消息信封字段类型错误: " + name);
        }
        return value.intValue();
    }

    /** 返回信封中不丢失跨语言精度的规范十进制 MessageId。 */
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
}
