import type { GenerateMenuAndRoutesOptions } from '@vben/types';

import { generateAccessible } from '@vben/access';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';
import { resetStaticRoutes } from '@vben/utils';

import { BasicLayout, IFrameView } from '#/layouts';

import { accessRoutes, routes } from './routes';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

/** 使用本地静态路由和当前 Session 的权限编码生成可访问菜单。 */
async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  return await generateAccessible(preferences.app.accessMode, {
    ...options,
    forbiddenComponent,
    layoutMap: { BasicLayout, IFrameView },
    pageMap: import.meta.glob('../views/**/*.vue'),
  });
}

/** 删除旧动态路由，并按最新权限编码重新安装 Vben 菜单和路由。 */
async function replaceAccess(
  router: GenerateMenuAndRoutesOptions['router'],
  roles: string[],
) {
  const accessStore = useAccessStore();
  resetStaticRoutes(router, routes);
  accessStore.setAccessMenus([]);
  accessStore.setAccessRoutes([]);
  accessStore.setIsAccessChecked(false);

  const { accessibleMenus, accessibleRoutes } = await generateAccess({
    roles,
    router,
    routes: accessRoutes,
  });
  accessStore.setAccessMenus(accessibleMenus);
  accessStore.setAccessRoutes(accessibleRoutes);
  accessStore.setIsAccessChecked(true);
}

export { generateAccess, replaceAccess };
