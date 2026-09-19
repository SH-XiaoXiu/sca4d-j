package com.sca4d.iot.telemetry;

import com.sca4d.core.api.scheduling.ClusterTask;
import com.sca4d.iot.config.IotTimescale;
import com.sca4d.iot.timescale.persistence.TimescaleRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 使用 `drop_chunks` 清理 TimescaleDB Telemetry 和事件历史。 */
public class TimescaleRetentionTask implements ClusterTask {

    private static final Logger LOG = LoggerFactory.getLogger(TimescaleRetentionTask.class);

    private final @NotNull TimescaleRepository repository;

    private final @NotNull Clock clock;

    private final @NotNull Duration telemetryRetention;

    private final @NotNull Duration eventRetention;

    /**
     * 创建清理两个 IoT 历史 hypertable 完整 chunk 的集群任务。
     *
     * @param repository TimescaleDB 持久化入口
     * @param clock 计算保留截止时间的时钟
     * @param telemetryRetention Telemetry 历史保留期
     * @param eventRetention IoT 事件历史保留期
     * @throws NullPointerException Repository、时钟或保留期为空时抛出
     * @throws IllegalArgumentException 任一保留期不是正数时抛出
     */
    public TimescaleRetentionTask(
            @NotNull TimescaleRepository repository,
            @NotNull Clock clock,
            @NotNull Duration telemetryRetention,
            @NotNull Duration eventRetention) {
        this.repository = Objects.requireNonNull(repository, "Timescale Repository 不能为空");
        this.clock = Objects.requireNonNull(clock, "Timescale 清理时钟不能为空");
        Duration checkedTelemetry = Objects.requireNonNull(telemetryRetention, "Telemetry 保留期不能为空");
        Duration checkedEvent = Objects.requireNonNull(eventRetention, "事件保留期不能为空");
        if (checkedTelemetry.isZero() || checkedTelemetry.isNegative()) {
            throw new IllegalArgumentException("Telemetry 保留期必须为正数");
        }
        if (checkedEvent.isZero() || checkedEvent.isNegative()) {
            throw new IllegalArgumentException("事件保留期必须为正数");
        }
        this.telemetryRetention = checkedTelemetry;
        this.eventRetention = checkedEvent;
    }

    /** 清理两个 hypertable 中早于各自保留截止时间的完整 chunk。 */
    @Override
    @Transactional(transactionManager = IotTimescale.TRANSACTION_MANAGER)
    public void execute() {
        Instant now = clock.instant();
        Instant telemetryOlderThan = now.minus(telemetryRetention);
        Instant eventOlderThan = now.minus(eventRetention);
        repository.dropChunks(telemetryOlderThan, eventOlderThan);
        LOG.info(
                "TimescaleDB IoT 历史 chunk 清理已完成 | telemetryOlderThan={} eventOlderThan={}",
                telemetryOlderThan,
                eventOlderThan);
    }
}
