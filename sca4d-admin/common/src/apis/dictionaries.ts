import type { ApiClient } from "../api-client";
import type {
  DictionaryCreateInput,
  DictionaryItemCreateInput,
  DictionaryItemPageQuery,
  DictionaryItemUpdateInput,
  DictionaryOrderItemView,
  DictionaryPageQuery,
  DictionarySelectionView,
  DictionaryUpdateInput,
  Id,
  ManagedDictionaryItemView,
  ManagedDictionaryView,
  PageData
} from "../types";

/** 管理字典类型、字典项、排序和业务选择项的请求接口。 */
export interface DictionaryApi {
  page(query: DictionaryPageQuery, signal?: AbortSignal): Promise<PageData<ManagedDictionaryView>>;
  detail(dictionaryId: Id, signal?: AbortSignal): Promise<ManagedDictionaryView>;
  create(input: DictionaryCreateInput): Promise<Id>;
  update(dictionaryId: Id, input: DictionaryUpdateInput): Promise<void>;
  delete(dictionaryId: Id, version: Id, idempotencyKey: string): Promise<void>;
  order(signal?: AbortSignal): Promise<DictionaryOrderItemView[]>;
  reorder(items: Array<{ id: Id; version: Id }>): Promise<void>;
  pageItems(
    dictionaryId: Id,
    query: DictionaryItemPageQuery,
    signal?: AbortSignal
  ): Promise<PageData<ManagedDictionaryItemView>>;
  createItem(dictionaryId: Id, input: DictionaryItemCreateInput): Promise<Id>;
  updateItem(dictionaryId: Id, itemId: Id, input: DictionaryItemUpdateInput): Promise<void>;
  deleteItem(dictionaryId: Id, itemId: Id, version: Id, idempotencyKey: string): Promise<void>;
  itemOrder(dictionaryId: Id, signal?: AbortSignal): Promise<DictionaryOrderItemView[]>;
  reorderItems(dictionaryId: Id, items: Array<{ id: Id; version: Id }>): Promise<void>;
  options(codes: string[], signal?: AbortSignal): Promise<DictionarySelectionView[]>;
}

/** 按平台或租户路径创建字典管理请求方法。 */
export function createDictionaryApi(client: ApiClient, root: string): DictionaryApi {
  return {
    page(query, signal) {
      return client.get(root, query, signal);
    },
    detail(dictionaryId, signal) {
      return client.get(`${root}/${dictionaryId}`, undefined, signal);
    },
    create(input) {
      return client.post(root, input);
    },
    update(dictionaryId, input) {
      return client.put(`${root}/${dictionaryId}`, input);
    },
    delete(dictionaryId, version, idempotencyKey) {
      return client.request({
        path: `${root}/${dictionaryId}`,
        method: "DELETE",
        body: { version, idempotencyKey }
      });
    },
    order(signal) {
      return client.get(`${root}/order`, undefined, signal);
    },
    reorder(items) {
      return client.put(`${root}/order`, { items });
    },
    pageItems(dictionaryId, query, signal) {
      return client.get(`${root}/${dictionaryId}/items`, query, signal);
    },
    createItem(dictionaryId, input) {
      return client.post(`${root}/${dictionaryId}/items`, input);
    },
    updateItem(dictionaryId, itemId, input) {
      return client.put(`${root}/${dictionaryId}/items/${itemId}`, input);
    },
    deleteItem(dictionaryId, itemId, version, idempotencyKey) {
      return client.request({
        path: `${root}/${dictionaryId}/items/${itemId}`,
        method: "DELETE",
        body: { version, idempotencyKey }
      });
    },
    itemOrder(dictionaryId, signal) {
      return client.get(`${root}/${dictionaryId}/items/order`, undefined, signal);
    },
    reorderItems(dictionaryId, items) {
      return client.put(`${root}/${dictionaryId}/items/order`, { items });
    },
    options(codes, signal) {
      return client.get(`${root}/options`, { codes }, signal);
    }
  };
}
