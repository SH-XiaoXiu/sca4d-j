package com.sca4d.core.spring.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.gateway.GatewayCallback;
import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.gateway.GatewayCallbackRequest;
import com.sca4d.core.api.gateway.GatewayCallbackResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 验证 GatewayCallbackDispatcher 按回调编码（code）查找回调、把 JSON 转换为请求类型，并原样返回回调的 HTTP 状态、Header 和 body。 */
class GatewayCallbackDispatcherTest {

    @Test
    @DisplayName("按回调编码转换请求并保留状态 Header 和 body")
    void dispatchesTypedCallback() throws Exception {
        GatewayCallbackDispatcher dispatcher = new GatewayCallbackDispatcher(
                List.of(new TestCallback()),
                new ObjectMapper());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, "header");

        var response = dispatcher.dispatch(
                "test.callback",
                headers,
                new ObjectMapper().readTree("{\"value\":\"body\"}"));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getHeaders().getFirst("Location")).isEqualTo("/accepted");
        assertThat(response.getBody()).isEqualTo(Map.of("result", "header:body"));
    }

    @Test
    @DisplayName("未知回调编码返回 404")
    void returnsNotFoundForUnknownCode() {
        GatewayCallbackDispatcher dispatcher = new GatewayCallbackDispatcher(List.of(), new ObjectMapper());

        assertThat(dispatcher.dispatch("missing", new HttpHeaders(), null).getStatusCode().value())
                .isEqualTo(404);
    }

    /** 回调分发测试的请求体类型，字段 value 由请求 JSON 中的 value 绑定。 */
    public static class TestInput {

        private String value;

        /** @return JSON 中的测试值 */
        public String getValue() {
            return value;
        }

        /** @param value JSON 中的测试值 */
        public void setValue(String value) {
            this.value = value;
        }
    }

    /** 返回可核对 HTTP 结果的测试回调。 */
    private static class TestCallback implements GatewayCallback<TestInput, Map<String, String>> {

        @Override
        public @NotNull GatewayCallbackCode code() {
            return new GatewayCallbackCode("test.callback");
        }

        @Override
        public @NotNull Class<TestInput> requestType() {
            return TestInput.class;
        }

        @Override
        public @NotNull GatewayCallbackResponse<Map<String, String>> respond(
                @NotNull GatewayCallbackRequest<TestInput> request) {
            return new GatewayCallbackResponse<>(
                    202,
                    Map.of("Location", List.of("/accepted")),
                    Map.of(
                            "result",
                            request.header("user-agent").orElseThrow()
                                    + ":"
                                    + Objects.requireNonNull(request.body(), "测试回调请求体不能为空").getValue()));
        }
    }
}
