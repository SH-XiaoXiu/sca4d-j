package com.sca4d.system.dictionary;

import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.system.DictionaryKind;
import com.sca4d.system.DictionaryStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Map;

/** 管理端编辑、排序和乐观锁恢复使用的完整字典类型结果。 */
public class ManagedDictionary {

    private final Long id;
    private final Long tenantId;
    private final String code;
    private final DictionaryKind kind;
    private final DictionarySource source;
    private final String labelKey;
    private final Map<String, String> labels;
    private final String label;
    private final DictionaryStatus status;
    private final int sortOrder;
    private final long version;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    /**
     * 创建完整字典类型管理结果。
     *
     * @param id 字典类型主键
     * @param tenantId 所属租户；空表示平台全局
     * @param code 不可复用的固定类型编码
     * @param kind 字典业务含义类型
     * @param source 字典类型由代码同步或由管理端维护
     * @param labelKey 代码枚举名称键；可配置字典为空
     * @param labels 用户维护的原始多语言名称
     * @param label 当前请求语言名称
     * @param status 启用状态
     * @param sortOrder 可自由调整的展示排序
     * @param version 当前乐观锁版本
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public ManagedDictionary(
            @NotNull Long id,
            @Nullable Long tenantId,
            @NotNull String code,
            @NotNull DictionaryKind kind,
            @NotNull DictionarySource source,
            @Nullable String labelKey,
            @NotNull Map<String, String> labels,
            @NotNull String label,
            @NotNull DictionaryStatus status,
            int sortOrder,
            long version,
            @NotNull OffsetDateTime createdAt,
            @NotNull OffsetDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.kind = kind;
        this.source = source;
        this.labelKey = labelKey;
        this.labels = Map.copyOf(labels);
        this.label = label;
        this.status = status;
        this.sortOrder = sortOrder;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public @NotNull Long getId() {
        return id;
    }

    public @Nullable Long getTenantId() {
        return tenantId;
    }

    public @NotNull String getCode() {
        return code;
    }

    public @NotNull DictionaryKind getKind() {
        return kind;
    }

    public @NotNull DictionarySource getSource() {
        return source;
    }

    public @Nullable String getLabelKey() {
        return labelKey;
    }

    public @NotNull Map<String, String> getLabels() {
        return labels;
    }

    public @NotNull String getLabel() {
        return label;
    }

    public @NotNull DictionaryStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public long getVersion() {
        return version;
    }

    public @NotNull OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public @NotNull OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
