package com.sca4d.gateway.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关运行配置。
 */
@ConfigurationProperties(prefix = "sca4dj.gateway")
@Component("sca4djGatewayProperties")
public class Sca4djGatewayProperties {

    /**
     * upstream 外部配置文件路径。
     */
    private String upstreamsFile = "config/gateway-upstreams.yml";

    /**
     * upstream 文件轮询间隔，单位毫秒。
     */
    private long reloadIntervalMillis = 5000;

    /**
     * 实例健康检查间隔，单位毫秒。
     */
    private long healthIntervalMillis = 5000;

    /**
     * 获取静态 upstream 配置文件路径。
     *
     * @return 相对工作目录或绝对文件系统路径
     */
    public @NotNull String getUpstreamsFile() {
        return upstreamsFile;
    }

    /**
     * 设置静态 upstream 配置文件路径。
     *
     * @param upstreamsFile 外部化配置提供的文件路径
     */
    public void setUpstreamsFile(@NotNull String upstreamsFile) {
        this.upstreamsFile = upstreamsFile;
    }

    /**
     * 获取配置文件轮询间隔。
     *
     * @return 轮询间隔毫秒数
     */
    public long getReloadIntervalMillis() {
        return reloadIntervalMillis;
    }

    /**
     * 设置配置文件轮询间隔。
     *
     * @param reloadIntervalMillis 轮询间隔毫秒数
     */
    public void setReloadIntervalMillis(long reloadIntervalMillis) {
        this.reloadIntervalMillis = reloadIntervalMillis;
    }

    /**
     * 获取实例主动健康检查间隔。
     *
     * @return 健康检查间隔毫秒数
     */
    public long getHealthIntervalMillis() {
        return healthIntervalMillis;
    }

    /**
     * 设置实例主动健康检查间隔。
     *
     * @param healthIntervalMillis 健康检查间隔毫秒数
     */
    public void setHealthIntervalMillis(long healthIntervalMillis) {
        this.healthIntervalMillis = healthIntervalMillis;
    }
}
