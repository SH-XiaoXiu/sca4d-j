package com.sca4d.messaging.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.codec.VersionedCodec;
import com.sca4d.core.api.codec.VersionedCodecCatalog;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventMessageCodec;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按 Message TreeCode 和协议版本索引应用安装的非 Event Codec。
 */
@ApiStatus.Internal
class MessageCodecRegistry {

    private final @NotNull Map<@NotNull TreeCode, @NotNull VersionedCodecCatalog<Message, byte[]>> codecs;

    private final @NotNull ObjectMapper json;

    MessageCodecRegistry(
            @NotNull List<? extends @NotNull MessageCodec<?>> codecs,
            @NotNull ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
        Map<TreeCode, List<MessageCodec<?>>> grouped = new LinkedHashMap<>();
        for (MessageCodec<?> candidate : Objects.requireNonNull(codecs, "MessageCodec 列表不能为空")) {
            MessageCodec<?> codec = Objects.requireNonNull(candidate, "MessageCodec 不能包含 null");
            grouped.computeIfAbsent(codec.code(), ignored -> new java.util.ArrayList<>()).add(codec);
        }
        Map<TreeCode, VersionedCodecCatalog<Message, byte[]>> registered = new LinkedHashMap<>();
        for (Map.Entry<TreeCode, List<MessageCodec<?>>> entry : grouped.entrySet()) {
            List<MessageCodec<Message>> values = entry.getValue()
                    .stream()
                    .map(MessageCodecRegistry::asMessageCodec)
                    .toList();
            MessageCodec<Message> current = values.stream()
                    .max(java.util.Comparator.comparingInt(MessageCodec::version))
                    .orElseThrow(() -> new IllegalStateException("消息没有注册 Codec: " + entry.getKey()));
            registered.put(
                    entry.getKey(),
                    new VersionedCodecCatalog<>(current, values));
        }
        this.codecs = Map.copyOf(registered);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    MessageCodec<Message> codec(@NotNull Message message) {
        Message checked = Objects.requireNonNull(message, "消息不能为空");
        if (checked instanceof Event<?> event) {
            return (MessageCodec<Message>) (MessageCodec<?>) new EventMessageCodec<>(event.type(), json);
        }
        MessageCodec<?> codec = codec(checked.code(), checked.protocolVersion());
        if (!codec.valueType().isInstance(checked)) {
            throw new IllegalArgumentException(
                    "消息没有匹配 Codec: " + checked.code() + '@' + checked.protocolVersion());
        }
        return (MessageCodec<Message>) codec;
    }

    @NotNull
    MessageCodec<?> codec(@NotNull TreeCode code, int version) {
        VersionedCodecCatalog<Message, byte[]> catalog = codecs.get(Objects.requireNonNull(code, "消息编码不能为空"));
        if (catalog == null) {
            throw new IllegalArgumentException("消息没有注册 Codec: " + code + '@' + version);
        }
        return asMessageCodec(catalog.select(version));
    }

    @SuppressWarnings("unchecked")
    private static @NotNull MessageCodec<Message> asMessageCodec(@NotNull MessageCodec<?> codec) {
        return (MessageCodec<Message>) codec;
    }

    private static @NotNull MessageCodec<?> asMessageCodec(
            @NotNull VersionedCodec<Message, byte[]> codec) {
        return (MessageCodec<?>) codec;
    }
}
