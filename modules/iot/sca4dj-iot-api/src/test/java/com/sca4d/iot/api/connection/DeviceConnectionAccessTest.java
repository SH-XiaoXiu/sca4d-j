package com.sca4d.iot.api.connection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

/** 验证连接接入结果要求接入方式属于 Device，并与 Session 绑定一致。 */
class DeviceConnectionAccessTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    @DisplayName("Session 接入方式可以绑定 SessionId")
    void acceptsSessionForSessionAccessMethod() {
        DeviceAccessMethod method = DeviceAccessMethod.session();

        assertDoesNotThrow(
                () -> new DeviceConnectionAccess(
                        device(method),
                        method,
                        new SessionId("session-1"),
                        EXPIRES_AT));
    }

    @Test
    @DisplayName("直接认证接入方式拒绝绑定 SessionId")
    void rejectsSessionForDirectAuthenticationMethod() {
        DeviceAccessMethod method = DeviceAccessMethod.parse("account:shared-secret");
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeviceConnectionAccess(
                        device(method),
                        method,
                        new SessionId("session-1"),
                        EXPIRES_AT));
    }

    @Test
    @DisplayName("Session 接入方式必须绑定 SessionId")
    void requiresSessionForSessionMethod() {
        DeviceAccessMethod method = DeviceAccessMethod.session();
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeviceConnectionAccess(
                        device(method),
                        method,
                        null,
                        EXPIRES_AT));
    }

    @Test
    @DisplayName("间接 Device 不登记直接连接和认证接入方式")
    void acceptsIndirectDeviceWithoutConnectionAccess() {
        assertDoesNotThrow(
                () -> new Device(
                        2,
                        ProductBinding.database(1, "product-1"),
                        "device-2",
                        null,
                        "Device 2",
                        null,
                        1,
                        1,
                        "protocol-1",
                        null,
                        Set.of()));
    }

    /** 创建只允许指定接入方式的 Device 快照。 */
    private static Device device(DeviceAccessMethod method) {
        return new Device(
                1,
                ProductBinding.database(1, "product-1"),
                "device-1",
                null,
                "Device 1",
                null,
                1,
                1,
                "protocol-1",
                new DeviceConnectionBinding(
                        DeviceConnectionProtocol.MQTT,
                        "mqtt-client-id",
                        "device-1"),
                Set.of(method));
    }
}
