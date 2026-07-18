package com.sca4d.gateway.discovery;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 网关 upstream 服务的不可变定义。
 */
public final class GatewayUpstream {

    private final String serviceId;
    private final String healthPath;
    private final Duration connectTimeout;
    private final Duration responseTimeout;
    private final int healthyThreshold;
    private final int unhealthyThreshold;
    private final List<GatewayInstance> instances;

    /**
     * 创建一个 upstream 服务定义。
     *
     * @param serviceId 服务标识
     * @param healthPath 实例健康检查路径
     * @param connectTimeout 健康检查连接超时
     * @param responseTimeout 健康检查响应超时
     * @param healthyThreshold 标记健康所需连续成功次数
     * @param unhealthyThreshold 标记不健康所需连续失败次数
     * @param instances 静态配置的服务实例
     */
    public GatewayUpstream(
            @NotNull String serviceId,
            @NotNull String healthPath,
            @NotNull Duration connectTimeout,
            @NotNull Duration responseTimeout,
            int healthyThreshold,
            int unhealthyThreshold,
            @NotNull List<GatewayInstance> instances) {
        this.serviceId = serviceId;
        this.healthPath = healthPath;
        this.connectTimeout = connectTimeout;
        this.responseTimeout = responseTimeout;
        this.healthyThreshold = healthyThreshold;
        this.unhealthyThreshold = unhealthyThreshold;
        this.instances = List.copyOf(instances);
    }

    /**
     * 获取服务标识。
     *
     * @return 服务标识
     */
    public @NotNull String getServiceId() {
        return serviceId;
    }

    /**
     * 获取实例健康检查路径。
     *
     * @return 相对实例基础地址的健康检查路径
     */
    public @NotNull String getHealthPath() {
        return healthPath;
    }

    /**
     * 获取健康检查连接超时。
     *
     * @return 健康检查连接超时
     */
    public @NotNull Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * 获取健康检查响应超时。
     *
     * @return 健康检查响应超时
     */
    public @NotNull Duration getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * 获取标记健康所需的连续成功次数。
     *
     * @return 连续成功阈值
     */
    public int getHealthyThreshold() {
        return healthyThreshold;
    }

    /**
     * 获取标记不健康所需的连续失败次数。
     *
     * @return 连续失败阈值
     */
    public int getUnhealthyThreshold() {
        return unhealthyThreshold;
    }

    /**
     * 获取静态配置的服务实例。
     *
     * @return 不可变的服务实例列表
     */
    public @NotNull List<GatewayInstance> getInstances() {
        return instances;
    }

    /**
     * 生成实例健康状态键。
     *
     * @param instance upstream 中的实例
     * @return 服务、实例与地址共同组成的健康状态键
     */
    public @NotNull String healthKey(@NotNull GatewayInstance instance) {
        return serviceId + "/" + instance.getId() + "/" + instance.getUri();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GatewayUpstream upstream)) {
            return false;
        }
        return healthyThreshold == upstream.healthyThreshold
                && unhealthyThreshold == upstream.unhealthyThreshold
                && serviceId.equals(upstream.serviceId)
                && healthPath.equals(upstream.healthPath)
                && connectTimeout.equals(upstream.connectTimeout)
                && responseTimeout.equals(upstream.responseTimeout)
                && instances.equals(upstream.instances);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                serviceId,
                healthPath,
                connectTimeout,
                responseTimeout,
                healthyThreshold,
                unhealthyThreshold,
                instances);
    }
}
