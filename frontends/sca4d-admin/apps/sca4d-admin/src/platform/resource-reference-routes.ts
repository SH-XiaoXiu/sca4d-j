import type {
  LocationQuery,
  LocationQueryRaw,
  RouteLocationRaw,
} from 'vue-router';

import type { ResourceReferenceData } from '@sca4d/admin';

import { resourceReferenceId } from '@sca4d/admin';

/** 日志可以直接定位的应用资源类型。 */
export type RoutableResourceType =
  | 'account'
  | 'notification:email:sender'
  | 'notification:email:template'
  | 'role';

/** 当前 Session 可以访问的资源管理页面。 */
export interface ResourceReferenceRouteAccess {
  readonly accounts: boolean;
  readonly email: boolean;
  readonly roles: boolean;
}

/** 把日志目标转换成管理端资源页的查询路由。 */
export function resourceReferenceRoute(
  reference: ResourceReferenceData,
  access: ResourceReferenceRouteAccess,
): null | RouteLocationRaw {
  const id = resourceReferenceId(reference.resourceType, reference.id);
  if (
    !id ||
    !/^[1-9][0-9]*$/u.test(id) ||
    !isRoutableResourceType(reference.resourceType)
  ) {
    return null;
  }
  if (reference.resourceType === 'account') {
    if (!access.accounts) return null;
    return { path: '/sca4d-server/accounts', query: { accountId: id } };
  }
  if (reference.resourceType === 'role') {
    if (!access.roles) return null;
    return { path: '/sca4d-server/roles', query: { roleId: id } };
  }
  if (!access.email) return null;
  if (reference.resourceType === 'notification:email:sender') {
    return {
      path: '/sca4d-server/email',
      query: { senderId: id, tab: 'senders' },
    };
  }
  return {
    path: '/sca4d-server/email',
    query: { tab: 'templates', templateId: id },
  };
}

/** 读取账号、角色和 Email 页使用的深链接标识。 */
export function resourceQueryId(
  query: LocationQuery,
  key: 'accountId' | 'roleId' | 'senderId' | 'templateId',
): null | string {
  const value = query[key];
  const candidate = Array.isArray(value) ? value[0] : value;
  const checked = typeof candidate === 'string' ? candidate.trim() : '';
  return /^[1-9][0-9]*$/u.test(checked) ? checked : null;
}

/** 移除已经被资源页消费的深链接参数，并保留其他查询条件。 */
export function withoutResourceQuery(
  query: LocationQuery,
  ...keys: readonly string[]
): LocationQueryRaw {
  const next: LocationQueryRaw = { ...query };
  keys.forEach((key) => delete next[key]);
  return next;
}

/** 检查查询参数是否引用受支持的可定位资源类型。 */
function isRoutableResourceType(
  value: null | string,
): value is RoutableResourceType {
  return (
    value === 'account' ||
    value === 'role' ||
    value === 'notification:email:sender' ||
    value === 'notification:email:template'
  );
}
