package com.sca4d.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import com.sca4d.core.api.gateway.GatewayHttpHeaders;
import com.sca4d.gateway.config.GatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/** 验证只有注册 Server 路由能够获得可信 Header 和路径恢复。 */
class RegisteredServerGatewayFilterFactoryTest {

    @Test
    @DisplayName("注册 Server 路由剥离前缀并写入可信值")
    void preparesTrustedServerRequest() {
        RegisteredServerGatewayFilterFactory factory = new RegisteredServerGatewayFilterFactory(properties());
        RegisteredServerGatewayFilterFactory.Config config = new RegisteredServerGatewayFilterFactory.Config();
        config.setExternalPrefix("/api");
        MockServerWebExchange exchange = exchange("/api/resource", "lb://emergency-light-server");

        factory.apply(config).filter(exchange, forwarded -> {
            assertThat(forwarded.getRequest().getPath().value()).isEqualTo("/resource");
            assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.SECRET))
                    .isEqualTo("trusted-secret");
            assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.CLIENT_IP))
                    .isEqualTo("203.0.113.8");
            assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.EXTERNAL_PREFIX))
                    .isEqualTo("/api");
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Forwarded-For")).isNull();
            forwarded.getResponse().getHeaders().set(HttpHeaders.LOCATION, "/target");
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString("/api/target");
    }

    @Test
    @DisplayName("过滤器拒绝向任意 HTTP 目标泄漏 Gateway Secret")
    void rejectsNonRegisteredTarget() {
        RegisteredServerGatewayFilterFactory factory = new RegisteredServerGatewayFilterFactory(properties());
        MockServerWebExchange exchange = exchange("/proxy", "https://example.com");

        assertThatThrownBy(
                () -> factory.apply(new RegisteredServerGatewayFilterFactory.Config())
                        .filter(exchange, ignored -> Mono.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RegisteredServer");
    }

    @Test
    @DisplayName("注册 Server WebSocket 路由使用相同可信边界")
    void acceptsRegisteredWebSocketTarget() {
        RegisteredServerGatewayFilterFactory factory = new RegisteredServerGatewayFilterFactory(properties());
        MockServerWebExchange exchange = exchange("/events/ws", "lb:ws://emergency-light-server");

        factory.apply(new RegisteredServerGatewayFilterFactory.Config())
                .filter(exchange, forwarded -> {
                    assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.SECRET))
                            .isEqualTo("trusted-secret");
                    return Mono.empty();
                })
                .block();
    }

    /** 创建包含目标路由和伪造 Header 的测试 Exchange。 */
    private static MockServerWebExchange exchange(String path, String target) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .remoteAddress(new InetSocketAddress("203.0.113.8", 12345))
                        .header(GatewayHttpHeaders.SECRET, "forged")
                        .header("X-Forwarded-For", "198.51.100.9")
                        .build());
        exchange.getAttributes()
                .put(
                        GATEWAY_ROUTE_ATTR,
                        Route.async()
                                .id("test-route")
                                .uri(target)
                                .asyncPredicate(ignored -> Mono.just(true))
                                .build());
        return exchange;
    }

    /** 创建有效 Gateway 配置。 */
    private static GatewayProperties properties() {
        GatewayProperties properties = new GatewayProperties();
        properties.setSecret("trusted-secret");
        properties.setBackendServiceId("emergency-light-server");
        return properties;
    }
}
