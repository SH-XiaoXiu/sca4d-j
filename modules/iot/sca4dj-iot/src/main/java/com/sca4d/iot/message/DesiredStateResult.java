package com.sca4d.iot.message;

import com.sca4d.iot.api.message.DeviceDownlink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 返回 desired 写入后的 Device desiredSequence 和实际生成的状态同步消息。 */
public class DesiredStateResult {

    private final long desiredSequence;

    private final @NotNull List<DeviceDownlink> messages;

    /** @param desiredSequence Device 当前 desired sequence @param messages 本次实际变化生成的消息 */
    public DesiredStateResult(long desiredSequence, @NotNull List<DeviceDownlink> messages) {
        if (desiredSequence < 0)
            throw new IllegalArgumentException("desired sequence 不能为负数");
        this.desiredSequence = desiredSequence;
        this.messages = List.copyOf(Objects.requireNonNull(messages, "desired messages 不能为空"));
    }

    /** @return Device 当前 desired sequence */
    public long desiredSequence() {
        return desiredSequence;
    }

    /** @return 本次实际生成的消息 */
    public @NotNull @Unmodifiable List<DeviceDownlink> messages() {
        return messages;
    }
}
