package com.sca4d.system.dictionary;

import com.sca4d.common.dictionary.DictionaryTone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 前后端共用选择项语言中的一个字典选项。 */
public class DictionaryOption {

    private final String value;
    private final String labelKey;
    private final String label;
    private final DictionaryTone tone;
    private final boolean disabled;

    /**
     * 创建已经按当前请求语言解析的字典选项。
     *
     * @param value 业务判断、接口和持久化共用的固定值
     * @param labelKey 代码枚举的固定消息键；用户维护内容为空
     * @param label 当前请求语言的展示标签
     * @param tone 有限含义色调
     * @param disabled 是否禁止用于新的选择
     */
    public DictionaryOption(
            @NotNull String value,
            @Nullable String labelKey,
            @NotNull String label,
            @NotNull DictionaryTone tone,
            boolean disabled) {
        this.value = value;
        this.labelKey = labelKey;
        this.label = label;
        this.tone = tone;
        this.disabled = disabled;
    }

    /** @return 业务判断、接口和持久化共用的固定值 */
    public @NotNull String getValue() {
        return value;
    }

    /** @return 代码枚举的固定消息键；用户维护内容为空 */
    public @Nullable String getLabelKey() {
        return labelKey;
    }

    /** @return 当前请求语言的展示标签 */
    public @NotNull String getLabel() {
        return label;
    }

    /** @return 有限含义色调 */
    public @NotNull DictionaryTone getTone() {
        return tone;
    }

    /** @return 是否禁止用于新的选择 */
    public boolean isDisabled() {
        return disabled;
    }
}
