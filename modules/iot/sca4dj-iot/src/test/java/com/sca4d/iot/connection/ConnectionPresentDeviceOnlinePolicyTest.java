package com.sca4d.iot.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.connection.DeviceConnection;
import com.sca4d.iot.api.connection.DeviceConnectionId;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

/** 验证默认在线策略只依赖仍有效的当前连接事实。 */
class ConnectionPresentDeviceOnlinePolicyTest {

    private final ConnectionPresentDeviceOnlinePolicy policy = new ConnectionPresentDeviceOnlinePolicy();

    @Test
    void reportsOnlineOnlyWhenConnectionProjectionIsPresent() {
        DeviceConnection connection = new DeviceConnection(
                new DeviceConnectionId("mqtt:client-7:1"),
                7,
                DeviceConnectionProtocol.MQTT,
                null,
                DeviceAccessMethod.parse("account:shared-secret"),
                Instant.parse("2026-08-10T00:00:00Z"));

        assertThat(policy.isOnline(List.of())).isFalse();
        assertThat(policy.isOnline(List.of(connection))).isTrue();
    }
}
