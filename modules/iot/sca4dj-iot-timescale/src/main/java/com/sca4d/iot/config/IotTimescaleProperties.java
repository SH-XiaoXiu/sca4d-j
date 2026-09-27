package com.sca4d.iot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 保存 IoT 历史功能和清理参数；数据库连接由动态数据源配置负责。 */
@ConfigurationProperties("sca4dj.iot.timescale")
public class IotTimescaleProperties {

    private boolean enabled;

    private Duration cleanupInterval = Duration.ofHours(1);

    private Duration telemetryRetention = Duration.ofDays(90);

    private Duration eventRetention = Duration.ofDays(90);

    private int maxQueryResults = 1_000;

    /** @return 是否创建独立 TimescaleDB 数据源、迁移和高频历史实现 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否创建独立 TimescaleDB 数据源、迁移和高频历史实现 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 清理完整 hypertable chunk 的调度间隔 */
    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    /** @param cleanupInterval 清理完整 hypertable chunk 的正数间隔 */
    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    /** @return Telemetry 历史保留期 */
    public Duration getTelemetryRetention() {
        return telemetryRetention;
    }

    /** @param telemetryRetention Telemetry 历史正数保留期 */
    public void setTelemetryRetention(Duration telemetryRetention) {
        this.telemetryRetention = telemetryRetention;
    }

    /** @return ThingEvent 历史保留期 */
    public Duration getEventRetention() {
        return eventRetention;
    }

    /** @param eventRetention ThingEvent 历史正数保留期 */
    public void setEventRetention(Duration eventRetention) {
        this.eventRetention = eventRetention;
    }

    /** @return 单次 Telemetry 高频历史查询允许返回的最大样本数 */
    public int getMaxQueryResults() {
        return maxQueryResults;
    }

    /** @param maxQueryResults 单次 Telemetry 高频历史查询允许返回的正数样本数 */
    public void setMaxQueryResults(int maxQueryResults) {
        this.maxQueryResults = maxQueryResults;
    }

    /** 校验启用 TimescaleDB 时连接和清理参数，避免应用启动后才暴露错误。 */
    public void validate() {
        if (!enabled) {
            return;
        }
        requirePositive(cleanupInterval, "sca4dj.iot.timescale.cleanup-interval");
        requirePositive(telemetryRetention, "sca4dj.iot.timescale.telemetry-retention");
        requirePositive(eventRetention, "sca4dj.iot.timescale.event-retention");
        if (maxQueryResults < 1 || maxQueryResults > 10_000) {
            throw new IllegalStateException("sca4dj.iot.timescale.max-query-results 必须在 1 到 10000 之间");
        }
    }

    /** 要求清理间隔和保留期为正数。 */
    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + " 必须配置为正数时长");
        }
    }
}
