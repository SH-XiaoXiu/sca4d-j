package com.sca4d.system;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.i18n.SupportedLocale;
import com.sca4d.common.permission.PermissionCatalog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 汇总业务模块注册路由并校验其权限引用的代码目录。 */
public final class NavigationRouteCatalog {

    private final List<NavigationRoute> routes;
    private final Map<String, NavigationRoute> byCode;

    /**
     * 构建唯一、可预测的导航路由目录。
     *
     * <p>每个贡献者读取一次，目录构建只在内存中校验；带权限路由必须引用 {@link PermissionCatalog} 已注册编码。
     *
     * @param contributors 业务模块路由贡献者
     * @param permissionCatalog 代码权限权威目录
     * @param messages 后端消息资源解析入口
     */
    public NavigationRouteCatalog(
            @NotNull List<NavigationRouteContributor> contributors,
            @NotNull PermissionCatalog permissionCatalog,
            @NotNull LocalizedMessages messages) {
        Set<String> permissionCodes = permissionCatalog.items().stream()
                .map(item -> item.getCode())
                .collect(Collectors.toUnmodifiableSet());
        Map<String, NavigationRoute> checked = new LinkedHashMap<>();
        for (NavigationRouteContributor contributor : contributors) {
            if (contributor == null) {
                throw new IllegalArgumentException("导航路由贡献者不能为空");
            }
            List<NavigationRoute> contributed = contributor.navigationRoutes();
            if (contributed == null) {
                throw new IllegalArgumentException("导航路由贡献者不能返回 null: " + contributor.getClass().getName());
            }
            for (NavigationRoute route : contributed) {
                if (route == null) {
                    throw new IllegalArgumentException("导航路由不能为空: " + contributor.getClass().getName());
                }
                if (route.getPermissionCode() != null && !permissionCodes.contains(route.getPermissionCode())) {
                    throw new IllegalArgumentException("导航路由引用了未注册权限: " + route.getCode());
                }
                for (SupportedLocale locale : SupportedLocale.values()) {
                    messages.get(locale.locale(), route.getDefaultTitleKey());
                }
                if (checked.putIfAbsent(route.getCode(), route) != null) {
                    throw new IllegalArgumentException("重复导航路由编码: " + route.getCode());
                }
            }
        }
        this.routes = checked.values().stream()
                .sorted(Comparator.comparing(NavigationRoute::getCode))
                .toList();
        this.byCode = Map.copyOf(checked);
    }

    /** @return 按路由编码固定排序的全部菜单入口路由 */
    public @NotNull List<NavigationRoute> routes() {
        return routes;
    }

    /**
     * 按固定编码读取注册路由。
     *
     * @param code 数据库菜单引用的路由编码
     * @return 已注册路由；编码不存在时返回 null
     */
    public @Nullable NavigationRoute find(@NotNull String code) {
        return byCode.get(code);
    }
}
