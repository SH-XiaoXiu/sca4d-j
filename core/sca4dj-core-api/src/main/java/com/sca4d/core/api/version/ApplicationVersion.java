package com.sca4d.core.api.version;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/** 从应用配置创建的不可变版本快照；不依赖配置文件格式或构建工具。 */
public final class ApplicationVersion {
    private final @NotNull String buildMode;

    private final @NotNull String buildVersion;

    private final @NotNull String builtAt;

    private final @NotNull String component;

    private final @NotNull String componentBuildId;

    private final @NotNull String product;

    private final @NotNull String releaseId;

    private final @NotNull String releaseVersion;

    private final @NotNull String sourceRevision;

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

    /** @return 产品标识 */
    @JsonProperty
    public @NotNull String product() {
        return product;
    }

    /** @return 产品发布版本 */
    @JsonProperty
    public @NotNull String releaseVersion() {
        return releaseVersion;
    }

    /** @return 发布批次标识 */
    @JsonProperty
    public @NotNull String releaseId() {
        return releaseId;
    }

    /** @return 当前应用组件标识 */
    @JsonProperty
    public @NotNull String component() {
        return component;
    }

    /** @return 组件构建标识 */
    @JsonProperty
    public @NotNull String componentBuildId() {
        return componentBuildId;
    }

    /** @return 构建使用的源码提交；无法确定时为配置声明的未知值 */
    @JsonProperty
    public @NotNull String sourceRevision() {
        return sourceRevision;
    }

    /** @return 构建时间；无法确定时为配置声明的未知值 */
    @JsonProperty
    public @NotNull String builtAt() {
        return builtAt;
    }

    /** @return 构建模式 */
    @JsonProperty
    public @NotNull String buildMode() {
        return buildMode;
    }

    /** @return 用于显示的构建版本 */
    @JsonProperty
    public @NotNull String buildVersion() {
        return buildVersion;
    }

    /** @return 当前组件的接口契约版本 */
    @JsonProperty
    public int apiContractVersion() {
        return apiContractVersion;
    }
}
