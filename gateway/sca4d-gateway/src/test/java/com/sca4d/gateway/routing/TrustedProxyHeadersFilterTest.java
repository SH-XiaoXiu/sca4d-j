package com.sca4d.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.gateway.GatewayHttpHeaders;
import com.sca4d.gateway.config.GatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/** 验证 Gateway 删除伪造代理 Header 并写入自己的可信值。 */
class TrustedProxyHeadersFilterTest {

    @Test
    @DisplayName("伪造 Gateway 和 Forwarded Header 被覆盖或删除")
    void replacesUntrustedHeaders() {
        GatewayProperties properties = new GatewayProperties();
        properties.setSecret("trusted-secret");
        TrustedProxyHeadersFilter filter = new TrustedProxyHeadersFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource")
                        .remoteAddress(new InetSocketAddress("203.0.113.8", 12345))
                        .header(GatewayHttpHeaders.SECRET, "forged")
                        .header(GatewayHttpHeaders.CLIENT_IP, "198.51.100.9")
                        .header("X-Forwarded-Host", "forged.example")
                        .header("Forwarded", "for=198.51.100.9")
                        .build());

        filter.filter(exchange, forwarded -> {
            assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.SECRET))
                    .isEqualTo("trusted-secret");
            assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.CLIENT_IP))
                    .isEqualTo("203.0.113.8");
            assertThat(forwarded.getRequest().getHeaders().getFirst(GatewayHttpHeaders.EXTERNAL_PREFIX))
                    .isNull();
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Forwarded-Host"))
                    .isNull();
            assertThat(forwarded.getRequest().getHeaders().getFirst("Forwarded")).isNull();
            return Mono.empty();
        }).block();
    }
}
