package com.sca4d.iot.message;

import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.message.IotMessageTypes;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 在来源事务提交后消费可靠 ACK 请求，并执行现有消息级状态转换。 */
public final class DeviceAcknowledgementConsumer implements MessageConsumer<DeviceAcknowledgement> {

    private final @NotNull DeviceAcknowledgementService acknowledgements;

    /** @param acknowledgements 使用每 message 分布式锁处理 ACK 的服务 */
    public DeviceAcknowledgementConsumer(@NotNull DeviceAcknowledgementService acknowledgements) {
        this.acknowledgements = Objects.requireNonNull(acknowledgements, "Device ACK 服务不能为空");
    }

    @Override
    public @NotNull String consumerCode() {
        return "iot.downlink.ack-requested";
    }

    @Override
    public @NotNull com.sca4d.core.api.event.EventType<DeviceAcknowledgement> eventType() {
        return IotMessageTypes.ACKNOWLEDGEMENT_REQUESTED;
    }

    @Override
    public void consume(@NotNull MessageDelivery<DeviceAcknowledgement> delivery) {
        acknowledgements.acknowledge(
                Objects.requireNonNull(delivery, "Device ACK 投递不能为空").event().payload());
    }
}
