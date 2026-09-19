package com.sca4d.gateway.routing;

import com.sca4d.core.api.gateway.GatewayHttpPaths;
import com.sca4d.gateway.registration.GatewayInstance;
import com.sca4d.gateway.registration.GatewayInstanceRegistry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import reactor.core.publisher.Mono;

/** 根据请求路径从内存注册表选择普通 API 或指定回调的健康 Server 实例。 */
public final class GatewayLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final @NotNull GatewayInstanceRegistry registry;

    /** @param registry 管理租约、readiness 和轮询游标的实例注册表 */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "registry 是注入的共享实例注册协作对象，负载均衡器必须读取同一个注册表")
    public GatewayLoadBalancer(@NotNull GatewayInstanceRegistry registry) {
        this.registry = registry;
    }

    /**
     * 为当前请求选择 Server 实例。
     *
     * @param request Spring Cloud Gateway 当前请求上下文
     * @return 一个健康实例或空响应
     */
    @Override
    public @NotNull Mono<Response<ServiceInstance>> choose(@NotNull Request request) {
        java.util.Optional<String> callbackCode = callbackCode(request);
        GatewayInstance selected = callbackCode.isPresent()
                ? registry.selectCallback(callbackCode.orElseThrow())
                : registry.selectApi();
        Response<ServiceInstance> response = selected == null
                ? new EmptyResponse()
                : new DefaultResponse(new GatewayServiceInstance(selected));
        return Mono.just(response);
    }

    /** 从负载均衡请求上下文中读取回调路径的最后一段编码。 */
    private static @NotNull java.util.Optional<String> callbackCode(@NotNull Request<?> request) {
        Object context = request.getContext();
        if (!(context instanceof org.springframework.cloud.client.loadbalancer.RequestDataContext requestContext)) {
            return java.util.Optional.empty();
        }
        String path = requestContext.getClientRequest().getUrl().getPath();
        String prefix = GatewayHttpPaths.CALLBACK_PREFIX;
        if (!path.startsWith(prefix) || path.length() == prefix.length()) {
            return java.util.Optional.empty();
        }
        String code = path.substring(prefix.length());
        return code.indexOf('/') < 0 ? java.util.Optional.of(code) : java.util.Optional.empty();
    }
}
