package com.sca4d.system.menu;

import com.sca4d.system.MenuIconName;
import com.sca4d.system.MenuTitles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 系统菜单仓储返回的单节点字段，不包含父子树关系。 */
public final class MenuSnapshot {

    private final Long menuId;
    private final Long parentId;
    private final String routeCode;
    private final String permissionCode;
    private final MenuTitles titles;
    private final MenuIconName icon;
    private final int sortOrder;
    private final boolean visible;
    private final long version;

    /**
     * 创建不包含树派生关系的菜单数据快照。
     *
     * @param menuId 菜单固定主键
     * @param parentId 父菜单主键；一级菜单为空
     * @param routeCode 代码路由编码；结构分组为空
     * @param permissionCode 路由绑定权限；结构分组或无权限入口为空
     * @param titles 完整双语标题
     * @param icon 图标含义
     * @param sortOrder 同级展示排序
     * @param visible 是否进入当前用户导航
     * @param version 乐观锁版本
     */
    public MenuSnapshot(
            @NotNull Long menuId,
            @Nullable Long parentId,
            @Nullable String routeCode,
            @Nullable String permissionCode,
            @NotNull MenuTitles titles,
            @NotNull MenuIconName icon,
            int sortOrder,
            boolean visible,
            long version) {
        this.menuId = menuId;
        this.parentId = parentId;
        this.routeCode = routeCode;
        this.permissionCode = permissionCode;
        this.titles = titles;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.visible = visible;
        this.version = version;
    }

    /** @return 菜单固定主键 */
    public @NotNull Long getMenuId() {
        return menuId;
    }

    /** @return 父菜单主键；一级菜单返回 null */
    public @Nullable Long getParentId() {
        return parentId;
    }

    /** @return 代码路由编码；结构分组返回 null */
    public @Nullable String getRouteCode() {
        return routeCode;
    }

    /** @return 路由绑定权限；结构分组或无权限入口返回 null */
    public @Nullable String getPermissionCode() {
        return permissionCode;
    }

    /** @return 完整双语标题 */
    public @NotNull MenuTitles getTitles() {
        return titles;
    }

    /** @return 图标含义 */
    public @NotNull MenuIconName getIcon() {
        return icon;
    }

    /** @return 同级展示排序 */
    public int getSortOrder() {
        return sortOrder;
    }

    /** @return 是否进入当前用户导航 */
    public boolean isVisible() {
        return visible;
    }

    /** @return 当前乐观锁版本 */
    public long getVersion() {
        return version;
    }
}
