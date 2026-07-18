package com.sca4d.system;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 业务模块向平台菜单注册可导航前端路由的扩展点。 */
public interface NavigationRouteContributor {

    /**
     * 返回当前模块允许数据库菜单引用的路由。
     *
     * @return 不包含详情页和任意组件声明的菜单入口路由
     */
    @NotNull
    List<NavigationRoute> navigationRoutes();
}
