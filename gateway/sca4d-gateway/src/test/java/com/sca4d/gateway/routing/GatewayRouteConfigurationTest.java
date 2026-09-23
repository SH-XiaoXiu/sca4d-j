package com.sca4d.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.gateway.Sca4dGatewayApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;

import java.time.Duration;

/** 验证标准 Spring Cloud Gateway YAML 能够装载默认注册 Server 路由。 */
@SpringBootTest(classes = Sca4dGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sca4dj.gateway.secret=test-secret",
        "sca4dj.gateway.backend-service-id=emergency-light-server",
        "spring.profiles.active=test"
})
class GatewayRouteConfigurationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private LoadBalancerClientFactory loadBalancerClientFactory;

    @Test
    @DisplayName("默认 YAML 注册 API、实时和回调路由")
    void loadsDefaultYamlRoutes() {
        var routes = routeLocator.getRoutes().collectList().block(Duration.ofSeconds(5));

        assertThat(routes)
                .isNotNull()
                .extracting(route -> route.getId())
                .contains(
                        "sca4d-events-websocket",
                        "sca4d-events-sse",
                        "sca4d-callbacks",
                        "sca4d-api");
        assertThat(routes)
                .filteredOn(route -> route.getId().startsWith("sca4d-"))
                .allSatisfy(
                        route -> assertThat(route.getUri().toString())
                                .contains("emergency-light-server"));
        assertThat(
                loadBalancerClientFactory.getInstance(
                        "emergency-light-server",
                        ReactorServiceInstanceLoadBalancer.class))
                .isInstanceOf(GatewayLoadBalancer.class);
    }
}
