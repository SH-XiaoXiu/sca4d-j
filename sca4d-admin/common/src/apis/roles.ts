import type { ApiClient } from "../api-client";
import type {
  Id,
  ManagedRolePermissionView,
  ManagedRoleView,
  PageData,
  RoleCreateInput,
  RolePageQuery,
  RoleUpdateInput,
  RoleUsageView
} from "../types";

/** 角色分页、详情、创建、修改、权限替换和删除请求接口。 */
export interface RoleApi {
  page(query: RolePageQuery, signal?: AbortSignal): Promise<PageData<ManagedRoleView>>;
  permissions(signal?: AbortSignal): Promise<ManagedRolePermissionView[]>;
  create(input: RoleCreateInput): Promise<Id>;
  detail(roleId: Id): Promise<ManagedRoleView>;
  update(roleId: Id, input: RoleUpdateInput): Promise<void>;
  replacePermissions(roleId: Id, permissionIds: Id[], version: Id): Promise<void>;
  usage(roleId: Id): Promise<RoleUsageView>;
  delete(roleId: Id, version: Id, idempotencyKey: string): Promise<void>;
}

/** 按平台或租户路径创建角色管理请求方法。 */
export function createRoleApi(client: ApiClient, root: string): RoleApi {
  return {
    page(query, signal) {
      return client.get(root, query, signal);
    },
    permissions(signal) {
      return client.get(`${root}/permissions`, undefined, signal);
    },
    create(input) {
      return client.post(root, input);
    },
    detail(roleId) {
      return client.get(`${root}/${roleId}`);
    },
    update(roleId, input) {
      return client.put(`${root}/${roleId}`, input);
    },
    replacePermissions(roleId, permissionIds, version) {
      return client.put(`${root}/${roleId}/permissions`, { permissionIds, version });
    },
    usage(roleId) {
      return client.get(`${root}/${roleId}/usage`);
    },
    delete(roleId, version, idempotencyKey) {
      return client.request({
        path: `${root}/${roleId}`,
        method: "DELETE",
        body: { version, idempotencyKey }
      });
    }
  };
}
