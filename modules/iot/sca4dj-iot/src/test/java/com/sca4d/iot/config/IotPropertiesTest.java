package com.sca4d.iot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/** 验证 IoT 连接和设备定时使用的公共运行默认值。 */
class IotPropertiesTest {

    @Test
    @DisplayName("连接投影和设备定时使用可覆盖对账窗口的默认值")
    void usesConfirmedRuntimeDefaults() {
        IotProperties properties = new IotProperties();

        assertThat(properties.getConnectionTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getReportedStore()).isEqualTo(ReportedStore.POSTGRESQL);
        assertThat(properties.getScheduleTick()).isEqualTo(Duration.ofMillis(200));
        assertThat(properties.getMaxReportedProperties()).isEqualTo(100);
        assertThat(properties.getMaxDesiredProperties()).isEqualTo(100);
        assertThat(properties.getMaxEventsPerMessage()).isEqualTo(100);
        assertThat(properties.getProductCatalogScanBatchSize()).isEqualTo(1_000);
        assertThat(properties.getDeviceScheduleBatchSize()).isEqualTo(1_000);
        assertThat(properties.getDownlinkCleanupBatchSize()).isEqualTo(10_000);
    }
}
