package com.sca4d.system.view;

import com.sca4d.system.MenuIconName;
import com.sca4d.system.menu.ManagedMenu;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** 平台菜单管理树节点视图。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManagedMenuView {

    private Long menuId;
    private Long parentId;
    private String routeCode;
    private String pathTemplate;
    private String permissionCode;
    private Map<String, String> titles;
    private MenuIconName icon;
    private Integer sortOrder;
    private Boolean visible;
    private Long version;
    private List<ManagedMenuView> children;

    /**
     * 把 ManagedMenu 及其子菜单递归转换为 HTTP 响应。
     *
     * @param menu 已完成目录和树结构校验的管理菜单
     * @return 保留双语标题、版本和下级节点的响应结构
     */
    public static @NotNull ManagedMenuView from(@NotNull ManagedMenu menu) {
        return new ManagedMenuView(
                menu.getMenuId(),
                menu.getParentId(),
                menu.getRouteCode(),
                menu.getPathTemplate(),
                menu.getPermissionCode(),
                menu.getTitles().asMap(),
                menu.getIcon(),
                menu.getSortOrder(),
                menu.isVisible(),
                menu.getVersion(),
                menu.getChildren().stream().map(ManagedMenuView::from).toList());
    }
}
