package com.sca4d.gateway.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关默认路由配置。
 */
@Configuration
public class GatewayRouteConfig {

    /**
     * 注册脚手架主应用默认路由。
     *
     * @param builder Spring Gateway 提供的路由构造器
     * @return 供 Gateway 注册的路由定义
     */
    @Bean
    public @NotNull RouteLocator sca4dGatewayRoutes(@NotNull RouteLocatorBuilder builder) {
        return builder
                .routes()
                .route("sca4d-app-api", route -> route.path("/api/**").uri("lb://sca4d-app"))
                .build();
    }
}
