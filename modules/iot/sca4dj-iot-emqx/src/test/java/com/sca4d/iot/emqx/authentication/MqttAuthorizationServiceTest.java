package com.sca4d.iot.emqx.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.isolation.AppName;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.emqx.connection.MqttDeviceConnection;
import com.sca4d.iot.emqx.protocol.MqttProtocolCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证 MQTT ACL 指纹能够识别连接使用的规则是否仍然有效。 */
class MqttAuthorizationServiceTest {

    static {
        AppName.publish("SCA4D_TEST");
    }

    private static final Device DEVICE = new Device(
            71,
            ProductBinding.code("mqtt-debug"),
            "debug-key",
            null,
            "Debug Key",
            null,
            1,
            1,
            "mqtt-debug-v1",
            MqttDeviceConnection.clientId("dbg-71"),
            Set.of(DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET")));

    @Test
    @DisplayName("相同 ACL 生成稳定指纹且当前连接匹配")
    void createsStableFingerprint() {
        MqttAuthorizationService service = service("#");

        MqttAuthorization first = service.resolve(DEVICE, "dbg-71");
        MqttAuthorization second = service.resolve(DEVICE, "dbg-71");

        assertThat(first.fingerprint()).hasSize(64).isEqualTo(second.fingerprint());
        assertThat(
                service.isCurrent(
                        DEVICE,
                        "dbg-71",
                        Map.of(MqttAuthorizationService.FINGERPRINT_ATTRIBUTE, first.fingerprint())))
                .isTrue();
    }

    @Test
    @DisplayName("ACL 内容变化会使旧连接指纹过期")
    void invalidatesFingerprintWhenRulesChange() {
        MqttAuthorization current = service("debug/#")
                .resolve(DEVICE, "dbg-71");
        MqttAuthorizationService changed = service("#");

        assertThat(
                changed.isCurrent(
                        DEVICE,
                        "dbg-71",
                        Map.of(MqttAuthorizationService.FINGERPRINT_ATTRIBUTE, current.fingerprint())))
                .isFalse();
        assertThat(changed.isCurrent(DEVICE, "dbg-71", Map.of())).isFalse();
    }

    @Test
    @DisplayName("当前 App 隔离参与相同 ACL 的指纹")
    void usesCurrentAppIsolationInFingerprint() {
        MqttAuthorization first = service("#").resolve(DEVICE, "dbg-71");
        MqttAuthorization second = service("#").resolve(DEVICE, "dbg-71");

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    private static MqttAuthorizationService service(String allowedTopic) {
        MqttAuthorizationPolicy policy = new MqttAuthorizationPolicy() {
            @Override
            public boolean supports(Device device) {
                return true;
            }

            @Override
            public List<EmqxAclRule> rules(Device device, String clientId) {
                return List.of(
                        new EmqxAclRule("allow", "all", allowedTopic, List.of(0, 1, 2), null),
                        new EmqxAclRule("deny", "all", "#", List.of(0, 1, 2), null));
            }
        };
        return new MqttAuthorizationService(new MqttProtocolCatalog(List.of()), List.of(policy));
    }
}
