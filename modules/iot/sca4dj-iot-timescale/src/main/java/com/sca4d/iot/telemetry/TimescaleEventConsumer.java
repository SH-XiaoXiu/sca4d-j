package com.sca4d.iot.telemetry;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.sca4d.core.api.annotation.SystemExecution;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.EventHandler;
import com.sca4d.core.api.messaging.HandlingDecision;
import com.sca4d.core.api.messaging.IncomingMessage;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.iot.api.IotCapabilities;
import com.sca4d.iot.api.history.DeviceEventMessage;
import com.sca4d.iot.api.message.IotMessageTypes;
import com.sca4d.iot.config.IotTimescale;
import com.sca4d.iot.timescale.persistence.TimescaleRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 消费物模型和 IoT 状态机事件，并按发生时间与 Id 幂等写入 TimescaleDB。 */
public class TimescaleEventConsumer implements EventHandler<DeviceEventMessage> {

    private final @NotNull TimescaleRepository repository;

    /** @param repository TimescaleDB 持久化入口 */
    public TimescaleEventConsumer(@NotNull TimescaleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Timescale Repository 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("IOT/TIMESCALE/EVENT/HANDLER");
    }

    @Override
    public @NotNull EventType<DeviceEventMessage> eventType() {
        return IotMessageTypes.DEVICE_EVENT_OCCURRED;

    }

    /** 同一发生时间和 Id 的重复投递不会产生重复事件。 */
    @Override
    @DS(IotTimescale.DATASOURCE)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public void handleEvent(@NotNull IncomingMessage<Event<DeviceEventMessage>> delivery) {
        IncomingMessage<Event<DeviceEventMessage>> checked = Objects.requireNonNull(delivery, "Device 事件投递不能为空");
        repository.addEvent(checked.messageId().value(), checked.message().payload());
    }

    /** RabbitMQ 调用的入口先选择历史库，再开启事务；提交后才能确认消息。 */
    @Override
    @DS(IotTimescale.DATASOURCE)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<Event<DeviceEventMessage>> delivery)
            throws Exception {
        return EventHandler.super.handle(delivery);
    }
}
