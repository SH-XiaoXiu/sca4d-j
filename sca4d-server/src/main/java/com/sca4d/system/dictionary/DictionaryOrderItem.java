package com.sca4d.system.dictionary;

import org.jetbrains.annotations.NotNull;

/** 字典类型或字典项进入完整顺序编辑器所需的轻量结果。 */
public final class DictionaryOrderItem {

    private final Long id;
    private final String label;
    private final String description;
    private final long version;

    /**
     * 创建一个已按当前展示顺序排列的字典排序条目。
     *
     * @param id 字典类型或字典项主键
     * @param label 当前请求语言下的展示名称
     * @param description 固定字典编码或字典项值
     * @param version 生成排序草稿时的乐观锁版本
     */
    public DictionaryOrderItem(
            @NotNull Long id,
            @NotNull String label,
            @NotNull String description,
            long version) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.version = version;
    }

    /** @return 字典类型或字典项主键 */
    public @NotNull Long getId() {
        return id;
    }

    /** @return 当前请求语言下的展示名称 */
    public @NotNull String getLabel() {
        return label;
    }

    /** @return 固定字典编码或字典项值 */
    public @NotNull String getDescription() {
        return description;
    }

    /** @return 生成排序草稿时的乐观锁版本 */
    public long getVersion() {
        return version;
    }
}
