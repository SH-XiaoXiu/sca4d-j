package com.sca4d.gateway.routing;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

/** 把 `sca4d-server` service ID 连接到 Gateway 自有实例选择器。 */
@Configuration(proxyBeanMethods = false)
@LoadBalancerClients(@LoadBalancerClient(name = GatewayRoutes.SERVER_SERVICE_ID, configuration = GatewayLoadBalancerConfiguration.class))
public class GatewayRoutingConfiguration {
}
