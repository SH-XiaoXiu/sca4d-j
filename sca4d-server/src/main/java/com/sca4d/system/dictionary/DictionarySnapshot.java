package com.sca4d.system.dictionary;

import com.sca4d.system.DictionaryKind;
import com.sca4d.system.DictionaryLabels;
import com.sca4d.system.DictionaryStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Repository 从字典类型及其全部字典项恢复的固定读取快照。 */
public class DictionarySnapshot {

    private final String code;
    private final DictionaryKind kind;
    private final DictionaryStatus status;
    private final String nameKey;
    private final DictionaryLabels labels;
    private final List<DictionaryItemSnapshot> items;

    /**
     * 创建字典读取快照。
     *
     * @param code 当前可见范围内唯一的字典类型编码
     * @param kind 字典业务含义类型
     * @param status 字典类型启用状态；停用类型仍返回历史选项，但全部选项不可用于新输入
     * @param nameKey 代码枚举名称键；可配置字典为空
     * @param labels 可配置字典的用户维护名称
     * @param items 按数据库展示顺序排列的全部未删除字典项
     */
    public DictionarySnapshot(
            @NotNull String code,
            @NotNull DictionaryKind kind,
            @NotNull DictionaryStatus status,
            @Nullable String nameKey,
            @NotNull DictionaryLabels labels,
            @NotNull List<DictionaryItemSnapshot> items) {
        this.code = code;
        this.kind = kind;
        this.status = status;
        this.nameKey = nameKey;
        this.labels = labels;
        this.items = List.copyOf(items);
    }

    /** @return 当前可见范围内唯一的字典类型编码 */
    public @NotNull String getCode() {
        return code;
    }

    /** @return 字典业务含义类型 */
    public @NotNull DictionaryKind getKind() {
        return kind;
    }

    /** @return 字典类型是否允许用于新输入 */
    public @NotNull DictionaryStatus getStatus() {
        return status;
    }

    /** @return 代码枚举名称键；可配置字典为空 */
    public @Nullable String getNameKey() {
        return nameKey;
    }

    /** @return 可配置字典的用户维护名称 */
    public @NotNull DictionaryLabels getLabels() {
        return labels;
    }

    /** @return 按数据库展示顺序排列的全部未删除字典项 */
    public @NotNull List<DictionaryItemSnapshot> getItems() {
        return items;
    }
}
