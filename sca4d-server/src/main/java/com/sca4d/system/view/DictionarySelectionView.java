package com.sca4d.system.view;

import com.sca4d.system.DictionaryKind;
import com.sca4d.system.dictionary.DictionarySelection;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 管理端共用选择组件消费的一个完整字典视图。 */
@Schema(description = "本地化字典及统一选项")
public class DictionarySelectionView {

    private final String code;
    private final DictionaryKind kind;
    private final String labelKey;
    private final String label;
    private final List<DictionaryOptionView> options;

    /**
     * 创建字典选择视图。
     *
     * @param code 固定字典类型编码
     * @param kind 字典业务含义类型
     * @param labelKey 代码枚举名称键；用户内容为空
     * @param label 当前请求语言名称
     * @param options 按数据库展示顺序排列的选项
     */
    public DictionarySelectionView(
            @NotNull String code,
            @NotNull DictionaryKind kind,
            @Nullable String labelKey,
            @NotNull String label,
            @NotNull List<DictionaryOptionView> options) {
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

    /** @return 代码枚举名称键；用户内容为空 */
    public @Nullable String getLabelKey() {
        return labelKey;
    }

    /** @return 当前请求语言名称 */
    public @NotNull String getLabel() {
        return label;
    }

    /** @return 按数据库展示顺序排列的选项 */
    public @NotNull List<DictionaryOptionView> getOptions() {
        return options;
    }

    /**
     * 从业务模块字典选择结果构造 HTTP 视图。
     *
     * @param selection 已按当前语言生成名称和选项的字典结果
     * @return HTTP 字典选择视图
     */
    public static @NotNull DictionarySelectionView from(@NotNull DictionarySelection selection) {
        return new DictionarySelectionView(
                selection.getCode(),
                selection.getKind(),
                selection.getLabelKey(),
                selection.getLabel(),
                selection.getOptions().stream().map(DictionaryOptionView::from).toList());
    }
}
