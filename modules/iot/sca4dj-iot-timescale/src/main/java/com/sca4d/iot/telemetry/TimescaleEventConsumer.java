package com.sca4d.iot.telemetry;

import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.iot.api.history.DeviceEventMessage;
import com.sca4d.iot.api.message.IotMessageTypes;
import com.sca4d.iot.config.IotTimescale;
import com.sca4d.iot.timescale.persistence.TimescaleRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 消费物模型和 IoT 状态机事件，并按发生时间与 MessageId 幂等写入 TimescaleDB。 */
public class TimescaleEventConsumer implements MessageConsumer<DeviceEventMessage> {

    private final @NotNull TimescaleRepository repository;

    /** @param repository TimescaleDB 持久化入口 */
    public TimescaleEventConsumer(@NotNull TimescaleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Timescale Repository 不能为空");
    }

    @Override
    public @NotNull String consumerCode() {
        return "iot.timescale.event-store";
    }

    @Override
    public @NotNull com.sca4d.core.api.event.EventType<DeviceEventMessage> eventType() {
        return IotMessageTypes.DEVICE_EVENT_OCCURRED;
    }

    /** 同一发生时间和 MessageId 的重复投递不会产生重复事件。 */
    @Override
    @Transactional(transactionManager = IotTimescale.TRANSACTION_MANAGER)
    public void consume(@NotNull MessageDelivery<DeviceEventMessage> delivery) {
        MessageDelivery<DeviceEventMessage> checked = Objects.requireNonNull(delivery, "Device 事件投递不能为空");
        repository.addEvent(checked.messageId().value(), checked.event().payload());
    }
}
