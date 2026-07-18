package com.sca4d.gateway.discovery;

import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;

/**
 * 基于静态 upstream 文件的响应式服务发现客户端。
 */
@Component
public class GatewayReactiveDiscoveryClient implements ReactiveDiscoveryClient {

    private final GatewayUpstreamRegistry registry;

    /**
     * 创建基于静态 upstream 注册表的响应式服务发现客户端。
     *
     * @param registry 提供当前健康实例的 upstream 注册表
     */
    public GatewayReactiveDiscoveryClient(@NotNull GatewayUpstreamRegistry registry) {
        this.registry = registry;
    }

    @Override
    public @NotNull String description() {
        return "sca4d static upstream discovery client";
    }

    @Override
    public @NotNull Flux<ServiceInstance> getInstances(@NotNull String serviceId) {
        return Flux.fromIterable(registry.availableInstances(serviceId)).map(instance -> serviceInstance(serviceId, instance));
    }

    @Override
    public @NotNull Flux<String> getServices() {
        return Flux.fromIterable(registry.serviceIds());
    }

    /**
     * 将网关实例转换为 Spring Cloud 负载均衡契约。
     *
     * @param serviceId Spring Cloud 请求的服务标识
     * @param instance 注册表筛选出的健康实例
     * @return 保留地址、安全协议和元数据的服务实例
     */
    private static @NotNull ServiceInstance serviceInstance(
            @NotNull String serviceId, @NotNull GatewayInstance instance) {
        URI uri = instance.getUri();
        int port = uri.getPort();
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        if (port < 0) {
            port = secure ? 443 : 80;
        }
        return new DefaultServiceInstance(instance.getId(), serviceId, uri.getHost(), port, secure, instance.getMetadata());
    }
}
