package com.sca4d.core.api.version;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/** 随构件保存的不可变版本事实；所有运行时消费方共享同一对象。 */
public final class ApplicationVersion {
    private final String buildMode;

    private final String buildVersion;

    private final String builtAt;

    private final String component;

    private final String componentBuildId;

    private final String product;

    private final String releaseId;

    private final String releaseVersion;

    private final String sourceRevision;

    private final int apiContractVersion;

    /** @param properties 构建生成的字段，缺失时停止启动而不伪造版本 */
    public ApplicationVersion(@NotNull Map<String, String> properties) {
        this.product = Objects.requireNonNull(properties.get("product"), "构建清单缺少 product");
        this.releaseVersion = Objects.requireNonNull(properties.get("releaseVersion"), "构建清单缺少 releaseVersion");
        this.releaseId = Objects.requireNonNull(properties.get("releaseId"), "构建清单缺少 releaseId");
        this.component = Objects.requireNonNull(properties.get("component"), "构建清单缺少 component");
        this.componentBuildId = Objects.requireNonNull(properties.get("componentBuildId"), "构建清单缺少 componentBuildId");
        this.sourceRevision = Objects.requireNonNull(properties.get("sourceRevision"), "构建清单缺少 sourceRevision");
        this.builtAt = Objects.requireNonNull(properties.get("builtAt"), "构建清单缺少 builtAt");
        this.buildMode = Objects.requireNonNull(properties.get("buildMode"), "构建清单缺少 buildMode");
        this.buildVersion = Objects.requireNonNull(properties.get("buildVersion"), "构建清单缺少 buildVersion");
        this.apiContractVersion = Integer.parseInt(Objects.requireNonNull(properties.get("apiContractVersion")));
        if (apiContractVersion <= 0)
            throw new IllegalArgumentException("契约版本必须为正数");
    }

    /** @return 构建字段 product */
    @JsonProperty
    public String product() {
        return product;
    }

    /** @return 构建字段 releaseVersion */
    @JsonProperty
    public String releaseVersion() {
        return releaseVersion;
    }

    /** @return 构建字段 releaseId */
    @JsonProperty
    public String releaseId() {
        return releaseId;
    }

    /** @return 构建字段 component */
    @JsonProperty
    public String component() {
        return component;
    }

    /** @return 构建字段 componentBuildId */
    @JsonProperty
    public String componentBuildId() {
        return componentBuildId;
    }

    /** @return 构建字段 sourceRevision */
    @JsonProperty
    public String sourceRevision() {
        return sourceRevision;
    }

    /** @return 构建字段 builtAt */
    @JsonProperty
    public String builtAt() {
        return builtAt;
    }

    /** @return 构建字段 buildMode */
    @JsonProperty
    public String buildMode() {
        return buildMode;
    }

    /** @return 构建字段 buildVersion */
    @JsonProperty
    public String buildVersion() {
        return buildVersion;
    }

    /** @return 构建字段 apiContractVersion */
    @JsonProperty
    public int apiContractVersion() {
        return apiContractVersion;
    }
}
