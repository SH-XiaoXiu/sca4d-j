package com.sca4d.web.realtime.connection;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** 保存 SSE 与 STOMP 共同发送给浏览器的事件编码、版本、发生时间和 payload。 */
@ApiStatus.Internal
public class BrowserEnvelope {

    private final @NotNull TreeCode eventCode;

    private final int protocolVersion;

    private final @NotNull Instant occurredAt;

    private final @NotNull JsonNode payload;

    /** 创建不包含内部目标、业务键和 RabbitMQ 元数据的浏览器信封。 */
    public BrowserEnvelope(
            @NotNull TreeCode eventCode,
            int protocolVersion,
            @NotNull Instant occurredAt,
            @NotNull JsonNode payload) {
        this.eventCode = Objects.requireNonNull(eventCode, "浏览器事件编码不能为空");
        this.protocolVersion = protocolVersion;
        this.occurredAt = Objects.requireNonNull(occurredAt, "浏览器事件时间不能为空");
        this.payload = Objects.requireNonNull(payload, "浏览器事件 payload 不能为空");
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
}
