package com.sca4d.system.menu;

import com.sca4d.system.MenuIconName;
import com.sca4d.system.MenuTitles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 平台管理端读取和编辑的一个菜单节点。 */
public final class ManagedMenu {

    private final Long menuId;
    private final Long parentId;
    private final String routeCode;
    private final String pathTemplate;
    private final String permissionCode;
    private final MenuTitles titles;
    private final MenuIconName icon;
    private final int sortOrder;
    private final boolean visible;
    private final long version;
    private final List<ManagedMenu> children;

    /**
     * 创建一个已经完成路由和树结构校验的管理菜单节点。
     *
     * @param menuId 菜单固定主键
     * @param parentId 父菜单主键；一级菜单为空
     * @param routeCode 代码路由编码；结构分组为空
     * @param pathTemplate 代码目录路径模板；结构分组为空
     * @param permissionCode 路由绑定权限；结构分组或无权限入口为空
     * @param titles 管理员维护的双语标题
     * @param icon 图标含义
     * @param sortOrder 同级展示排序
     * @param visible 是否进入当前用户导航
     * @param version 乐观锁版本
     * @param children 按排序固定排列的下级菜单
     */
    public ManagedMenu(
            @NotNull Long menuId,
            @Nullable Long parentId,
            @Nullable String routeCode,
            @Nullable String pathTemplate,
            @Nullable String permissionCode,
            @NotNull MenuTitles titles,
            @NotNull MenuIconName icon,
            int sortOrder,
            boolean visible,
            long version,
            @NotNull List<ManagedMenu> children) {
        this.menuId = menuId;
        this.parentId = parentId;
        this.routeCode = routeCode;
        this.pathTemplate = pathTemplate;
        this.permissionCode = permissionCode;
        this.titles = titles;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.visible = visible;
        this.version = version;
        this.children = List.copyOf(children);
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

    /** @return 代码目录路径模板；结构分组返回 null */
    public @Nullable String getPathTemplate() {
        return pathTemplate;
    }

    /** @return 路由要求的代码权限；无权限入口或结构分组返回 null */
    public @Nullable String getPermissionCode() {
        return permissionCode;
    }

    /** @return 管理员维护的完整双语标题 */
    public @NotNull MenuTitles getTitles() {
        return titles;
    }

    /** @return 管理端图标含义 */
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

    /** @return 按同级排序固定排列的不可变下级菜单 */
    public @NotNull List<ManagedMenu> getChildren() {
        return children;
    }
}
