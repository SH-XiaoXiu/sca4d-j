package com.sca4d.gateway.loki;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.core.api.setting.ConfiguredConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/** 验证 Loki 写入路由只接受独立凭据且不会转发认证信息。 */
class LokiPushAuthenticationFilterTest {

    @BeforeAll
    static void initializeGatewayConstants() {
        ConfiguredConstants.initialize(GatewayConstants.class, ignored -> null);
    }

    @Test
    @DisplayName("正确 Basic Auth 继续转发并删除 Authorization 和 Gateway Header")
    void forwardsWithoutSensitiveHeadersForValidCredential() {
        LokiPushAuthenticationFilter filter = new LokiPushAuthenticationFilter(properties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/internal/logging/push")
                        .header(HttpHeaders.AUTHORIZATION, basic("alloy", "logging-secret"))
                        .header(GatewayConstants.SECRET_HEADER, "gateway-secret")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, current -> {
            forwarded.set(true);
            assertThat(current.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION)).isNull();
            assertThat(current.getRequest().getHeaders().get(GatewayConstants.SECRET_HEADER)).isNull();
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("错误或重复 Authorization 返回 401")
    void rejectsInvalidOrRepeatedAuthorization() {
        LokiPushAuthenticationFilter filter = new LokiPushAuthenticationFilter(properties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/internal/logging/push")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basic("alloy", "wrong"),
                                basic("alloy", "logging-secret"))
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, current -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Basic realm=\"Loki push\"");
    }

    /** 创建启用日志写入且包含固定测试凭据的配置。 */
    private static LokiGatewayProperties properties() {
        LokiGatewayProperties properties = new LokiGatewayProperties();
        properties.setEnabled(true);
        properties.setLokiOrigin("http://127.0.0.1:3100");
        properties.setUsername("alloy");
        properties.setPassword("logging-secret");
        return properties;
    }

    /** 返回 UTF-8 Basic Auth Header。 */
    private static String basic(String username, String password) {
        String credential = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
    }
}
