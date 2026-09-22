package com.sca4d.gateway.routing;

import com.sca4d.core.api.gateway.GatewayHttpHeaders;
import com.sca4d.gateway.config.GatewayProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/** 删除客户端提交的 Gateway/转发 Header，并为 Server 写入可信控制和客户端地址。 */
@Component
public final class TrustedProxyHeadersFilter implements GlobalFilter, Ordered {

    private final @NotNull String secret;

    /** @param properties 已校验的 Gateway 运行配置 */
    public TrustedProxyHeadersFilter(@NotNull GatewayProperties properties) {
        this.secret = properties.validatedSecret();
    }

    /** @return 在路由匹配和 Gateway 内置 ForwardedHeadersFilter 之前执行 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /** @return 使用清理后 Header 的后续 Gateway 过滤链 */
    @Override
    public @NotNull Mono<Void> filter(
            @NotNull ServerWebExchange exchange,
            @NotNull GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            removeUntrusted(headers);
            headers.set(GatewayHttpHeaders.SECRET, secret);
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                headers.set(GatewayHttpHeaders.CLIENT_IP, remoteAddress.getAddress().getHostAddress());
            }
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /** 删除所有客户端可伪造的控制和 Forwarded Header。 */
    private static void removeUntrusted(@NotNull HttpHeaders headers) {
        List<String> names = new ArrayList<>(headers.keySet());
        for (String name : names) {
            if (name.regionMatches(true, 0, GatewayHttpHeaders.PREFIX, 0, GatewayHttpHeaders.PREFIX.length())
                    || name.regionMatches(
                            true,
                            0,
                            GatewayHttpHeaders.FORWARDED_PREFIX,
                            0,
                            GatewayHttpHeaders.FORWARDED_PREFIX.length())
                    || GatewayHttpHeaders.FORWARDED.equalsIgnoreCase(name)) {
                headers.remove(name);
            }
        }
    }
}
