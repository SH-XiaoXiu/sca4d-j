package com.sca4d.core.api.version;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/** 从应用配置创建的不可变版本快照；不依赖配置文件格式或构建工具。 */
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

    /**
     * 创建已经解析配置变量的版本快照，版本规则不交给构建系统执行。
     * @param product 产品标识
     * @param component 当前应用组件
     * @param releaseVersion 产品发布版本
     * @param apiContractVersion 接口契约版本
     * @param releaseId 发布批次
     * @param componentBuildId 组件构建标识
     * @param sourceRevision 源码提交或明确的未知值
     * @param builtAt 构建时间或明确的未知值
     * @param buildMode 配置声明的构建模式
     * @param buildVersion 配置声明的构建展示值
     */
    public ApplicationVersion(
            @NotNull String product,
            @NotNull String component,
            @NotNull String releaseVersion,
            int apiContractVersion,
            @NotNull String releaseId,
            @NotNull String componentBuildId,
            @NotNull String sourceRevision,
            @NotNull String builtAt,
            @NotNull String buildMode,
            @NotNull String buildVersion) {
        for (String value : new String[] {product, component, releaseVersion, releaseId, componentBuildId,
                sourceRevision, builtAt, buildMode, buildVersion}) {
            if (value == null || value.isBlank() || value.contains("${"))
                throw new IllegalArgumentException("版本配置不能为空或包含未解析变量");
        }
        for (String headerValue : new String[] {component, releaseVersion, releaseId, componentBuildId}) {
            if (!headerValue.matches("[A-Za-z0-9._-]{1,240}"))
                throw new IllegalArgumentException("版本配置不能作为 HTTP Header 值使用");
        }
        CompatibilityRule.compareVersions(releaseVersion, releaseVersion);
        if (apiContractVersion <= 0)
            throw new IllegalArgumentException("契约版本必须为正数");
        this.product = product;
        this.component = component;
        this.releaseVersion = releaseVersion;
        this.apiContractVersion = apiContractVersion;
        this.releaseId = releaseId;
        this.componentBuildId = componentBuildId;
        this.sourceRevision = sourceRevision;
        this.builtAt = builtAt;
        this.buildMode = buildMode;
        this.buildVersion = buildVersion;
    }

    /** @return 配置字段 product */
    @JsonProperty
    public String product() {
        return product;
    }

    /** @return 配置字段 releaseVersion */
    @JsonProperty
    public String releaseVersion() {
        return releaseVersion;
    }

    /** @return 配置字段 releaseId */
    @JsonProperty
    public String releaseId() {
        return releaseId;
    }

    /** @return 配置字段 component */
    @JsonProperty
    public String component() {
        return component;
    }

    /** @return 配置字段 componentBuildId */
    @JsonProperty
    public String componentBuildId() {
        return componentBuildId;
    }

    /** @return 配置字段 sourceRevision */
    @JsonProperty
    public String sourceRevision() {
        return sourceRevision;
    }

    /** @return 配置字段 builtAt */
    @JsonProperty
    public String builtAt() {
        return builtAt;
    }

    /** @return 配置字段 buildMode */
    @JsonProperty
    public String buildMode() {
        return buildMode;
    }

    /** @return 配置字段 buildVersion */
    @JsonProperty
    public String buildVersion() {
        return buildVersion;
    }

    /** @return 配置字段 apiContractVersion */
    @JsonProperty
    public int apiContractVersion() {
        return apiContractVersion;
    }
}
