package com.sca4d.iot.message;

import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.messaging.MessageCodec;
import com.sca4d.core.api.messaging.MessageHandler;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 消费设备 ACK 处理消息，并执行现有消息级状态转换。 */
public class DeviceAcknowledgementHandler implements MessageHandler<DeviceAcknowledgement> {

    private static final TreeCode CODE = TreeCode.of("IOT/DOWNLINK/ACK/HANDLER");

    private final @NotNull DeviceAcknowledgementService acknowledgements;

    private final @NotNull MessageCodec<DeviceAcknowledgement> codec;

    /**
     * @param acknowledgements 负责更新下行消息状态的服务
     * @param codec 设备 ACK 消息 Codec
     */
    public DeviceAcknowledgementHandler(
            @NotNull DeviceAcknowledgementService acknowledgements,
            @NotNull MessageCodec<DeviceAcknowledgement> codec) {
        this.acknowledgements = Objects.requireNonNull(acknowledgements, "Device ACK 服务不能为空");
        this.codec = Objects.requireNonNull(codec, "Device ACK Codec 不能为空");
    }

    /** @return 设备 ACK Handler 的稳定编码 */
    @Override
    public @NotNull TreeCode code() {
        return CODE;
    }

    /** @return 设备 ACK 消息 Codec */
    @Override
    public @NotNull MessageCodec<DeviceAcknowledgement> codec() {
        return codec;
    }

    /**
     * 应用 ACK 状态转换并确认消息；服务失败时向消息容器抛出异常以触发重试或拒绝。
     *
     * @param incoming 已解码的设备 ACK 消息
     * @return 确认消息处理结果
     */
    @Override
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<DeviceAcknowledgement> incoming) {
        acknowledgements.acknowledge(Objects.requireNonNull(incoming, "Device ACK 消息不能为空").message());
        return HandlingDecision.acknowledge();
    }
}
