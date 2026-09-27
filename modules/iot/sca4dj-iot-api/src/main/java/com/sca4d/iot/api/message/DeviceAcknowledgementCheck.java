package com.sca4d.iot.api.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sca4d.core.api.message.Message;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/** 请求检查一个标准 Device 下行消息的指定发送尝试是否等待 ACK 超时。 */
public final class DeviceAcknowledgementCheck extends Message {

    public static final @NotNull TreeCode CODE = TreeCode.of("IOT/DOWNLINK/ACK/CHECK");

    public static final int PROTOCOL_VERSION = 1;

    private final long messageId;

    private final int attempt;

    /** @param messageId `iot_downlink_message` 标识 @param attempt 需要检查的发送尝试序号 */
    @JsonCreator
    public DeviceAcknowledgementCheck(
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

    /** @return 需要检查的发送尝试序号 */
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
