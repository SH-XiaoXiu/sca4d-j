package com.sca4d.system.dictionary;

import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.system.DictionaryLabels;
import com.sca4d.system.DictionaryStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Repository 从一个字典项持久化数据恢复的固定读取快照。 */
public class DictionaryItemSnapshot {

    private final String value;
    private final String labelKey;
    private final DictionaryLabels labels;
    private final DictionaryTone tone;
    private final boolean selectable;
    private final DictionaryStatus status;

    /**
     * 创建字典项读取快照。
     *
     * @param value 业务固定值或输入建议文本
     * @param labelKey 代码枚举标签键；可配置字典为空
     * @param labels 可配置字典的用户维护标签
     * @param tone 有限含义色调
     * @param selectable 是否允许用于新输入
     * @param status 字典项是否允许用于新输入
     */
    public DictionaryItemSnapshot(
            @NotNull String value,
            @Nullable String labelKey,
            @NotNull DictionaryLabels labels,
            @NotNull DictionaryTone tone,
            boolean selectable,
            @NotNull DictionaryStatus status) {
        this.value = value;
        this.labelKey = labelKey;
        this.labels = labels;
        this.tone = tone;
        this.selectable = selectable;
        this.status = status;
    }

    /** @return 业务固定值或输入建议文本 */
    public @NotNull String getValue() {
        return value;
    }

    /** @return 代码枚举标签键；可配置字典为空 */
    public @Nullable String getLabelKey() {
        return labelKey;
    }

    /** @return 可配置字典的用户维护标签 */
    public @NotNull DictionaryLabels getLabels() {
        return labels;
    }

    /** @return 有限含义色调 */
    public @NotNull DictionaryTone getTone() {
        return tone;
    }

    /** @return 是否允许用于新输入 */
    public boolean isSelectable() {
        return selectable;
    }

    /** @return 字典项是否允许用于新输入 */
    public @NotNull DictionaryStatus getStatus() {
        return status;
    }
}
