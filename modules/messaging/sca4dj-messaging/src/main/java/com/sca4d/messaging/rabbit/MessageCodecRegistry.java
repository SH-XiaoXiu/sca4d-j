package com.sca4d.messaging.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventMessageCodec;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageHandler;
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
final class MessageCodecRegistry {

    private final @NotNull Map<@NotNull Key, @NotNull MessageCodec<?>> codecs;

    private final @NotNull ObjectMapper json;

    MessageCodecRegistry(
            @NotNull List<? extends @NotNull MessageHandler<?>> handlers,
            @NotNull ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
        Map<Key, MessageCodec<?>> registered = new LinkedHashMap<>();
        for (MessageHandler<?> handler : Objects.requireNonNull(handlers, "MessageHandler 列表不能为空")) {
            MessageCodec<?> codec = Objects.requireNonNull(handler, "MessageHandler 不能包含 null").codec();
            Key key = new Key(codec.code(), codec.version());
            MessageCodec<?> previous = registered.putIfAbsent(key, codec);
            if (previous != null && !previous.valueType().equals(codec.valueType())) {
                throw new IllegalStateException("Message Codec 契约冲突: " + key);
            }
        }
        this.codecs = Map.copyOf(registered);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @NotNull
    MessageCodec<Message> codec(@NotNull Message message) {
        Message checked = Objects.requireNonNull(message, "消息不能为空");
        if (checked instanceof Event<?> event) {
            return (MessageCodec) new EventMessageCodec<>(event.type(), json);
        }
        MessageCodec<?> codec = codecs.get(new Key(checked.code(), checked.protocolVersion()));
        if (codec == null || !codec.valueType().isInstance(checked)) {
            throw new IllegalArgumentException(
                    "消息没有匹配 Codec: " + checked.code() + '@' + checked.protocolVersion());
        }
        return (MessageCodec<Message>) codec;
    }

    private static final class Key {
        private final @NotNull TreeCode code;

        private final int version;

        private Key(@NotNull TreeCode code, int version) {
            this.code = Objects.requireNonNull(code, "消息编码不能为空");
            this.version = version;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Key key && version == key.version && code.equals(key.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, version);
        }

        @Override
        public String toString() {
            return code + "@" + version;
        }
    }
}
