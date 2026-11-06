package com.sca4d.web.realtime.connection;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 SSE 与 STOMP 共同发送给浏览器的事件编码、版本、发生时间和 payload。 */
@ApiStatus.Internal
public class BrowserEnvelope {

    private final @NotNull Instant occurredAt;

    private final @NotNull JsonNode payload;

    private final @NotNull TreeCode eventCode;

    private final int protocolVersion;

    /** 创建发送给浏览器的统一事件信封。 */
    public BrowserEnvelope(
            @NotNull TreeCode eventCode,
            int protocolVersion,
            @NotNull Instant occurredAt,
            @NotNull JsonNode payload) {
        this.eventCode = Objects.requireNonNull(eventCode, "浏览器事件编码不能为空");
        this.protocolVersion = protocolVersion;
        this.occurredAt = Objects.requireNonNull(occurredAt, "浏览器事件时间不能为空");
        this.payload = longsAsStrings(Objects.requireNonNull(payload, "浏览器事件 payload 不能为空"));
    }

    /** @return 浏览器事件编码 */
    public @NotNull String getEventCode() {
        return eventCode.format(SLASH);
    }

    /** @return 浏览器事件协议版本 */
    public int getProtocolVersion() {
        return protocolVersion;
    }

    /** @return 业务事件发生时间 */
    public @NotNull Instant getOccurredAt() {
        return occurredAt;
    }

    /** @return JSON payload */
    public @NotNull JsonNode getPayload() {
        return payload;
    }

    /** 把浏览器事件中的 64 位整数递归编码为十进制字符串，避免 JavaScript 丢失精度。 */
    private static @NotNull JsonNode longsAsStrings(@NotNull JsonNode node) {
        if (node instanceof LongNode longNode) {
            return TextNode.valueOf(longNode.asText());
        }
        if (node instanceof ObjectNode object) {
            object.properties().forEach(entry -> entry.setValue(longsAsStrings(entry.getValue())));
        } else if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                array.set(index, longsAsStrings(array.get(index)));
            }
        }
        return node;
    }
}
