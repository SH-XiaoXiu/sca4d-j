package com.sca4d.iot.telemetry;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.api.history.DeviceHistoryStore;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.query.DeviceEvent;
import com.sca4d.iot.api.schedule.DeviceScheduleExecution;
import com.sca4d.iot.api.telemetry.TelemetrySample;
import com.sca4d.iot.config.IotTimescale;
import com.sca4d.iot.timescale.persistence.TimescaleRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 通过 TimescaleDB 两张历史表实现 Device 历史查询契约。 */
@DS(IotTimescale.DATASOURCE)
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
public class TimescaleDeviceHistoryStore implements DeviceHistoryStore {

    private final @NotNull TimescaleRepository repository;

    /** @param repository TimescaleDB 历史持久化入口 */
    public TimescaleDeviceHistoryStore(@NotNull TimescaleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Timescale Repository 不能为空");
    }

    @Override
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull DeviceEvent>> recentEvents(
            @NotNull Collection<@NotNull Long> deviceIds,
            int limitPerDevice) {
        return repository.findRecentEvents(deviceIds, limitPerDevice);
    }

    @Override
    public @NotNull @Unmodifiable Map<@NotNull Long, @NotNull List<@NotNull DeviceAcknowledgement>> recentAcknowledgements(
            @NotNull Collection<@NotNull Long> messageIds,
            int limitPerMessage) {
        return repository.findRecentAcknowledgements(messageIds, limitPerMessage);
    }

    @Override
    public @NotNull Page<DeviceEvent> events(long deviceId, @NotNull PageRequest page) {
        return repository.findEvents(deviceId, page);
    }

    @Override
    public @NotNull Page<DeviceAcknowledgement> acknowledgements(long deviceId, @NotNull PageRequest page) {
        return repository.findAcknowledgements(deviceId, page);
    }

    @Override
    public @NotNull Page<DeviceAcknowledgement> acknowledgementsByMessageId(
            long messageId,
            @NotNull PageRequest page) {
        return repository.findAcknowledgementsByMessageId(messageId, page);
    }

    @Override
    public @NotNull Page<DeviceScheduleExecution> scheduleExecutions(
            long scheduleId,
            @NotNull PageRequest page) {
        return repository.findScheduleExecutions(scheduleId, page);
    }

    @Override
    public @NotNull List<TelemetrySample> telemetry(
            long deviceId,
            @NotNull String propertyCode,
            @NotNull Instant from,
            @NotNull Instant to,
            int limit) {
        return repository.findTelemetry(deviceId, propertyCode, from, to, limit);
    }
}
