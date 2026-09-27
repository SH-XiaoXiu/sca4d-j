package com.sca4d.iot.emqx.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.isolation.AppName;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.connection.DeviceConnectionAccess;
import com.sca4d.iot.api.connection.DeviceConnectionAdmission;
import com.sca4d.iot.api.connection.DeviceConnectionRequest;
import com.sca4d.iot.emqx.connection.MqttDeviceConnection;
import com.sca4d.iot.emqx.protocol.MqttProtocolCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Set;

/** 验证应用授权策略能替代普通 Device 的协议精确 ACL。 */
class EmqxAuthenticationServiceTest {

    static {
        AppName.publish("SCA4D_TEST");
    }

    @Test
    @DisplayName("匹配的扩展策略生成完整 ACL 且认证结果始终不是 superuser")
    void usesMatchingAuthorizationPolicyWithoutSuperuser() {
        Device device = new Device(
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
        EmqxDeviceConnectionRequestFactory requests = mock(EmqxDeviceConnectionRequestFactory.class);
        DeviceConnectionAdmission admission = mock(DeviceConnectionAdmission.class);
        DeviceConnectionRequest connectionRequest = DeviceConnectionRequest.anonymous(
                MqttDeviceConnection.clientId("dbg-71"));
        when(requests.create(ArgumentMatchers.any())).thenReturn(connectionRequest);
        when(admission.admit(connectionRequest))
                .thenReturn(
                        new DeviceConnectionAccess(
                                device,
                                DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET"),
                                null,
                                null));
        MqttAuthorizationPolicy policy = new MqttAuthorizationPolicy() {
            @Override
            public boolean supports(Device candidate) {
                return candidate.product().code().equals("mqtt-debug");
            }

            @Override
            public List<EmqxAclRule> rules(Device candidate, String clientId) {
                return List.of(
                        new EmqxAclRule("deny", "all", "$SYS/#", List.of(0, 1, 2), null),
                        new EmqxAclRule("allow", "all", "#", List.of(0, 1, 2), null),
                        new EmqxAclRule("deny", "all", "#", List.of(0, 1, 2), null));
            }
        };
        MqttAuthorizationService authorization = new MqttAuthorizationService(
                new MqttProtocolCatalog(List.of()),
                List.of(policy));
        EmqxAuthenticationService service = new EmqxAuthenticationService(requests, admission, authorization);

        EmqxAuthenticationResponse response = service.authenticate(
                new EmqxAuthenticationRequest("dbg-71", "ACCOUNT:AUTHENTICATION/SHARED_SECRET", "secret", null));

        assertThat(response.result()).isEqualTo("allow");
        assertThat(response.superuser()).isFalse();
        assertThat(response.clientAttributes())
                .containsEntry("product_code", "mqtt-debug")
                .containsEntry("product_source", "CODE")
                .containsEntry("access_method", "ACCOUNT:AUTHENTICATION:SHARED_SECRET")
                .containsKey(MqttAuthorizationService.FINGERPRINT_ATTRIBUTE);
        assertThat(response.acl()).extracting(EmqxAclRule::topic)
                .containsExactly("$SYS/#", "#", "#");
    }
}
