package com.sca4d.gateway.routing;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

import com.sca4d.core.api.gateway.GatewayConstants;
import com.sca4d.gateway.config.GatewayProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** 为注册 Server 路由写入可信 Header，并可恢复外部路径前缀。 */
@Component
public class RegisteredServerGatewayFilterFactory
        extends
            AbstractGatewayFilterFactory<RegisteredServerGatewayFilterFactory.Config> {

    private static final String EXTERNAL_PREFIX = "externalPrefix";

    private final @NotNull String backendServiceId;

    private final @NotNull String secret;

    /** @param properties 已校验的 Gateway 运行配置 */
    public RegisteredServerGatewayFilterFactory(@NotNull GatewayProperties properties) {
        super(Config.class);
        this.secret = properties.validatedSecret();
        this.backendServiceId = properties.validatedBackendServiceId();
    }

    /** @return YAML shortcut 参数顺序；唯一参数是可选外部路径前缀 */
    @Override
    public @NotNull List<String> shortcutFieldOrder() {
        return List.of(EXTERNAL_PREFIX);
    }

    /** @return 仅向配置的注册后端转发可信值的路由过滤器 */
    @Override
    public @NotNull GatewayFilter apply(@NotNull Config config) {
        String externalPrefix = normalizePrefix(config.getExternalPrefix());
        return (exchange, chain) -> {
            requireRegisteredServerRoute(exchange);
            ServerHttpRequest request = trustedRequest(exchange, externalPrefix);
            ServerWebExchange trustedExchange = exchange.mutate().request(request).build();
            return chain.filter(trustedExchange).then(restoreLocation(trustedExchange, externalPrefix));
        };
    }

    /** 创建已剥离外部前缀并写入可信控制值的请求。 */
    private @NotNull ServerHttpRequest trustedRequest(
            @NotNull ServerWebExchange exchange,
            @Nullable String externalPrefix) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest.Builder builder = request.mutate();
        if (externalPrefix != null) {
            String path = request.getURI().getRawPath();
            String internalPath = stripPrefix(path, externalPrefix);
            addOriginalRequestUrl(exchange, request.getURI());
            builder.path(internalPath);
        }
        builder.headers(headers -> {
            removeInternalHeaders(headers);
            headers.set(GatewayConstants.SECRET_HEADER, secret);
            String clientAddress = clientAddress(request.getRemoteAddress());
            if (clientAddress != null) {
                headers.set(GatewayConstants.CLIENT_IP_HEADER, clientAddress);
            }
            if (externalPrefix != null) {
                headers.set(GatewayConstants.EXTERNAL_PREFIX_HEADER, externalPrefix);
            }
        });
        return builder.build();
    }

    /** 验证过滤器没有被误用到任意外部目标。 */
    private void requireRegisteredServerRoute(@NotNull ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        String serviceId = route == null ? null : loadBalancerServiceId(route.getUri());
        if (!backendServiceId.equals(serviceId)) {
            String routeId = route == null ? "unknown" : route.getId();
            throw new IllegalStateException(
                    "RegisteredServer 只能用于 backend service ID `" + backendServiceId + "`，route=" + routeId);
        }
    }

    /** 返回 lb:// 或 lb:ws:// URI 中的 service ID。 */
    private static @Nullable String loadBalancerServiceId(@NotNull URI uri) {
        if (!"lb".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        if (uri.getHost() != null) {
            return uri.getHost();
        }
        String nestedValue = uri.getRawSchemeSpecificPart();
        try {
            URI nested = URI.create(nestedValue);
            return nested.getHost();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** 删除转发给 Server 前不应保留的外部代理与内部控制 Header。 */
    private static void removeInternalHeaders(@NotNull HttpHeaders headers) {
        for (String name : new ArrayList<>(headers.keySet())) {
            if (name.regionMatches(true, 0, GatewayConstants.HEADER_PREFIX, 0, GatewayConstants.HEADER_PREFIX.length())
                    || name.regionMatches(
                            true,
                            0,
                            GatewayConstants.FORWARDED_HEADER_PREFIX,
                            0,
                            GatewayConstants.FORWARDED_HEADER_PREFIX.length())
                    || GatewayConstants.FORWARDED_HEADER.equalsIgnoreCase(name)) {
                headers.remove(name);
            }
        }
    }

    /** @return Reactor Netty 已按 trusted-proxies 解析后的客户端地址 */
    private static @Nullable String clientAddress(@Nullable InetSocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return null;
        }
        return remoteAddress.getAddress() == null
                ? remoteAddress.getHostString()
                : remoteAddress.getAddress().getHostAddress();
    }

    /** 返回移除精确外部前缀后的内部路径。 */
    private static @NotNull String stripPrefix(@NotNull String path, @NotNull String prefix) {
        if (path.equals(prefix)) {
            return "/";
        }
        if (!path.startsWith(prefix + "/")) {
            throw new IllegalStateException("请求路径不包含 RegisteredServer 配置的外部前缀: " + prefix);
        }
        return path.substring(prefix.length());
    }

    /** 在响应提交前恢复站内 Location 的外部路径前缀。 */
    private static @NotNull Mono<Void> restoreLocation(
            @NotNull ServerWebExchange exchange,
            @Nullable String externalPrefix) {
        return Mono.fromRunnable(() -> {
            if (externalPrefix == null) {
                return;
            }
            HttpHeaders headers = exchange.getResponse().getHeaders();
            URI location = headers.getLocation();
            if (location == null || location.isAbsolute()) {
                return;
            }
            String value = location.toString();
            if (value.startsWith("/")
                    && !value.equals(externalPrefix)
                    && !value.startsWith(externalPrefix + "/")) {
                headers.setLocation(URI.create(externalPrefix + value));
            }
        });
    }

    /** 返回去除首尾空白后的可选绝对路径前缀；值为空或只有 `/` 时返回 null。 */
    private static @Nullable String normalizePrefix(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String prefix = value.strip();
        if ("/".equals(prefix)) {
            return null;
        }
        if (!prefix.startsWith("/")
                || prefix.endsWith("/")
                || prefix.contains("?")
                || prefix.contains("#")
                || prefix.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("RegisteredServer external-prefix 必须是无结尾斜杠的绝对路径");
        }
        return prefix;
    }

    /** 保存 YAML 路由为注册 Server 配置的可选外部路径前缀。 */
    public static class Config {

        private @Nullable String externalPrefix;

        /** @return 转发前移除并通过可信 Header 告知 Server 的外部路径前缀 */
        public @Nullable String getExternalPrefix() {
            return externalPrefix;
        }

        /** @param externalPrefix 可选绝对路径前缀 */
        public void setExternalPrefix(@Nullable String externalPrefix) {
            this.externalPrefix = externalPrefix;
        }
    }
}
