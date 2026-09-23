package com.sca4d.gateway.loki;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;

/** 在依赖存在且显式启用时注册受限 Loki 日志写入代理。 */
@AutoConfiguration
@ConditionalOnClass(RouteLocator.class)
@ConditionalOnProperty(prefix = "sca4dj.gateway.logging", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LokiGatewayProperties.class)
public class LokiGatewayAutoConfiguration {

    /** 创建 Loki addon 自动配置。 */
    public LokiGatewayAutoConfiguration() {
    }

    /** @return 仅接受 POST 并执行独立 Basic Auth 的 Loki push 路由 */
    @Bean
    @ConditionalOnMissingBean(name = "lokiPushRouteLocator")
    @NotNull
    RouteLocator lokiPushRouteLocator(
            @NotNull RouteLocatorBuilder builder,
            @NotNull LokiGatewayProperties properties,
            @NotNull Environment environment) {
        properties.validate(environment);
        return builder.routes()
                .route(
                        "centralized-logging-push",
                        route -> route
                                .path(properties.getPushPath())
                                .and()
                                .method(HttpMethod.POST)
                                .filters(
                                        filters -> filters
                                                .filter(new LokiPushAuthenticationFilter(properties))
                                                .setPath(properties.getLokiPath()))
                                .uri(properties.validatedLokiOrigin().toString()))
                .build();
    }
}
