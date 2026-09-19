package com.sca4d.gateway.routing;

import com.sca4d.core.api.gateway.GatewayHttpPaths;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/** 在 Gateway 写出响应前为普通 API 的站内 Location 恢复公开路径前缀。 */
@Component
public final class LocationPrefixFilter implements GlobalFilter, Ordered {

    /** @return 紧跟 Netty 响应写出过滤器之后进入请求链，响应时先于 Netty 写出执行 */
    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER + 1;
    }

    /** @return Server 响应到达后已恢复公开 Location 的过滤结果 */
    @Override
    public @NotNull Mono<Void> filter(
            @NotNull ServerWebExchange exchange,
            @NotNull GatewayFilterChain chain) {
        boolean apiRequest = exchange.getRequest()
                .getPath()
                .value()
                .startsWith(GatewayHttpPaths.API_PREFIX + "/");
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            if (!apiRequest) {
                return;
            }
            HttpHeaders headers = exchange.getResponse().getHeaders();
            URI location = headers.getLocation();
            if (location == null || location.isAbsolute()) {
                return;
            }
            String path = location.toString();
            if (path.startsWith("/") && !path.startsWith(GatewayHttpPaths.API_PREFIX + "/")) {
                headers.setLocation(URI.create(GatewayHttpPaths.API_PREFIX + path));
            }
        }));
    }
}
