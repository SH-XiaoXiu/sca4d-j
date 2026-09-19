package com.sca4d.gateway.routing;

import com.sca4d.gateway.registration.GatewayInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.util.Map;

/** 把租约注册表中的健康 Server 实例转换为 Spring Cloud LoadBalancer 实例。 */
public final class GatewayServiceInstance implements ServiceInstance {

    private final @NotNull GatewayInstance instance;

    /** @param instance 已通过租约和 readiness 筛选的 Server 实例 */
    public GatewayServiceInstance(@NotNull GatewayInstance instance) {
        this.instance = instance;
    }

    /** @return Server 注册时提供的实例标识 */
    @Override
    public @NotNull String getInstanceId() {
        return instance.instanceId();
    }

    /** @return Gateway 路由使用的固定 service ID */
    @Override
    public @NotNull String getServiceId() {
        return GatewayRoutes.SERVER_SERVICE_ID;
    }

    /** @return Server 地址中的主机 */
    @Override
    public @NotNull String getHost() {
        return instance.baseUrl().getHost();
    }

    /** @return Server 地址中的显式端口或协议默认端口 */
    @Override
    public int getPort() {
        int port = instance.baseUrl().getPort();
        return port >= 0 ? port : isSecure() ? 443 : 80;
    }

    /** @return Server 使用 HTTPS 时为 true */
    @Override
    public boolean isSecure() {
        return "https".equalsIgnoreCase(instance.baseUrl().getScheme());
    }

    /** @return Server 注册时提供的 HTTP(S) Origin */
    @Override
    public @NotNull URI getUri() {
        return instance.baseUrl();
    }

    /** @return 当前实现不通过 metadata 暴露回调或租约信息 */
    @Override
    public @NotNull @Unmodifiable Map<String, String> getMetadata() {
        return Map.of();
    }
}
