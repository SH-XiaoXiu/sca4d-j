package com.sca4d.iot.emqx.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 EMQX HTTP Authenticator 请求使用真实 MQTT CONNECT 字段名。 */
class EmqxAuthenticationRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("username 字段按 MQTT CONNECT 原值读取")
    void readsMqttUsername() throws Exception {
        EmqxAuthenticationRequest request = objectMapper.readValue(
                """
                        {
                          "clientid": "device-7",
                          "username": "account:shared-secret",
                          "password": "secret",
                          "cert_pem": null
                        }
                        """,
                EmqxAuthenticationRequest.class);

        assertThat(request.clientId()).isEqualTo("device-7");
        assertThat(request.username()).isEqualTo("account:shared-secret");
        assertThat(request.password()).isEqualTo("secret");
        assertThat(request.certificatePem()).isNull();
    }
}
