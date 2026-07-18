package com.sca4d.gateway.discovery;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * 某一版本的完整 upstream 配置快照。
 */
public final class GatewayUpstreamSnapshot {

    private final long version;
    private final Map<String, GatewayUpstream> upstreams;

    /**
     * 创建完整 upstream 配置快照。
     *
     * @param version 配置版本号
     * @param upstreams 以服务标识索引的 upstream 定义
     */
    public GatewayUpstreamSnapshot(long version, @NotNull Map<String, GatewayUpstream> upstreams) {
        this.version = version;
        this.upstreams = Map.copyOf(upstreams);
    }

    /**
     * 获取配置版本号。
     *
     * @return 单调递增的配置版本号
     */
    public long getVersion() {
        return version;
    }

    /**
     * 获取按服务标识索引的 upstream 定义。
     *
     * @return 不可变的 upstream 定义映射
     */
    public @NotNull Map<String, GatewayUpstream> getUpstreams() {
        return upstreams;
    }

    /**
     * 创建没有任何 upstream 的初始快照。
     *
     * @return 版本为零的空快照
     */
    public static @NotNull GatewayUpstreamSnapshot empty() {
        return new GatewayUpstreamSnapshot(0, Map.of());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GatewayUpstreamSnapshot snapshot)) {
            return false;
        }
        return version == snapshot.version && upstreams.equals(snapshot.upstreams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, upstreams);
    }
}
