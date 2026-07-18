package com.sca4d.system.view;

import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.system.dictionary.DictionaryOption;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 管理端共用选择组件消费的字典选项视图。 */
@Schema(description = "统一字典选项")
public class DictionaryOptionView {

    private final String value;
    private final String labelKey;
    private final String label;
    private final DictionaryTone tone;
    private final boolean disabled;

    /**
     * 创建共用字典选项视图。
     *
     * @param value 业务固定值
     * @param labelKey 代码枚举消息键；用户内容为空
     * @param label 当前请求语言标签
     * @param tone 有限含义色调
     * @param disabled 是否禁止用于新选择
     */
    public DictionaryOptionView(
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

    /** @return 业务固定值 */
    public @NotNull String getValue() {
        return value;
    }

    /** @return 代码枚举消息键；用户内容为空 */
    public @Nullable String getLabelKey() {
        return labelKey;
    }

    /** @return 当前请求语言标签 */
    public @NotNull String getLabel() {
        return label;
    }

    /** @return 有限含义色调 */
    public @NotNull DictionaryTone getTone() {
        return tone;
    }

    /** @return 是否禁止用于新选择 */
    public boolean isDisabled() {
        return disabled;
    }

    /**
     * 从 DictionaryOption 构造字典选项响应。
     *
     * @param option 已按当前语言生成标签的字典选项
     * @return HTTP 字典选项视图
     */
    public static @NotNull DictionaryOptionView from(@NotNull DictionaryOption option) {
        return new DictionaryOptionView(
                option.getValue(), option.getLabelKey(), option.getLabel(), option.getTone(), option.isDisabled());
    }
}
