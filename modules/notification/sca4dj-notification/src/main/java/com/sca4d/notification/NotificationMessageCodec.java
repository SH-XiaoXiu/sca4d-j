package com.sca4d.notification;

import static com.sca4d.core.api.name.NameFormat.COLON;
import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.message.MessageContent;
import com.sca4d.core.api.message.MessageContentCodec;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notification;
import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.region.Region;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * 把 NotificationMessage 编解码为 RabbitMQ 与 Outbox 使用的稳定 JSON payload。
 */
public class NotificationMessageCodec implements MessageCodec<NotificationMessage> {

    private final @NotNull ObjectMapper json;

    private final @NotNull MessageContentCodec contents = new MessageContentCodec();

    /** @param json 用于读取和写入通知消息 payload 的 ObjectMapper */
    public NotificationMessageCodec(@NotNull ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "ObjectMapper 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return NotificationMessage.CODE;
    }

    @Override
    public int version() {
        return NotificationMessage.PROTOCOL_VERSION;
    }

    @Override
    public @NotNull Class<NotificationMessage> valueType() {
        return NotificationMessage.class;
    }

    @Override
    public byte @NotNull [] encode(@NotNull NotificationMessage value) {
        Notification notification = Objects.requireNonNull(value, "NotificationMessage 不能为空").notification();
        ObjectNode root = json.createObjectNode();
        root.put("notificationCode", notification.code().format(SLASH));
        root.put("recipientType", notification.recipient().type().format(COLON));
        root.put("recipientId", notification.recipient().id());
        root.put("languageTag", notification.locale().toLanguageTag());
        Region region = notification.region();
        if (region == null) {
            root.putNull("regionCode");
        } else {
            root.put("regionCode", region.code());
        }
        root.set("variables", json.valueToTree(notification.variables()));
        ArrayNode channelValues = root.putArray("channels");
        notification.channels()
                .stream()
                .sorted(Comparator.naturalOrder())
                .forEach(
                        channel -> channelValues.addObject()
                                .put("code", channel.format(SLASH))
                                .put("required", notification.requiredChannels().contains(channel)));
        ArrayNode contentValues = root.putArray("contents");
        notification.contents().forEach(content -> contentValues.add(contents.encode(content)));
        try {
            return json.writeValueAsBytes(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("NotificationMessage 无法编码", exception);
        }
    }

    @Override
    public @NotNull NotificationMessage decode(byte @NotNull [] wire) {
        try {
            JsonNode root = json.readTree(Objects.requireNonNull(wire, "NotificationMessage 编码不能为空"));
            Map<String, String> variables = new LinkedHashMap<>();
            root.required("variables")
                    .fields()
                    .forEachRemaining(
                            entry -> variables.put(
                                    entry.getKey(),
                                    entry.getValue().textValue()));
            Set<TreeCode> channels = new LinkedHashSet<>();
            Set<TreeCode> requiredChannels = new LinkedHashSet<>();
            root.required("channels").forEach(channel -> {
                TreeCode value = TreeCode.of(channel.required("code").textValue());
                channels.add(value);
                if (channel.required("required").booleanValue()) {
                    requiredChannels.add(value);
                }
            });
            List<MessageContent> decodedContents = StreamSupport
                    .stream(root.required("contents").spliterator(), false)
                    .map(contents::decode)
                    .toList();
            JsonNode region = root.get("regionCode");
            Notification notification = new Notification(
                    TreeCode.of(root.required("notificationCode").textValue()),
                    new NotificationRecipient(
                            TreeCode.ofSegment(root.required("recipientType").textValue()),
                            root.required("recipientId").textValue()),
                    Locale.forLanguageTag(root.required("languageTag").textValue()),
                    Map.copyOf(variables),
                    decodedContents,
                    region == null || region.isNull() ? null : new Region(region.textValue()),
                    Set.copyOf(channels),
                    Set.copyOf(requiredChannels));
            return new NotificationMessage(notification);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("NotificationMessage 无法解码", exception);
        }
    }
}
