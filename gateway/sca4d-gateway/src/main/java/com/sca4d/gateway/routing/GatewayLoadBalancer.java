package com.sca4d.gateway.routing;

import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.gateway.GatewayHttpPaths;
import com.sca4d.gateway.registration.GatewayInstance;
import com.sca4d.gateway.registration.GatewayInstanceRegistry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import reactor.core.publisher.Mono;

import java.util.Optional;

/** 根据请求路径从内存注册表选择普通 API 或指定回调的健康 Server 实例。 */
public final class GatewayLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final @NotNull GatewayInstanceRegistry registry;

    private final @NotNull String serviceId;

    /**
     * @param registry 管理租约、readiness 和轮询游标的实例注册表
     * @param serviceId 当前注册 Server 集群的 LoadBalancer service ID
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "registry 是注入的共享实例注册协作对象，负载均衡器必须读取同一个注册表")
    public GatewayLoadBalancer(@NotNull GatewayInstanceRegistry registry, @NotNull String serviceId) {
        this.registry = registry;
        this.serviceId = serviceId;
    }

    /**
     * 为当前请求选择 Server 实例。
     *
     * @param request Spring Cloud Gateway 当前请求上下文
     * @return 一个健康实例或空响应
     */
    @Override
    public @NotNull Mono<Response<ServiceInstance>> choose(@NotNull Request request) {
        Optional<GatewayCallbackCode> callbackCode = callbackCode(request);
        GatewayInstance selected = callbackCode.isPresent()
                ? registry.selectCallback(callbackCode.orElseThrow())
                : registry.selectApi(requestPath(request));
        Response<ServiceInstance> response = selected == null
                ? new EmptyResponse()
                : new DefaultResponse(new GatewayServiceInstance(selected, serviceId));
        return Mono.just(response);
    }

    /** 从负载均衡请求上下文中读取回调路径的最后一段编码。 */
    private static @NotNull Optional<GatewayCallbackCode> callbackCode(@NotNull Request<?> request) {
        Object context = request.getContext();
        if (!(context instanceof RequestDataContext requestContext)) {
            return Optional.empty();
        }
        String path = requestContext.getClientRequest().getUrl().getPath();
        String prefix = GatewayHttpPaths.CALLBACK_PREFIX;
        if (!path.startsWith(prefix) || path.length() == prefix.length()) {
            return Optional.empty();
        }
        String code = path.substring(prefix.length());
        return code.indexOf('/') < 0 ? Optional.of(new GatewayCallbackCode(code)) : Optional.empty();
    }

    /** 从负载均衡上下文读取服务器路径，供入口能力筛选使用。 */
    private static @NotNull String requestPath(@NotNull Request<?> request) {
        Object context = request.getContext();
        return context instanceof RequestDataContext requestContext
                ? requestContext.getClientRequest().getUrl().getPath()
                : "/";
    }
}
