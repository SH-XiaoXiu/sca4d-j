package com.sca4d.iot.telemetry;

import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.MessageConsumer;
import com.sca4d.core.api.messaging.MessageDelivery;
import com.sca4d.iot.api.message.IotMessageTypes;
import com.sca4d.iot.api.telemetry.TelemetryBatch;
import com.sca4d.iot.config.IotTimescale;
import com.sca4d.iot.timescale.persistence.TimescaleRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 消费 reported Telemetry 消息并按 MessageId 幂等写入 TimescaleDB。 */
public class TimescaleTelemetryConsumer implements MessageConsumer<TelemetryBatch> {

    private final @NotNull TimescaleRepository repository;

    /** @param repository TimescaleDB 持久化入口 */
    public TimescaleTelemetryConsumer(@NotNull TimescaleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Timescale Repository 不能为空");
    }

    @Override
    public @NotNull String consumerCode() {
        return "iot.timescale.telemetry-store";
    }

    @Override
    public @NotNull EventType<TelemetryBatch> eventType() {
        return IotMessageTypes.TELEMETRY_REPORTED;
    }

    /** 同一 MessageId 的 RabbitMQ 重复投递不会产生重复样本。 */
    @Override
    @Transactional(transactionManager = IotTimescale.TRANSACTION_MANAGER)
    public void consume(@NotNull MessageDelivery<TelemetryBatch> delivery) {
        MessageDelivery<TelemetryBatch> checked = Objects.requireNonNull(delivery, "Telemetry 投递不能为空");
        repository.addTelemetry(checked.messageId().value(), checked.event().payload());
    }
}
