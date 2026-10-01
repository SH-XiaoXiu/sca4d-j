package com.sca4d.core.api.gateway;

import static com.sca4d.core.api.name.NameFormat.DOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证 Gateway 回调公共类型保持稳定的编码、Header 和 HTTP 结果语义。 */
class GatewayCallbackContractTest {

    @Test
    @DisplayName("回调编码复用稳定名称规则并输出大写点式编码")
    void validatesCallbackCode() {
        assertEquals("IOT.EMQX.AUTHENTICATE", new GatewayCallbackCode("iot.emqx.authenticate").format(DOT));
        assertThrows(IllegalArgumentException.class, () -> new GatewayCallbackCode("IOT//AUTHENTICATE"));
    }

    @Test
    @DisplayName("请求 Header 大小写不敏感并与原集合隔离")
    void copiesCaseInsensitiveRequestHeaders() {
        ArrayList<String> values = new ArrayList<>(List.of("secret"));
        GatewayCallbackRequest<String> request = new GatewayCallbackRequest<>(Map.of("X-Iot-Secret", values), "body");
        values.set(0, "changed");

        assertEquals("secret", request.header("x-iot-secret").orElseThrow());
        assertThrows(
                UnsupportedOperationException.class,
                () -> request.headers().put("Other", List.of("value")));
    }

    @Test
    @DisplayName("响应保留原始 HTTP 状态和可选 JSON body")
    void keepsRawHttpResponse() {
        GatewayCallbackResponse<String> response = new GatewayCallbackResponse<>(
                202,
                Map.of("Location", List.of("/1")),
                null);

        assertEquals(202, response.status());
        assertEquals(List.of("/1"), response.headers().get("location"));
        assertTrue(response.body().isEmpty());
    }

    @Test
    @DisplayName("实例注册请求和响应使用同一份 JSON 契约")
    void serializesRegistrationContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GatewayRegistrationRequest request = new GatewayRegistrationRequest(
                "server-1",
                URI.create("http://localhost:8080"),
                "/actuator/health/readiness",
                "test-fingerprint",
                Set.of("iot.emqx.authenticate"),
                Set.of(),
                Map.of());

        GatewayRegistrationRequest decoded = mapper.readValue(
                mapper.writeValueAsBytes(request),
                GatewayRegistrationRequest.class);
        GatewayRegistrationResponse response = mapper.readValue(
                "{\"leaseId\":\"lease-1\"}",
                GatewayRegistrationResponse.class);

        assertEquals("server-1", decoded.getInstanceId());
        assertEquals(URI.create("http://localhost:8080"), decoded.getBaseUrl());
        assertEquals("test-fingerprint", decoded.getProtocolFingerprint());
        assertEquals(Set.of("iot.emqx.authenticate"), decoded.getCallbackCodes());
        assertEquals("lease-1", response.getLeaseId());
    }
}
