package com.sca4d.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.gateway.GatewayHttpHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/** 验证 Gateway 全局删除伪造控制 Header，但不向普通路由写入 Secret。 */
class GatewayControlHeadersFilterTest {

    @Test
    @DisplayName("伪造 Gateway Header 被删除且标准代理 Header 留给 Gateway 处理")
    void removesGatewayHeadersOnly() {
        GatewayControlHeadersFilter filter = new GatewayControlHeadersFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource")
                        .remoteAddress(new InetSocketAddress("203.0.113.8", 12345))
                        .header(GatewayHttpHeaders.SECRET, "forged")
                        .header(GatewayHttpHeaders.CLIENT_IP, "198.51.100.9")
                        .header("X-Forwarded-Host", "forged.example")
                        .header("Forwarded", "for=198.51.100.9")
                        .build());

        filter.filter(exchange, forwarded -> {
            assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.SECRET)).isNull();
            assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.CLIENT_IP)).isNull();
            assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.EXTERNAL_PREFIX))
                    .isNull();
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Forwarded-Host"))
                    .isEqualTo("forged.example");
            assertThat(forwarded.getRequest().getHeaders().getFirst("Forwarded"))
                    .isEqualTo("for=198.51.100.9");
            return Mono.empty();
        }).block();
    }
}
