package com.sca4d.app.server.iot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.gateway.GatewayCallbackRequest;
import com.sca4d.iot.emqx.authentication.EmqxAuthenticationRequest;
import com.sca4d.iot.emqx.authentication.EmqxAuthenticationResponse;
import com.sca4d.iot.emqx.authentication.EmqxAuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 验证 EMQX HTTP 认证回调保留端到端共享 Secret 和既有 Service 行为。 */
class EmqxCallbackTest {

    @Test
    @DisplayName("认证回调返回 EMQX 原始 JSON")
    void authenticatesWithEndToEndSecret() {
        EmqxAuthenticationService service = mock(EmqxAuthenticationService.class);
        EmqxAuthenticationRequest input = new EmqxAuthenticationRequest("device", "anonymous", null, null);
        EmqxAuthenticationResponse output = EmqxAuthenticationResponse.deny();
        when(service.authenticate(input)).thenReturn(output);

        assertThat(
                new EmqxAuthenticationCallback(service, authenticator())
                        .respond(request(input))
                        .body())
                .contains(output);
    }

    @Test
    @DisplayName("旧 Header 和错误 Secret 都不能调用回调")
    void rejectsLegacyOrInvalidSecret() {
        EmqxAuthenticationCallback callback = new EmqxAuthenticationCallback(
                mock(EmqxAuthenticationService.class),
                authenticator());

        assertThatThrownBy(
                () -> callback.respond(
                        new GatewayCallbackRequest<>(
                                Map.of("Iot-Emqx-Secret", List.of("secret")),
                                new EmqxAuthenticationRequest(
                                        "device",
                                        "anonymous",
                                        null,
                                        null))))
                .isInstanceOf(ResponseStatusException.class);
    }

    /** 创建使用新 Header 和有效 secret 的回调请求。 */
    private static <T> GatewayCallbackRequest<T> request(T body) {
        return new GatewayCallbackRequest<>(
                Map.of(IotHttpHeaders.EMQX_SECRET, List.of("secret")),
                body);
    }

    /** 创建使用固定 Secret 的 EMQX 回调认证器。 */
    private static EmqxCallbackAuthenticator authenticator() {
        AppIotProperties properties = new AppIotProperties();
        properties.setEmqxWebhookSecret("secret");
        return new EmqxCallbackAuthenticator(properties);
    }
}
