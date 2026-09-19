package com.sca4d.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/** 验证 Gateway 在 Netty 写出响应前恢复公开 API Location。 */
class LocationPrefixFilterTest {

    @Test
    @DisplayName("普通 API 的站内 Location 补回公开前缀")
    void prefixesRelativeLocationBeforeCommit() {
        LocationPrefixFilter filter = new LocationPrefixFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/resource").build());

        filter.filter(exchange, forwarded -> {
            forwarded.getResponse().getHeaders().set(HttpHeaders.LOCATION, "/target");
            return Mono.empty();
        }).block();

        assertThat(filter.getOrder())
                .isEqualTo(NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER + 1);
        assertThat(exchange.getResponse().getHeaders().getLocation())
                .hasToString("/api/target");
    }
}
