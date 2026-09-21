package com.sca4d.core.api.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.codec.JsonCodec;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.Objects;

/** 把 MessageContent 编解码为稳定 JSON 结构。 */
public final class MessageContentCodec implements JsonCodec<MessageContent> {

    private static final String TEXT = "text";

    private static final String BINARY = "binary";

    private static final String REFERENCE = "reference";

    @Override
    public @NotNull JsonNode encode(@NotNull MessageContent value) {
        MessageContent checked = Objects.requireNonNull(value, "消息内容不能为空");
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("mediaType", checked.mediaType());
        root.put("disposition", checked.disposition().name());
        if (checked.name() == null) {
            root.putNull("name");
        } else {
            root.put("name", checked.name());
        }
        ObjectNode source = root.putObject("source");
        if (checked.body() instanceof TextContent text) {
            source.put("type", TEXT);
            source.put("value", text.value());
        } else if (checked.body() instanceof BinaryContent binary) {
            source.put("type", BINARY);
            source.put("value", Base64.getEncoder().encodeToString(binary.value()));
        } else if (checked.body() instanceof ReferencedContent reference) {
            source.put("type", REFERENCE);
            source.put("scheme", reference.scheme());
            source.put("key", reference.key());
        } else {
            throw new IllegalArgumentException("不支持的消息内容正文: " + checked.body().getClass().getName());
        }
        return root;
    }

    @Override
    public @NotNull MessageContent decode(@NotNull JsonNode value) {
        JsonNode root = requireObject(value, "消息内容");
        String mediaType = requiredText(root, "mediaType");
        ContentDisposition disposition;
        try {
            disposition = ContentDisposition.valueOf(requiredText(root, "disposition"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("消息内容 disposition 无效", exception);
        }
        JsonNode name = root.get("name");
        String contentName = name == null || !name.isTextual() || name.textValue().isBlank()
                ? null
                : name.textValue();
        JsonNode source = requireObject(root.get("source"), "消息内容 source");
        String type = requiredText(source, "type");
        ContentBody body = switch (type) {
            case TEXT -> new TextContent(requiredText(source, "value"));
            case BINARY -> new BinaryContent(decodeBytes(requiredText(source, "value")));
            case REFERENCE -> new ReferencedContent(requiredText(source, "scheme"), requiredText(source, "key"));
            default -> throw new IllegalArgumentException("消息内容 source.type 无效: " + type);
        };
        return new MessageContent(mediaType, disposition, body, contentName);
    }

    private static byte @NotNull [] decodeBytes(@NotNull String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("消息内容 binary 不是有效 Base64", exception);
        }
    }

    private static @NotNull JsonNode requireObject(JsonNode value, @NotNull String name) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(name + "必须是 JSON object");
        }
        return value;
    }

    private static @NotNull String requiredText(@NotNull JsonNode root, @NotNull String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(name + "必须是非空文本");
        }
        return value.textValue();
    }
}
