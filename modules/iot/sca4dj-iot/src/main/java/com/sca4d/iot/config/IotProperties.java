package com.sca4d.iot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 保存具体应用为 IoT 连接、设备定时、ACK 和 reported 存储选择的运行参数。 */
@ConfigurationProperties("sca4dj.iot")
public class IotProperties {

    private Duration connectionTtl = Duration.ofMinutes(30);

    private Duration scheduleTick = Duration.ofMillis(200);

    private Duration acknowledgementTimeout = Duration.ofSeconds(30);

    private Duration downlinkRetention = Duration.ofDays(7);

    private Duration downlinkCleanupInterval = Duration.ofHours(1);

    private final Reported reported = new Reported();

    private int maxReportedProperties = 100;

    private int maxDesiredProperties = 100;

    private int maxEventsPerMessage = 100;

    private int productCatalogScanBatchSize = 1_000;

    private int deviceScheduleBatchSize = 1_000;

    private int downlinkCleanupBatchSize = 10_000;

    /** @return Broker 连接信息未续期时的保留时间 */
    public Duration getConnectionTtl() {
        return connectionTtl;
    }

    /** @param connectionTtl Broker 连接信息未续期时的保留时间 */
    public void setConnectionTtl(Duration connectionTtl) {
        this.connectionTtl = connectionTtl;
    }

    /** @return 高精度设备定时到期扫描间隔 */
    public Duration getScheduleTick() {
        return scheduleTick;
    }

    /** @param scheduleTick 高精度设备定时到期扫描间隔 */
    public void setScheduleTick(Duration scheduleTick) {
        this.scheduleTick = scheduleTick;
    }

    /** @return 下行消息发送后等待业务 ACK 的时间 */
    public Duration getAcknowledgementTimeout() {
        return acknowledgementTimeout;
    }

    /** @param acknowledgementTimeout 下行消息发送后等待业务 ACK 的时间 */
    public void setAcknowledgementTimeout(Duration acknowledgementTimeout) {
        this.acknowledgementTimeout = acknowledgementTimeout;
    }

    /** @return 最终状态下行消息在 PostgreSQL 中保留的时间 */
    public Duration getDownlinkRetention() {
        return downlinkRetention;
    }

    /** @param downlinkRetention 最终状态下行消息在 PostgreSQL 中保留的时间 */
    public void setDownlinkRetention(Duration downlinkRetention) {
        this.downlinkRetention = downlinkRetention;
    }

    /** @return 下行终态记录分批清理间隔 */
    public Duration getDownlinkCleanupInterval() {
        return downlinkCleanupInterval;
    }

    /** @param downlinkCleanupInterval 下行终态记录分批清理间隔 */
    public void setDownlinkCleanupInterval(Duration downlinkCleanupInterval) {
        this.downlinkCleanupInterval = downlinkCleanupInterval;
    }

    /** @return latest reported 使用 `postgresql` 还是 `redis` */
    public ReportedStore getReportedStore() {
        return reported.store;
    }

    /** @param reportedStore latest reported 内置存储编码 */
    public void setReportedStore(ReportedStore reportedStore) {
        reported.store = reportedStore;
    }

    /** @return latest reported 投影资源配置 */
    public Reported getReported() {
        return reported;
    }

    /** @return 单条 reported 允许覆盖的最大属性数量 */
    public int getMaxReportedProperties() {
        return maxReportedProperties;
    }

    /** @param maxReportedProperties 单条 reported 允许覆盖的最大属性数量 */
    public void setMaxReportedProperties(int maxReportedProperties) {
        this.maxReportedProperties = maxReportedProperties;
    }

    /** @return 单次 desired 修改允许包含的最大属性数量 */
    public int getMaxDesiredProperties() {
        return maxDesiredProperties;
    }

    /** @param maxDesiredProperties 单次 desired 修改允许包含的最大属性数量 */
    public void setMaxDesiredProperties(int maxDesiredProperties) {
        this.maxDesiredProperties = maxDesiredProperties;
    }

    /** @return 单条上行消息允许携带的最大离散事件数量 */
    public int getMaxEventsPerMessage() {
        return maxEventsPerMessage;
    }

