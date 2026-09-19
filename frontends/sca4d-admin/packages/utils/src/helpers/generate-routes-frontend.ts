import type { RouteRecordRaw } from 'vue-router';

import { filterTree, mapTree } from '@vben-core/shared/utils';

/**
 * 动态生成路由 - 前端方式
 */
async function generateRoutesByFrontend(
  routes: RouteRecordRaw[],
  roles: string[],
  forbiddenComponent?: RouteRecordRaw['component'],
): Promise<RouteRecordRaw[]> {
  const copied = mapTree<RouteRecordRaw, RouteRecordRaw>(routes, (route) => ({
    ...route,
    ...(route.meta ? { meta: { ...route.meta } } : {}),
  }));
  if (!forbiddenComponent)
    return filterTree(copied, (route) => hasAuthority(route, roles));

  // 父级权限同样约束子路由，防止布局移除或命名视图绕过拒绝结果。
  const deniedRoutes = new Set<RouteRecordRaw>();
  return mapTree(copied, (route) => {
    const denied = deniedRoutes.has(route) || !hasAuthority(route, roles);
    if (denied) {
      const pending = [...(route.children ?? [])];
      for (const child of pending) {
        deniedRoutes.add(child);
        pending.push(...(child.children ?? []));
      }
      route.component = forbiddenComponent;
      if (route.components) {
        route.components = Object.fromEntries(
          Object.keys(route.components).map((name) => [
            name,
            forbiddenComponent,
          ]),
        );
      }
      route.meta ??= { title: '' };
      route.meta.hideInMenu =
        Boolean(route.meta.hideInMenu) || !menuHasVisibleWithForbidden(route);
      delete route.redirect;
    }
    return route;
  });
}

/**
 * 判断路由是否有权限访问
 * @param route
 * @param access
 */
function hasAuthority(route: RouteRecordRaw, access: string[]) {
  const authority = route.meta?.authority;
  if (!authority) {
    return true;
  }
  return access.some((value) => authority.includes(value));
}

/**
 * 判断路由是否在菜单中显示，但是访问会被重定向到403
 * @param route
 */
function menuHasVisibleWithForbidden(route: RouteRecordRaw) {
  return (
    !!route.meta?.authority &&
    Reflect.has(route.meta || {}, 'menuVisibleWithForbidden') &&
    !!route.meta?.menuVisibleWithForbidden
  );
}

export { generateRoutesByFrontend, hasAuthority };
