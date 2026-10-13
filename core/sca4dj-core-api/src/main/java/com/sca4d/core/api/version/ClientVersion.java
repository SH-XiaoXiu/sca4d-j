package com.sca4d.core.api.version;

import org.jetbrains.annotations.Nullable;

/** 客户端声明的构建身份；只用于兼容选择和排障，不提供认证或授权。 */
public final class ClientVersion {
    private final Integer contract;

    private final String build;

    private final String name;

    private final String releaseId;

    private final String releaseVersion;

    /** 创建已解析的客户端身份，未声明的字段保留为空。 */
    public ClientVersion(
            @Nullable String name,
            @Nullable String releaseVersion,
            @Nullable String releaseId,
            @Nullable String build,
            @Nullable Integer contract) {
        this.name = name;
        this.releaseVersion = releaseVersion;
        this.releaseId = releaseId;
        this.build = build;
        this.contract = contract;
        if (contract != null && contract <= 0)
            throw new IllegalArgumentException("客户端契约版本必须为正数");
    }

    /** @return 客户端名称 */
    public @Nullable String name() {
        return name;
    }

    /** @return 客户端产品版本 */
    public @Nullable String releaseVersion() {
        return releaseVersion;
    }

    /** @return 发布批次 */
    public @Nullable String releaseId() {
        return releaseId;
    }

    /** @return 客户端构建标识 */
    public @Nullable String build() {
        return build;
    }

    /** @return 显式请求的契约版本；未提供时为空 */
    public @Nullable Integer contract() {
        return contract;
    }

    /** @return 是否完全没有版本声明 */
    public boolean unspecified() {
        return name == null && releaseVersion == null && releaseId == null && build == null && contract == null;
    }
}
