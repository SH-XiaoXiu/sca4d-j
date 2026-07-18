package com.sca4d.system.view;

import com.sca4d.system.MenuIconName;
import com.sca4d.system.NavigationMenu;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 当前 Principal 可以直接渲染的最终导航节点视图。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NavigationMenuView {

    private Long menuId;
    private String routeCode;
    private String path;
    private String title;
    private MenuIconName icon;
    private List<NavigationMenuView> children;

    /**
     * 把服务端已过滤导航递归转换为响应结构。
     *
     * @param menu 当前 Principal 可见的导航节点
     * @return 不包含权限判断和模板变量的最终导航视图
     */
    public static @NotNull NavigationMenuView from(@NotNull NavigationMenu menu) {
        return new NavigationMenuView(
                menu.getMenuId(),
                menu.getRouteCode(),
                menu.getPath(),
                menu.getTitle(),
                menu.getIcon(),
                menu.getChildren().stream().map(NavigationMenuView::from).toList());
    }
}
