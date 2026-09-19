package com.sca4d.iot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 保存 IoT TimescaleDB 连接和高频历史清理参数。 */
@ConfigurationProperties("sca4dj.iot.timescale")
public final class IotTimescaleProperties {

    private boolean enabled;

    private String url;

    private String username;

    private String password;

    private String driverClassName;

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

    /** @return TimescaleDB JDBC 地址 */
    public String getUrl() {
        return url;
    }

    /** @param url TimescaleDB JDBC 地址 */
    public void setUrl(String url) {
        this.url = url;
    }

    /** @return TimescaleDB 登录用户名 */
    public String getUsername() {
        return username;
    }

    /** @param username TimescaleDB 登录用户名 */
    public void setUsername(String username) {
        this.username = username;
    }

    /** @return TimescaleDB 登录密码；允许空值以支持数据库认证策略 */
    public String getPassword() {
        return password;
    }

    /** @param password TimescaleDB 登录密码；允许空值以支持数据库认证策略 */
    public void setPassword(String password) {
        this.password = password;
    }

    /** @return 可选 JDBC 驱动类名 */
    public String getDriverClassName() {
        return driverClassName;
    }

    /** @param driverClassName 可选 JDBC 驱动类名 */
    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
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
        requireText(getUrl(), "sca4dj.iot.timescale.url");
        requireText(getUsername(), "sca4dj.iot.timescale.username");
        requirePositive(cleanupInterval, "sca4dj.iot.timescale.cleanup-interval");
        requirePositive(telemetryRetention, "sca4dj.iot.timescale.telemetry-retention");
        requirePositive(eventRetention, "sca4dj.iot.timescale.event-retention");
        if (maxQueryResults < 1 || maxQueryResults > 10_000) {
            throw new IllegalStateException("sca4dj.iot.timescale.max-query-results 必须在 1 到 10000 之间");
        }
    }

    /** 要求连接配置文本存在且不为空。 */
    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " 必须显式配置");
        }
    }

    /** 要求清理间隔和保留期为正数。 */
    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + " 必须配置为正数时长");
        }
    }
}
