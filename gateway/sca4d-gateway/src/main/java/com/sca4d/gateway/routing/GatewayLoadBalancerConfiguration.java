package com.sca4d.gateway.routing;

import com.sca4d.gateway.registration.GatewayInstanceRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.context.annotation.Bean;

/** 为 `sca4d-server` 注册基于租约和回调编码选择实例的 LoadBalancer。 */
public class GatewayLoadBalancerConfiguration {

    /**
     * 创建 Server 实例选择器。
     *
     * @param registry 当前 Gateway 进程的实例注册表
     * @return 基于租约、readiness 和回调编码的实例选择器
     */
    @Bean
    @NotNull
    ReactorServiceInstanceLoadBalancer gatewayLoadBalancer(@NotNull GatewayInstanceRegistry registry) {
        return new GatewayLoadBalancer(registry);
    }
}
