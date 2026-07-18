package com.sca4d.gateway.discovery;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * 网关 upstream 实例的不可变配置。
 */
public final class GatewayInstance {

    private final String id;
    private final URI uri;
    private final boolean enabled;
    private final boolean draining;
    private final Map<String, String> metadata;

    /**
     * 创建一个网关 upstream 实例。
     *
     * @param id 实例标识
     * @param uri 实例基础地址
     * @param enabled 是否参与健康检查和路由
     * @param draining 是否仅保留已有连接并拒绝新请求
     * @param metadata 负载均衡可见的实例元数据
     */
    public GatewayInstance(
            @NotNull String id,
            @NotNull URI uri,
            boolean enabled,
            boolean draining,
            @NotNull Map<String, String> metadata) {
        this.id = id;
        this.uri = uri;
        this.enabled = enabled;
        this.draining = draining;
        this.metadata = Map.copyOf(metadata);
    }

    /**
     * 获取实例标识。
     *
     * @return 在所属 upstream 内唯一的实例标识
     */
    public @NotNull String getId() {
        return id;
    }

    /**
     * 获取实例基础地址。
     *
     * @return 实例基础地址
     */
    public @NotNull URI getUri() {
        return uri;
    }

    /**
     * 判断实例是否参与健康检查和路由。
     *
     * @return 配置启用时返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 判断实例是否正在排水下线。
     *
     * @return 仅保留已有连接时返回 true
     */
    public boolean isDraining() {
        return draining;
    }

    /**
     * 获取实例元数据。
     *
     * @return 不可变的实例元数据
     */
    public @NotNull Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 判断实例是否允许承接新请求。
     *
     * @return 配置启用且不处于排水状态时返回 true
     */
    public boolean routable() {
        return enabled && !draining;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GatewayInstance instance)) {
            return false;
        }
        return enabled == instance.enabled
                && draining == instance.draining
                && id.equals(instance.id)
                && uri.equals(instance.uri)
                && metadata.equals(instance.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uri, enabled, draining, metadata);
    }
}
