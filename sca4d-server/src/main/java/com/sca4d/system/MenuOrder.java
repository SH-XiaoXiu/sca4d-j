package com.sca4d.system;

import org.jetbrains.annotations.NotNull;

/** 一次菜单同级排序中的节点版本和最终位置。 */
public final class MenuOrder {

    private final Long menuId;
    private final long version;
    private final int sortOrder;

    /**
     * 创建一个已完成兄弟集合校验的菜单位置。
     *
     * @param menuId 菜单主键
     * @param version 拖拽开始时读取的乐观锁版本
     * @param sortOrder 服务端转换后的内部排序值
     */
    public MenuOrder(@NotNull Long menuId, long version, int sortOrder) {
        this.menuId = menuId;
        this.version = version;
        this.sortOrder = sortOrder;
    }

    /** @return 菜单主键 */
    public @NotNull Long getMenuId() {
        return menuId;
    }

    /** @return 拖拽开始时读取的乐观锁版本 */
    public long getVersion() {
        return version;
    }

    /** @return 服务端转换后的内部排序值 */
    public int getSortOrder() {
        return sortOrder;
    }
}
