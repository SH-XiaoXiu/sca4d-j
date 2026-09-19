package com.sca4d.app.server.iot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/** 锁定 Sca4d Server 自有 Device 创建幂等数据的清理默认值。 */
class AppIotPropertiesTest {

    @Test
    @DisplayName("默认每十分钟清理一次 Device 创建幂等记录")
    void usesDeviceCreationCleanupDefaults() {
        AppIotProperties properties = new AppIotProperties();

        assertThat(properties.getDeviceCreationCleanupInterval()).isEqualTo(Duration.ofMinutes(10));
    }
}
