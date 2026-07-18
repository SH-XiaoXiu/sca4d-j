package com.sca4d.system.dictionary;

import com.sca4d.system.DictionaryKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 一个可由管理端共用选择组件消费的本地化字典。 */
public class DictionarySelection {

    private final String code;
    private final DictionaryKind kind;
    private final String labelKey;
    private final String label;
    private final List<DictionaryOption> options;

    /**
     * 创建字典选择结果。
     *
     * @param code 固定字典类型编码
     * @param kind 字典业务含义类型
     * @param labelKey 代码枚举名称键；用户维护内容为空
     * @param label 当前请求语言的字典名称
     * @param options 按数据库展示顺序排列的共用选项
     */
    public DictionarySelection(
            @NotNull String code,
            @NotNull DictionaryKind kind,
            @Nullable String labelKey,
            @NotNull String label,
            @NotNull List<DictionaryOption> options) {
        this.code = code;
        this.kind = kind;
        this.labelKey = labelKey;
        this.label = label;
        this.options = List.copyOf(options);
    }

    /** @return 固定字典类型编码 */
    public @NotNull String getCode() {
        return code;
    }

    /** @return 字典业务含义类型 */
    public @NotNull DictionaryKind getKind() {
        return kind;
    }

    /** @return 代码枚举名称键；用户维护内容为空 */
    public @Nullable String getLabelKey() {
        return labelKey;
    }

    /** @return 当前请求语言的字典名称 */
    public @NotNull String getLabel() {
        return label;
    }

    /** @return 按数据库展示顺序排列的共用选项 */
    public @NotNull List<DictionaryOption> getOptions() {
        return options;
    }
}