    /** @param maxEventsPerMessage 单条上行消息允许携带的最大离散事件数量 */
    public void setMaxEventsPerMessage(int maxEventsPerMessage) {
        this.maxEventsPerMessage = maxEventsPerMessage;
    }

    /** @return 完整读取数据库 Product 目录时的分页大小 */
    public int getProductCatalogScanBatchSize() {
        return productCatalogScanBatchSize;
    }

    /** @param productCatalogScanBatchSize 完整读取数据库 Product 目录时的正数分页大小 */
    public void setProductCatalogScanBatchSize(int productCatalogScanBatchSize) {
        this.productCatalogScanBatchSize = productCatalogScanBatchSize;
    }

    /** @return 每轮到期设备规则扫描的最大数量 */
    public int getDeviceScheduleBatchSize() {
        return deviceScheduleBatchSize;
    }

    /** @param deviceScheduleBatchSize 每轮到期设备规则扫描的最大数量 */
    public void setDeviceScheduleBatchSize(int deviceScheduleBatchSize) {
        this.deviceScheduleBatchSize = deviceScheduleBatchSize;
    }

    /** @return 每轮删除的终态下行消息最大数量 */
    public int getDownlinkCleanupBatchSize() {
        return downlinkCleanupBatchSize;
    }

    /** @param downlinkCleanupBatchSize 每轮删除的终态下行消息最大数量 */
    public void setDownlinkCleanupBatchSize(int downlinkCleanupBatchSize) {
        this.downlinkCleanupBatchSize = downlinkCleanupBatchSize;
    }

    /** 校验公共模块运行必需且不能由公共模块猜测的配置。 */
    public void validate() {
        requirePositive(connectionTtl, "sca4dj.iot.connection-ttl");
        requirePositive(scheduleTick, "sca4dj.iot.schedule-tick");
        requirePositive(acknowledgementTimeout, "sca4dj.iot.acknowledgement-timeout");
        requirePositive(downlinkRetention, "sca4dj.iot.downlink-retention");
        requirePositive(downlinkCleanupInterval, "sca4dj.iot.downlink-cleanup-interval");
        if (acknowledgementTimeout.toMillis() < 1) {
            throw new IllegalStateException("sca4dj.iot.acknowledgement-timeout 必须至少为 1 毫秒");
        }
        if (reported.store == null) {
            throw new IllegalStateException("sca4dj.iot.reported.store 不能为空");
        }
        requireLimit(maxReportedProperties, "sca4dj.iot.max-reported-properties");
        requireLimit(maxDesiredProperties, "sca4dj.iot.max-desired-properties");
        requireLimit(maxEventsPerMessage, "sca4dj.iot.max-events-per-message");
        requireBatchSize(productCatalogScanBatchSize, "sca4dj.iot.product-catalog-scan-batch-size");
        requireBatchSize(deviceScheduleBatchSize, "sca4dj.iot.device-schedule-batch-size");
        requireBatchSize(downlinkCleanupBatchSize, "sca4dj.iot.downlink-cleanup-batch-size");
    }

    /** 要求 Duration 已提供且为正数。 */
    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + " 必须显式配置为正数有效期");
        }
    }

    /** 要求单条消息数量限制位于公共模块允许的保护范围内。 */
    private static void requireLimit(int value, String property) {
        if (value < 1 || value > 1000) {
            throw new IllegalStateException(property + " 必须在 1 到 1000 之间");
        }
    }

    /** 要求内部扫描和清理批次位于可控的正数范围内。 */
    private static void requireBatchSize(int value, String property) {
        if (value < 1 || value > 10_000) {
            throw new IllegalStateException(property + " 必须在 1 到 10000 之间");
        }
    }

    /** 配置 latest reported 投影的存储选择。 */
    public static class Reported {

        private ReportedStore store = ReportedStore.POSTGRESQL;

        /** @return latest reported 投影使用的存储 */
        public ReportedStore getStore() {
            return store;
        }

        /** @param store latest reported 投影使用的存储 */
        public void setStore(ReportedStore store) {
            this.store = store;
        }
    }
}
