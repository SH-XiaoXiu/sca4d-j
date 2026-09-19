import type { RouteLocationNormalizedLoaded } from 'vue-router';

const ROUTE_PARAM_PATTERN = /:([A-Za-z_][A-Za-z0-9_]*)/gu;

/**
 * 把 `RouteMeta.activePath` 中的动态参数替换为当前路由参数。
 *
 * 参数缺失时返回当前路由路径，避免把无法匹配的模板传给菜单组件。
 */
function resolveActiveMenuPath(
  route: Pick<RouteLocationNormalizedLoaded, 'meta' | 'params' | 'path'>,
): string {
  const activePath = route.meta.activePath;
  if (!activePath) return route.path;

  let complete = true;
  const resolvedPath = activePath.replaceAll(
    ROUTE_PARAM_PATTERN,
    (_placeholder, paramName: string) => {
      const value = route.params[paramName];
      if (typeof value === 'string' && value.length > 0) {
        return encodeURIComponent(value);
      }
      if (Array.isArray(value) && value.length > 0) {
        return value.map((item) => encodeURIComponent(item)).join('/');
      }
      complete = false;
      return '';
    },
  );
  return complete ? resolvedPath : route.path;
}

export { resolveActiveMenuPath };
