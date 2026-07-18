package com.sca4d.system.view;

import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.SupportedLocale;
import com.sca4d.system.MenuIconName;
import com.sca4d.system.NavigationRoute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 菜单编辑器可选择的代码注册路由视图。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NavigationRouteView {

    private String code;
    private String pathTemplate;
    private Set<PrincipalType> principalTypes;
    private String permissionCode;
    private Map<String, String> defaultTitles;
    private MenuIconName defaultIcon;

    /**
     * 把代码路由目录项转换为不包含组件名的管理选项。
     *
     * @param route 已完成权限引用校验的注册路由
     * @param messages 后端消息资源解析入口
     * @return 路由编码、路径模板、Principal 范围和默认展示元数据
     */
    public static @NotNull NavigationRouteView from(
            @NotNull NavigationRoute route, @NotNull LocalizedMessages messages) {
        Map<String, String> defaultTitles = new LinkedHashMap<>();
        for (SupportedLocale locale : SupportedLocale.values()) {
            defaultTitles.put(
                    locale.value(), messages.get(locale.locale(), route.getDefaultTitleKey()));
        }
        return new NavigationRouteView(
                route.getCode(),
                route.getPathTemplate(),
                route.getPrincipalTypes(),
                route.getPermissionCode(),
                Map.copyOf(defaultTitles),
                route.getDefaultIcon());
    }
}
