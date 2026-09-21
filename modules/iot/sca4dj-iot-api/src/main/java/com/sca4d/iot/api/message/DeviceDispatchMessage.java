package com.sca4d.iot.api.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.message.Message;
import org.jetbrains.annotations.NotNull;

/** 请求发送一个标准 Device 下行消息的指定尝试。 */
public final class DeviceDispatchMessage extends Message {

    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/DISPATCH");

    public static final int PROTOCOL_VERSION = 1;

    private final long messageId;

    private final int attempt;

    /** @param messageId `iot_downlink_message` 标识 @param attempt 从 1 开始的发送尝试序号 */
    @JsonCreator
    public DeviceDispatchMessage(
            @JsonProperty("messageId") long messageId,
            @JsonProperty("attempt") int attempt) {
        if (messageId <= 0 || attempt <= 0) {
            throw new IllegalArgumentException("下行消息标识和尝试序号必须为正数");
        }
        this.messageId = messageId;
        this.attempt = attempt;
    }

    /** @return `iot_downlink_message` 标识 */
    @JsonProperty("messageId")
    public long messageId() {
        return messageId;
    }

    /** @return 从 1 开始的发送尝试序号 */
    @JsonProperty("attempt")
    public int attempt() {
        return attempt;
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public int protocolVersion() {
        return PROTOCOL_VERSION;
    }
}
