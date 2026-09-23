package com.sca4d.iot.message;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 消费设备 ACK 处理消息，并执行现有消息级状态转换。 */
public final class DeviceAcknowledgementHandler implements MessageHandler<DeviceAcknowledgement> {

    private static final TreeCode CODE = TreeCode.of("IOT/DOWNLINK/ACK/HANDLER");

    private final @NotNull DeviceAcknowledgementService acknowledgements;

    private final @NotNull MessageCodec<DeviceAcknowledgement> codec;

    public DeviceAcknowledgementHandler(
            @NotNull DeviceAcknowledgementService acknowledgements,
            @NotNull MessageCodec<DeviceAcknowledgement> codec) {
        this.acknowledgements = Objects.requireNonNull(acknowledgements, "Device ACK 服务不能为空");
        this.codec = Objects.requireNonNull(codec, "Device ACK Codec 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    @Override
    public @NotNull MessageCodec<DeviceAcknowledgement> codec() {
        return codec;
    }

    @Override
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<DeviceAcknowledgement> incoming) {
        acknowledgements.acknowledge(Objects.requireNonNull(incoming, "Device ACK 消息不能为空").message());
        return HandlingDecision.acknowledge();
    }
}
