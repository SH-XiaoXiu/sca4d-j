package com.sca4d.plugin.spring;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** 配置一个 App 的插件交付位置和旧代次停止等待时间。 */
@ConfigurationProperties("sca4dj.plugin")
public final class PluginProperties {
    /** 与生成描述一致的 App API 编码。 */
    private String api;

    /** 不可变交付副本的本地缓存位置。 */
    private String cacheDirectory = "build/plugin-cache";

    /** 启动时必须加载的插件 JAR 路径。 */
    private @NotNull List<String> artifacts = List.of();

    /** 关闭或替换时等待正在执行操作的最大时间。 */
    private Duration drainTimeout = Duration.ofSeconds(30);

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public String getCacheDirectory() {
        return cacheDirectory;
    }

    public void setCacheDirectory(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public @NotNull @Unmodifiable List<String> getArtifacts() {
        return artifacts;
    }

    /**
     * 复制传入的构件列表并替换当前配置；列表中包含 null 元素时抛出 NullPointerException，原列表保持不变。
     *
     * @param artifacts 构件标识列表，之后对原列表的修改不影响本配置
     */
    public void setArtifacts(@NotNull List<String> artifacts) {
        this.artifacts = List.copyOf(artifacts);
    }

    public Duration getDrainTimeout() {
        return drainTimeout;
    }

    public void setDrainTimeout(Duration drainTimeout) {
        this.drainTimeout = drainTimeout;
    }
}
