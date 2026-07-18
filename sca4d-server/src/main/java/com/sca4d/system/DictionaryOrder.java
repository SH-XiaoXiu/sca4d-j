package com.sca4d.system;

import org.jetbrains.annotations.NotNull;

/** 字典类型或字典项在一次完整排序写入中的持久化位置。 */
public final class DictionaryOrder {

    private final Long id;
    private final long version;
    private final int sortOrder;

    /**
     * 创建一个已完成集合与版本校验的排序位置。
     *
     * @param id 字典类型或字典项主键
     * @param version 拖拽开始时读取的乐观锁版本
     * @param sortOrder 服务端转换后的内部排序值
     */
    public DictionaryOrder(@NotNull Long id, long version, int sortOrder) {
        this.id = id;
        this.version = version;
        this.sortOrder = sortOrder;
    }

    /**
     * 返回待排序资源主键。
     *
     * @return 字典类型或字典项主键
     */
    public @NotNull Long getId() {
        return id;
    }

    /**
     * 返回排序草稿读取时的资源版本。
     *
     * @return 乐观锁版本
     */
    public long getVersion() {
        return version;
    }

    /**
     * 返回服务端生成的内部排序值。
     *
     * @return 固定步长的持久化排序值
     */
    public int getSortOrder() {
        return sortOrder;
    }
}
