package com.sca4d.system;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 服务端按当前 Principal 和权限过滤后的一个导航节点。 */
public final class NavigationMenu {

    private final Long menuId;
    private final String routeCode;
    private final String path;
    private final String title;
    private final MenuIconName icon;
    private final List<NavigationMenu> children;

    /**
     * 创建浏览器可以直接渲染的最终导航节点。
     *
     * @param menuId 菜单固定主键
     * @param routeCode 代码路由编码；结构分组为空
     * @param path 已解析租户参数的最终路径；结构分组为空
     * @param title 当前请求语言标题
     * @param icon 图标含义
     * @param children 已过滤且按数据库排序排列的下级导航
     */
    public NavigationMenu(
            @NotNull Long menuId,
            @Nullable String routeCode,
            @Nullable String path,
            @NotNull String title,
            @NotNull MenuIconName icon,
            @NotNull List<NavigationMenu> children) {
        this.menuId = menuId;
        this.routeCode = routeCode;
        this.path = path;
        this.title = title;
        this.icon = icon;
        this.children = List.copyOf(children);
    }

    /** @return 菜单固定主键 */
    public @NotNull Long getMenuId() {
        return menuId;
    }

    /** @return 代码路由编码；结构分组返回 null */
    public @Nullable String getRouteCode() {
        return routeCode;
    }

    /** @return 最终前端路径；结构分组返回 null */
    public @Nullable String getPath() {
        return path;
    }

    /** @return 当前请求语言标题 */
    public @NotNull String getTitle() {
        return title;
    }

    /** @return 图标含义 */
    public @NotNull MenuIconName getIcon() {
        return icon;
    }

    /** @return 已过滤的不可变下级导航 */
    public @NotNull List<NavigationMenu> getChildren() {
        return children;
    }
}
