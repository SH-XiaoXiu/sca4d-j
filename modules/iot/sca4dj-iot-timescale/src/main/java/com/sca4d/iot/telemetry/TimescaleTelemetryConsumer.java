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
import com.sca4d.iot.api.message.IotMessageTypes;
import com.sca4d.iot.api.telemetry.TelemetryBatch;
import com.sca4d.iot.config.IotTimescale;
import com.sca4d.iot.timescale.persistence.TimescaleRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 消费 reported Telemetry 消息并按 MessageId 幂等写入 TimescaleDB。 */
public class TimescaleTelemetryConsumer implements EventHandler<TelemetryBatch> {

    private final @NotNull TimescaleRepository repository;

    /** @param repository TimescaleDB 持久化入口 */
    public TimescaleTelemetryConsumer(@NotNull TimescaleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Timescale Repository 不能为空");
    }

    @Override
    public @NotNull TreeCode code() {
        return TreeCode.of("IOT/TIMESCALE/TELEMETRY/HANDLER");
    }

    @Override
    public @NotNull EventType<TelemetryBatch> eventType() {
        return IotMessageTypes.TELEMETRY_REPORTED;
    }

    /** 同一 MessageId 的 RabbitMQ 重复投递不会产生重复样本。 */
    @Override
    @DS(IotTimescale.DATASOURCE)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public void handleEvent(@NotNull IncomingMessage<Event<TelemetryBatch>> delivery) {
        IncomingMessage<Event<TelemetryBatch>> checked = Objects.requireNonNull(delivery, "Telemetry 投递不能为空");
        repository.addTelemetry(checked.messageId().value(), checked.message().payload());
    }

    /** RabbitMQ 调用的入口先选择历史库，再开启事务；提交后才能确认消息。 */
    @Override
    @DS(IotTimescale.DATASOURCE)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @SystemExecution(IotCapabilities.SYSTEM_IDENTITY_VALUE)
    public @NotNull HandlingDecision handle(@NotNull IncomingMessage<Event<TelemetryBatch>> delivery) throws Exception {
        return EventHandler.super.handle(delivery);
    }
}
