import type { ApiClient } from "../api-client";
import type { Id, ManagedMenuView, MenuWriteInput, NavigationRouteView } from "../types";

/** 创建平台菜单树、路由目录、写入和重排请求方法。 */
export function createSystemMenuApi(client: ApiClient) {
  const root = "/mg/system/menus";
  return {
    menus(): Promise<ManagedMenuView[]> {
      return client.get(root);
    },
    routeOptions(): Promise<NavigationRouteView[]> {
      return client.get(`${root}/route-options`);
    },
    create(input: MenuWriteInput, idempotencyKey: string): Promise<Id> {
      return client.post(root, { ...input, idempotencyKey });
    },
    update(menuId: Id, input: MenuWriteInput, version: Id): Promise<void> {
      return client.put(`${root}/${menuId}`, { ...input, version });
    },
    reorder(parentId: Id | null, items: Array<{ menuId: Id; version: Id }>): Promise<void> {
      return client.put(`${root}/order`, { parentId, items });
    },
    delete(menuId: Id, version: Id, idempotencyKey: string): Promise<void> {
      return client.request({
        path: `${root}/${menuId}`,
        method: "DELETE",
        body: { version, idempotencyKey }
      });
    }
  };
}
