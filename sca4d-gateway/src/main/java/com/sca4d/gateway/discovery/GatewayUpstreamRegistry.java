package com.sca4d.gateway.discovery;

import com.sca4d.gateway.config.Sca4djGatewayProperties;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * upstream 实例注册表。
 */
@Component
public class GatewayUpstreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(GatewayUpstreamRegistry.class);

    private final Sca4djGatewayProperties properties;

    private final GatewayUpstreamFileLoader loader;

    private final GatewayUpstreamHealthState healthState;

    private final AtomicLong version = new AtomicLong();

    private final AtomicReference<GatewayUpstreamSnapshot> snapshot = new AtomicReference<>(GatewayUpstreamSnapshot.empty());

    private volatile FileTime lastModifiedTime;

    /**
     * 创建支持文件热更新和健康实例筛选的 upstream 注册表。
     *
     * @param properties 网关外部化配置
     * @param loader upstream 文件加载器
     * @param healthState 实例主动健康检查状态
     */
    public GatewayUpstreamRegistry(
            @NotNull Sca4djGatewayProperties properties,
            @NotNull GatewayUpstreamFileLoader loader,
            @NotNull GatewayUpstreamHealthState healthState) {
        this.properties = properties;
        this.loader = loader;
        this.healthState = healthState;
    }

    /**
     * 启动时加载 upstream 文件。
     */
    @PostConstruct
    public void initialize() {
        reload(true);
    }

    /**
     * 周期性检测 upstream 文件变化。
     */
    @Scheduled(fixedDelayString = "#{@sca4djGatewayProperties.reloadIntervalMillis}")
    public void reloadIfChanged() {
        reload(false);
    }

    /**
     * 获取当前 upstream 快照。
     *
     * @return 当前生效的 upstream 配置快照
     */
    public @NotNull GatewayUpstreamSnapshot snapshot() {
        return snapshot.get();
    }

    /**
     * 获取当前服务 ID 集合。
     *
     * @return 当前配置中全部服务的标识集合
     */
    public @NotNull Set<String> serviceIds() {
        return snapshot.get().getUpstreams().keySet();
    }

    /**
     * 获取可承接新请求的健康实例。
     *
     * @param serviceId 需要发现实例的服务标识
     * @return 可参与负载均衡的实例列表
     */
    public @NotNull List<GatewayInstance> availableInstances(@NotNull String serviceId) {
        GatewayUpstream upstream = snapshot.get().getUpstreams().get(serviceId);
        if (upstream == null) {
            return List.of();
        }
        return upstream
                .getInstances()
                .stream()
                .filter(GatewayInstance::routable)
                .filter(instance -> healthState.healthy(upstream.healthKey(instance)))
                .toList();
    }

    /**
     * 在启动或文件发生变化时原子替换 upstream 快照。
     *
     * <p>非首次加载失败时保留上一版快照；成功切换后同时裁剪已经移除实例的健康状态。
     *
     * @param required 是否属于必须成功的首次加载
     */
    private void reload(boolean required) {
        try {
            Path upstreamsFile = Path.of(properties.getUpstreamsFile());
            FileTime currentModifiedTime = Files.getLastModifiedTime(upstreamsFile);
            if (!required && currentModifiedTime.equals(lastModifiedTime)) {
                log.trace("网关上游配置没有变化 | file={} version={}",
                        upstreamsFile.toAbsolutePath(), snapshot.get().getVersion());
                return;
            }
            GatewayUpstreamSnapshot loaded = loader.load(upstreamsFile, version.incrementAndGet());
            snapshot.set(loaded);
            healthState.retain(loaded.getUpstreams().values().stream()
                    .flatMap(upstream -> upstream.getInstances().stream().map(upstream::healthKey))
                    .collect(Collectors.toUnmodifiableSet()));
            lastModifiedTime = currentModifiedTime;
            log.info("网关上游配置已加载 | serviceCount={} version={} file={}",
                    loaded.getUpstreams().size(),
                    loaded.getVersion(),
                    upstreamsFile.toAbsolutePath());
        } catch (RuntimeException | IOException ex) {
            if (required) {
                throw new IllegalStateException("网关上游配置初始加载失败", ex);
            }
            log.error("网关上游配置重新加载失败，继续使用上一版 | file={}",
                    properties.getUpstreamsFile(), ex);
        }
    }

}
