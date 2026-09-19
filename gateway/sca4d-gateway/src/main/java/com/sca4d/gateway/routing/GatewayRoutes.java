package com.sca4d.gateway.routing;

import com.sca4d.core.api.gateway.GatewayHttpHeaders;
import com.sca4d.core.api.gateway.GatewayHttpPaths;
import com.sca4d.gateway.config.GatewayProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** 注册日志写入、普通 API、SSE、WebSocket 和回调路由。 */
@Configuration(proxyBeanMethods = false)
public class GatewayRoutes {

    /** Spring Cloud LoadBalancer 使用的固定 Server service ID。 */
    public static final String SERVER_SERVICE_ID = "sca4d-server";

    private static final String EVENTS_PATH = GatewayHttpPaths.API_PREFIX + "/events";

    private static final String WEBSOCKET_PATH = EVENTS_PATH + "/ws";

    /** Alloy 写入中心日志时使用的固定 Gateway 路径。 */
    public static final String LOGGING_PUSH_PATH = "/internal/logging/push";

    /**
     * 创建 Gateway 路由；日志写入未启用时不注册对应路径。
     *
     * @param builder Spring Cloud Gateway 路由构造器
     * @param properties 已校验的 Gateway 配置
     * @return 按长连接优先级排列的路由定义
     */
    @Bean
    public @NotNull RouteLocator sca4dRouteLocator(
            @NotNull RouteLocatorBuilder builder,
            @NotNull GatewayProperties properties) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        GatewayProperties.Logging logging = properties.getLogging();
        if (logging.isEnabled()) {
            routes.route(
                    "centralized-logging-push",
                    route -> route
                            .path(LOGGING_PUSH_PATH)
                            .and()
                            .method(HttpMethod.POST)
                            .filters(
                                    filters -> filters
                                            .filter(new LokiPushAuthenticationFilter(logging))
                                            .setPath("/loki/api/v1/push"))
                            .uri(logging.requireLokiOrigin().toString()));
        }
        return routes
                .route(
                        "sca4d-events-websocket",
                        route -> route
                                .path(WEBSOCKET_PATH)
                                .filters(
                                        filters -> filters
                                                .stripPrefix(1)
                                                .setRequestHeader(
                                                        GatewayHttpHeaders.EXTERNAL_PREFIX,
                                                        GatewayHttpPaths.API_PREFIX))
                                .uri("lb:ws://" + SERVER_SERVICE_ID))
                .route(
                        "sca4d-events-sse",
                        route -> route
                                .path(EVENTS_PATH)
                                .filters(
                                        filters -> filters
                                                .stripPrefix(1)
                                                .setRequestHeader(
                                                        GatewayHttpHeaders.EXTERNAL_PREFIX,
                                                        GatewayHttpPaths.API_PREFIX))
                                .metadata("response-timeout", -1)
                                .uri("lb://" + SERVER_SERVICE_ID))
                .route(
                        "sca4d-callbacks",
                        route -> route
                                .path(GatewayHttpPaths.CALLBACK_PATTERN)
                                .uri("lb://" + SERVER_SERVICE_ID))
                .route(
                        "sca4d-api",
                        route -> route
                                .path(GatewayHttpPaths.API_PATTERN)
                                .filters(
                                        filters -> filters
                                                .stripPrefix(1)
                                                .setRequestHeader(
                                                        GatewayHttpHeaders.EXTERNAL_PREFIX,
                                                        GatewayHttpPaths.API_PREFIX))
                                .uri("lb://" + SERVER_SERVICE_ID))
                .build();
    }
}
