package com.sca4d.gateway.routing;

import com.sca4d.core.api.gateway.GatewayHttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

/** 删除客户端提交的 Gateway 控制 Header，不向未声明信任的路由写入内部值。 */
@Component
public class GatewayControlHeadersFilter implements GlobalFilter, Ordered {

    /** @return 在路由匹配和 Gateway 内置 ForwardedHeadersFilter 之前执行 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /** @return 使用已删除伪造 Gateway Header 的过滤链后续部分 */
    @Override
    public @NotNull Mono<Void> filter(
            @NotNull ServerWebExchange exchange,
            @NotNull GatewayFilterChain chain) {
        return chain.filter(
                exchange.mutate()
                        .request(
                                exchange.getRequest()
                                        .mutate()
                                        .headers(GatewayControlHeadersFilter::removeGatewayHeaders)
                                        .build())
                        .build());
    }

    /** 删除全部大小写不敏感匹配的 Gateway 控制 Header。 */
    private static void removeGatewayHeaders(@NotNull HttpHeaders headers) {
        for (String name : new ArrayList<>(headers.keySet())) {
            if (name.regionMatches(
                    true,
                    0,
                    GatewayHttpHeaders.PREFIX,
                    0,
                    GatewayHttpHeaders.PREFIX.length())) {
                headers.remove(name);
            }
        }
    }
}
