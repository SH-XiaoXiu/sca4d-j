package com.sca4d.core.spring.config;

import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslOptions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/** 把 Spring Boot Redis 连接信息转换成等价的 Redisson 连接配置。 */
class RedissonConfigFactory {

    private RedissonConfigFactory() {
    }

    /**
     * 转换单机、Sentinel 或 Cluster 连接信息，并保留认证与 SSL Bundle。
     *
     * @param details Spring Boot 解析后的 Redis 连接信息
     * @return Redisson 连接配置
     */
    static Config create(RedisConnectionDetails details) {
        RedisConnectionDetails checked = Objects.requireNonNull(details, "Redis 连接信息不能为空");
        Config config = new Config();
        RedisConnectionDetails.Sentinel sentinel = checked.getSentinel();
        if (sentinel != null) {
            SentinelServersConfig servers = config.useSentinelServers()
                    .setMasterName(sentinel.getMaster())
                    .setDatabase(sentinel.getDatabase())
                    .addSentinelAddress(
                            sentinel.getNodes()
                                    .stream()
                                    .map(node -> address(node, sentinel.getSslBundle()))
                                    .toArray(String[]::new));
            servers.setSentinelUsername(sentinel.getUsername());
            servers.setSentinelPassword(sentinel.getPassword());
            configureCommon(config, checked, sentinel.getSslBundle());
            return config;
        }

        RedisConnectionDetails.Cluster cluster = checked.getCluster();
        if (cluster != null) {
            config.useClusterServers()
                    .addNodeAddress(
                            cluster.getNodes()
                                    .stream()
                                    .map(node -> address(node, cluster.getSslBundle()))
                                    .toArray(String[]::new));
            configureCommon(config, checked, cluster.getSslBundle());
            return config;
        }

        RedisConnectionDetails.Standalone standalone = checked.getStandalone();
        if (standalone == null) {
            throw new IllegalStateException("Redis 连接信息没有单机、Sentinel 或 Cluster 地址");
        }
        config.useSingleServer()
                .setAddress(
                        address(
                                new RedisConnectionDetails.Node(standalone.getHost(), standalone.getPort()),
                                standalone.getSslBundle()))
                .setDatabase(standalone.getDatabase());
        configureCommon(config, checked, standalone.getSslBundle());
        return config;
    }

    /** 为服务器配置认证和 SSL 选项。 */
    private static void configureCommon(Config config, RedisConnectionDetails details, SslBundle sslBundle) {
        config.setUsername(details.getUsername());
        config.setPassword(details.getPassword());
        if (sslBundle == null) {
            return;
        }
        config.setSslKeyManagerFactory(sslBundle.getManagers().getKeyManagerFactory());
        config.setSslTrustManagerFactory(sslBundle.getManagers().getTrustManagerFactory());
        SslOptions options = sslBundle.getOptions();
        if (options.getEnabledProtocols() != null && options.getEnabledProtocols().length > 0) {
            config.setSslProtocols(options.getEnabledProtocols());
        }
        if (options.getCiphers() != null && options.getCiphers().length > 0) {
            config.setSslCiphers(options.getCiphers());
        }
    }

    /** 创建保留 IPv6 主机格式的 Redis URI。 */
    private static String address(RedisConnectionDetails.Node node, SslBundle sslBundle) {
        try {
            return new URI(sslBundle == null ? "redis" : "rediss", null, node.host(), node.port(), null, null, null)
                    .toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Redis 节点地址不正确", exception);
        }
    }
}
