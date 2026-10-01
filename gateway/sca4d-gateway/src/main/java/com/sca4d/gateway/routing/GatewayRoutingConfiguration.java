package com.sca4d.gateway.routing;

import com.sca4d.gateway.config.GatewayProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientSpecification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 把配置的注册后端 service ID 连接到 Gateway 自有实例选择器。 */
@Configuration(proxyBeanMethods = false)
public class GatewayRoutingConfiguration {

    /** @return 使用当前 backend service ID 的 LoadBalancer 子上下文配置 */
    @Bean
    @NotNull
    LoadBalancerClientSpecification registeredServerLoadBalancerSpecification(
            @NotNull GatewayProperties properties) {
        return new LoadBalancerClientSpecification(
                properties.validatedBackendServiceId(),
                new Class<?>[] {GatewayLoadBalancerConfiguration.class});
    }
}
