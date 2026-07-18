package com.sca4d.system.dictionary;

import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.system.DictionaryStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Map;

/** 管理端编辑、排序和乐观锁恢复使用的完整字典项结果。 */
public class ManagedDictionaryItem {

    private final Long id;
    private final Long dictionaryId;
    private final String value;
    private final DictionarySource source;
    private final String labelKey;
    private final Map<String, String> labels;
    private final String label;
    private final DictionaryTone tone;
    private final boolean selectable;
    private final DictionaryStatus status;
    private final int sortOrder;
    private final long version;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    /**
     * 创建完整字典项管理结果。
     *
     * @param id 字典项主键
     * @param dictionaryId 所属字典类型主键
     * @param value 业务固定值或输入建议文本
     * @param source 字典项固定值的维护来源
     * @param labelKey 代码枚举消息键；可配置字典为空
     * @param labels 用户维护的原始多语言标签
     * @param label 当前请求语言标签
     * @param tone 有限含义色调
     * @param selectable 是否允许用于新输入
     * @param status 启用状态
     * @param sortOrder 可自由调整的展示排序
     * @param version 当前乐观锁版本
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public ManagedDictionaryItem(
            @NotNull Long id,
            @NotNull Long dictionaryId,
            @NotNull String value,
            @NotNull DictionarySource source,
            @Nullable String labelKey,
            @NotNull Map<String, String> labels,
            @NotNull String label,
            @NotNull DictionaryTone tone,
            boolean selectable,
            @NotNull DictionaryStatus status,
            int sortOrder,
            long version,
            @NotNull OffsetDateTime createdAt,
            @NotNull OffsetDateTime updatedAt) {
        this.id = id;
        this.dictionaryId = dictionaryId;
        this.value = value;
        this.source = source;
        this.labelKey = labelKey;
        this.labels = Map.copyOf(labels);
        this.label = label;
        this.tone = tone;
        this.selectable = selectable;
        this.status = status;
        this.sortOrder = sortOrder;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public @NotNull Long getId() {
        return id;
    }

    public @NotNull Long getDictionaryId() {
        return dictionaryId;
    }

    public @NotNull String getValue() {
        return value;
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

    public @NotNull DictionaryTone getTone() {
        return tone;
    }

    public boolean isSelectable() {
        return selectable;
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
