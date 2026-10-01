package com.sca4d.gateway.routing;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;

/** 把连接 Server 实例失败转换为 502，不改变其他 HTTP 异常。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRoutingExceptionHandler implements WebExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRoutingExceptionHandler.class);

    /** @return 已转换为 502 的空响应，或继续传播非连接异常 */
    @Override
    public @NotNull Mono<Void> handle(
            @NotNull ServerWebExchange exchange,
            @NotNull Throwable failure) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(failure);
        }
        if (connectionFailure(failure)) {
            String path = exchange.getRequest().getPath().value();
            Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            LOGGER.warn(
                    "Gateway 上游连接失败，当前请求返回 502 | routeId={} path={} failureType={}",
                    route == null ? "unknown" : route.getId(),
                    path,
                    failure.getClass().getSimpleName());
            exchange.getResponse().setStatusCode(HttpStatus.BAD_GATEWAY);
            return exchange.getResponse().setComplete();
        }
        return Mono.error(failure);
    }

    /** 判断异常链中是否包含连接 Server 的 TCP 失败。 */
    private static boolean connectionFailure(@NotNull Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
