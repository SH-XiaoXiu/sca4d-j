package com.sca4d.gateway.discovery;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * upstream 文件加载器。
 */
@Component
public class GatewayUpstreamFileLoader {

    /**
     * 从 YAML 文件加载 upstream 快照。
     *
     * @param upstreamsFile 待读取的 upstream 配置文件路径
     * @param version 配置版本号
     * @return 从文件构建的完整 upstream 配置快照
     */
    public @NotNull GatewayUpstreamSnapshot load(@NotNull Path upstreamsFile, long version) {
        if (!Files.isRegularFile(upstreamsFile)) {
            throw new IllegalStateException("网关 upstream 文件不存在：" + upstreamsFile.toAbsolutePath());
        }
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(upstreamsFile));
        Properties properties = yaml.getObject();
        Map<String, Object> source = new LinkedHashMap<>();
        if (properties != null) {
            properties.forEach((key, value) -> source.put(String.valueOf(key), value));
        }
        GatewayUpstreamFileProperties fileProperties = new Binder(new MapConfigurationPropertySource(source))
                .bind("sca4dj.gateway", Bindable.of(GatewayUpstreamFileProperties.class))
                .orElseGet(GatewayUpstreamFileProperties::new);
        return new GatewayUpstreamSnapshot(version, upstreams(fileProperties));
    }

    /**
     * 将绑定后的 YAML 服务定义转换为不可变 upstream 映射。
     *
     * <p>空配置映射按没有服务处理；每个服务仍会经过完整的路径、阈值与实例校验。
     *
     * @param fileProperties Binder 绑定得到的顶层文件属性
     * @return 按服务标识索引的不可变 upstream 映射
     */
    private @NotNull Map<String, GatewayUpstream> upstreams(
            @NotNull GatewayUpstreamFileProperties fileProperties) {
        Map<String, GatewayUpstream> upstreams = new LinkedHashMap<>();
        fileProperties.getUpstreams().forEach((serviceId, properties) -> {
            if (properties == null) {
                throw new IllegalStateException("服务 " + serviceId + " 的 upstream 配置不能为空");
            }
            upstreams.put(serviceId, upstream(serviceId, properties));
        });
        return Map.copyOf(upstreams);
    }

    /**
     * 构建一个服务的运行时 upstream 定义。
     *
     * @param serviceId YAML 映射中的服务标识
     * @param properties 该服务的绑定配置
     * @return 经格式与阈值校验后的 upstream 定义
     * @throws IllegalStateException 服务标识、健康路径或阈值无效时抛出
     */
    private @NotNull GatewayUpstream upstream(
            @NotNull String serviceId, @NotNull GatewayUpstreamProperties properties) {
        if (!StringUtils.hasText(serviceId)) {
            throw new IllegalStateException("服务 ID 不能为空");
        }
        if (!serviceId.equals(serviceId.trim())) {
            throw new IllegalStateException("服务 ID 不能包含首尾空白：" + serviceId);
        }
        if (!StringUtils.hasText(properties.getHealthPath())) {
            throw new IllegalStateException("服务 " + serviceId + " 的健康检查路径不能为空");
        }
        if (properties.getConnectTimeout() == null
                || properties.getConnectTimeout().isZero()
                || properties.getConnectTimeout().isNegative()
                || properties.getConnectTimeout().compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new IllegalStateException("服务 " + serviceId + " 的连接超时必须在 1ms 到 Integer.MAX_VALUE ms 之间");
        }
        if (properties.getResponseTimeout() == null
                || properties.getResponseTimeout().isZero()
                || properties.getResponseTimeout().isNegative()) {
            throw new IllegalStateException("服务 " + serviceId + " 的响应超时必须大于 0");
        }
        if (properties.getHealthyThreshold() <= 0) {
            throw new IllegalStateException("服务 " + serviceId + " 的健康成功阈值必须大于 0");
        }
        if (properties.getUnhealthyThreshold() <= 0) {
            throw new IllegalStateException("服务 " + serviceId + " 的健康失败阈值必须大于 0");
        }
        List<GatewayInstance> instances = instances(serviceId, properties.getInstances());
        String healthPath = properties.getHealthPath().trim();
        return new GatewayUpstream(
                serviceId,
                healthPath.startsWith("/") ? healthPath : "/" + healthPath,
                properties.getConnectTimeout(),
                properties.getResponseTimeout(),
                properties.getHealthyThreshold(),
                properties.getUnhealthyThreshold(),
                instances);
    }

    /**
     * 构建一个服务全部实例，并拒绝重复实例标识。
     *
     * @param serviceId 所属服务标识，用于定位配置错误
     * @param properties Binder 读取的实例配置
     * @return 保持 YAML 声明顺序的不可变实例列表
     * @throws IllegalStateException 同一服务存在重复实例标识时抛出
     */
    private @NotNull List<GatewayInstance> instances(
            @NotNull String serviceId, @NotNull List<GatewayInstanceProperties> properties) {
        Map<String, GatewayInstance> instances = new LinkedHashMap<>();
        for (GatewayInstanceProperties instanceProperties : properties) {
            GatewayInstance instance = instance(serviceId, instanceProperties);
            if (instances.putIfAbsent(instance.getId(), instance) != null) {
                throw new IllegalStateException("服务 " + serviceId + " 存在重复实例 ID：" + instance.getId());
            }
        }
        return List.copyOf(new ArrayList<>(instances.values()));
    }

    /**
     * 将单个实例配置转换为可路由的运行时实例。
     *
     * <p>地址必须是具有主机部分的 HTTP 或 HTTPS URI，避免健康检查和服务发现接受无法访问的配置。
     *
     * @param serviceId 所属服务标识，用于定位配置错误
     * @param properties Binder 读取的实例配置
     * @return 经地址和元数据转换后的实例定义
     * @throws IllegalStateException 实例标识或地址不符合网关协议约束时抛出
     */
    private @NotNull GatewayInstance instance(
            @NotNull String serviceId, @NotNull GatewayInstanceProperties properties) {
        if (!StringUtils.hasText(properties.getId())) {
            throw new IllegalStateException("服务 " + serviceId + " 的实例 ID 不能为空");
        }
        if (!properties.getId().equals(properties.getId().trim())) {
            throw new IllegalStateException("服务 " + serviceId + " 的实例 ID 不能包含首尾空白：" + properties.getId());
        }
        if (!StringUtils.hasText(properties.getUri())) {
            throw new IllegalStateException("服务 " + serviceId + " 的实例 " + properties.getId() + " 地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(properties.getUri().trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "服务 " + serviceId + " 的实例 " + properties.getId() + " 地址不是合法 URI", exception);
        }
        if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException("服务 " + serviceId + " 的实例 " + properties.getId() + " 地址必须包含协议和主机");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("服务 " + serviceId + " 的实例 " + properties.getId() + " 只支持 HTTP/HTTPS");
        }
        Map<String, String> metadata = properties.getMetadata();
        if (metadata.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalStateException("服务 " + serviceId + " 的实例 " + properties.getId() + " 元数据不能包含空键或空值");
        }
        return new GatewayInstance(
                properties.getId(),
                uri,
                properties.isEnabled(),
                properties.isDraining(),
                Map.copyOf(metadata));
    }

    /** YAML 文件中 {@code sca4dj.gateway} 节点的绑定对象。 */
    public static class GatewayUpstreamFileProperties {

        private Map<String, GatewayUpstreamProperties> upstreams = new LinkedHashMap<>();

        public Map<String, GatewayUpstreamProperties> getUpstreams() {
            return upstreams;
        }

        public void setUpstreams(Map<String, GatewayUpstreamProperties> upstreams) {
            this.upstreams = upstreams == null ? new LinkedHashMap<>() : upstreams;
        }
    }

    /** 单个 upstream 服务的 YAML 绑定对象。 */
    public static class GatewayUpstreamProperties {

        private String healthPath = "/actuator/health/readiness";

        private Duration connectTimeout = Duration.ofSeconds(2);

        private Duration responseTimeout = Duration.ofSeconds(10);

        private int healthyThreshold = 2;

        private int unhealthyThreshold = 3;

        private List<GatewayInstanceProperties> instances = new ArrayList<>();

        public String getHealthPath() {
            return healthPath;
        }

        public void setHealthPath(String healthPath) {
            this.healthPath = healthPath;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getResponseTimeout() {
            return responseTimeout;
        }

        public void setResponseTimeout(Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
        }

        public int getHealthyThreshold() {
            return healthyThreshold;
        }

        public void setHealthyThreshold(int healthyThreshold) {
            this.healthyThreshold = healthyThreshold;
        }

        public int getUnhealthyThreshold() {
            return unhealthyThreshold;
        }

        public void setUnhealthyThreshold(int unhealthyThreshold) {
            this.unhealthyThreshold = unhealthyThreshold;
        }

        public List<GatewayInstanceProperties> getInstances() {
            return instances;
        }

        public void setInstances(List<GatewayInstanceProperties> instances) {
            this.instances = instances == null ? new ArrayList<>() : instances;
        }
    }

    /** 单个静态服务实例的 YAML 绑定对象。 */
    public static class GatewayInstanceProperties {

        private String id;

        private String uri;

        private boolean enabled = true;

        private boolean draining;

        private Map<String, String> metadata = new LinkedHashMap<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDraining() {
            return draining;
        }

        public void setDraining(boolean draining) {
            this.draining = draining;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
        }
    }
}
