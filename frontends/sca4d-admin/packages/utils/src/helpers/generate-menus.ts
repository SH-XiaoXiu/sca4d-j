import type { Router, RouteRecordRaw } from 'vue-router';

import type {
  ExRouteRecordRaw,
  MenuRecordRaw,
  RouteMeta,
} from '@vben-core/typings';

import { filterTree, mapTree, sortTree } from '@vben-core/shared/utils';

/**
 * 根据 routes 生成菜单列表
 * @param routes - 路由配置列表
 * @param router - Vue Router 实例
 * @returns 生成的菜单列表
 */
function generateMenus(
  routes: RouteRecordRaw[],
  router: Router,
): MenuRecordRaw[] {
  // 将路由列表转换为一个以 name 为键的对象映射
  const finalRoutesMap: { [key: string]: string } = Object.fromEntries(
    router.getRoutes().map(({ name, path }) => [name, path]),
  );

  // 先过滤副本，再从子节点向父节点移除纯目录，保留自身可访问的页面。
  const visible = filterTree(
    mapTree<RouteRecordRaw, RouteRecordRaw>(routes, (route) => ({
      ...route,
      ...(route.meta ? { meta: { ...route.meta } } : {}),
    })),
    (route) => !route.meta?.hideInMenu,
  );
  const nodes: RouteRecordRaw[] = [...visible];
  for (const node of nodes) nodes.push(...(node.children ?? []));
  const emptyDirectories = new Set<RouteRecordRaw>();
  for (const node of nodes.toReversed()) {
    if (node.children) {
      node.children = node.children.filter(
        (child) => !emptyDirectories.has(child),
      );
      if (
        node.children.length === 0 &&
        !node.component &&
        !node.components &&
        !node.meta?.hideChildrenInMenu &&
        !node.meta?.link
      ) {
        emptyDirectories.add(node);
      }
    }
  }
  let menus = mapTree<ExRouteRecordRaw, MenuRecordRaw>(
    visible.filter((node) => !emptyDirectories.has(node)),
    (route) => {
      // 获取最终的路由路径
      const path = finalRoutesMap[route.name as string] ?? route.path ?? '';

      const {
        meta = {} as RouteMeta,
        name: routeName,
        redirect,
        children = [],
      } = route;
      const {
        activeIcon,
        badge,
        badgeType,
        badgeVariants,
        hideChildrenInMenu = false,
        icon,
        link,
        order,
        title = '',
        query,
      } = meta;

      // 确保菜单名称不为空
      const name = (title || routeName || '') as string;

      // 处理子菜单
      const resultChildren = hideChildrenInMenu
        ? []
        : ((children as MenuRecordRaw[]) ?? []);

      // 设置子菜单的父子关系
      if (resultChildren.length > 0) {
        resultChildren.forEach((child) => {
          child.parents = [...(route.parents ?? []), path];
          child.parent = path;
        });
      }

      // 确定最终路径
      const resultPath = hideChildrenInMenu ? redirect || path : link || path;

      return {
        activeIcon,
        badge,
        badgeType,
        badgeVariants,
        icon,
        name,
        query,
        order,
        parent: route.parent,
        parents: route.parents,
        path: resultPath,
        show: !meta.hideInMenu,
        children: resultChildren,
      };
    },
  );

  // 对菜单进行排序，避免order=0时被替换成999的问题
  menus = sortTree(menus, (a, b) => (a?.order ?? 999) - (b?.order ?? 999));

  // 过滤掉隐藏的菜单项
  return filterTree(menus, (menu) => !!menu.show);
}

export { generateMenus };
