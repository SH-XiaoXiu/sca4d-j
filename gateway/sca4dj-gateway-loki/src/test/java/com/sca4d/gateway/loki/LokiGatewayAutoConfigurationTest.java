package com.sca4d.gateway.loki;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 验证 Loki addon 只在显式启用时自动注册路由。 */
class LokiGatewayAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LokiGatewayAutoConfiguration.class))
            .withUserConfiguration(GatewayRouteBuilderConfiguration.class);

    @Test
    @DisplayName("默认关闭时不创建 Loki RouteLocator")
    void doesNotRegisterRouteWhenDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("lokiPushRouteLocator");
            assertThat(context).doesNotHaveBean(LokiGatewayProperties.class);
        });
    }

    @Test
    @DisplayName("启用后自动注册唯一 Loki push 路由")
    void registersRouteWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "sca4dj.gateway.secret=gateway-secret",
                        "sca4dj.gateway.logging.enabled=true",
                        "sca4dj.gateway.logging.loki-origin=http://localhost:3100",
                        "sca4dj.gateway.logging.username=alloy",
                        "sca4dj.gateway.logging.password=logging-secret")
                .run(context -> {
                    assertThat(context).hasBean("lokiPushRouteLocator");
                    RouteLocator locator = context.getBean("lokiPushRouteLocator", RouteLocator.class);
                    assertThat(locator.getRoutes().collectList().block())
                            .singleElement()
                            .extracting(route -> route.getId())
                            .isEqualTo("centralized-logging-push");
                });
    }

    /** 提供构造 addon 路由所需的最小 Spring Cloud Gateway 协作对象。 */
    @Configuration(proxyBeanMethods = false)
    static class GatewayRouteBuilderConfiguration {

        @Bean
        WebFluxProperties webFluxProperties() {
            return new WebFluxProperties();
        }

        @Bean
        PathRoutePredicateFactory pathRoutePredicateFactory(WebFluxProperties properties) {
            return new PathRoutePredicateFactory(properties);
        }

        @Bean
        MethodRoutePredicateFactory methodRoutePredicateFactory() {
            return new MethodRoutePredicateFactory();
        }

        @Bean
        SetPathGatewayFilterFactory setPathGatewayFilterFactory() {
            return new SetPathGatewayFilterFactory();
        }

        @Bean
        RouteLocatorBuilder routeLocatorBuilder(ConfigurableApplicationContext context) {
            return new RouteLocatorBuilder(context);
        }
    }
}
