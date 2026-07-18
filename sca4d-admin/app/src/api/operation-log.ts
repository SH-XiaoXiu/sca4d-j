import { apiClient } from "./client";
import {
  normalizePageData,
  type Id,
  type PageData,
  type PageQuery,
  type PageResult,
  type QueryParameters
} from "@sca4d/admin-common";

/** 操作日志分页支持的模块、动作、结果、资源、追踪标识和时间筛选。 */
export interface OperationLogQuery extends PageQuery {
  moduleCode?: string;
  actionCode?: string;
  result?: AuditResult;
  subjectId?: Id;
  principalId?: Id;
  targetType?: string;
  targetId?: Id;
  traceId?: string;
  createdAtStart?: string;
  createdAtEnd?: string;
}

/** 操作日志记录的成功或失败结果。 */
export type AuditResult = "SUCCESS" | "FAILED";

/** 操作日志资源当前是否可以打开详情页。 */
export type ResourceNavigationState = "NAVIGABLE" | "FORBIDDEN" | "DELETED" | "UNSUPPORTED";

/** 操作日志操作者或目标资源的名称、状态和详情地址。 */
export interface ResourceReferenceView {
  resourceType: string;
  resourceId: Id;
  label: string | null;
  tenantId: Id | null;
  navigationState: ResourceNavigationState;
  path: string | null;
  navigationMessage: string | null;
}

/** 操作日志列表展示的动作、摘要、资源引用和发生时间。 */
export interface OperationLogView {
  id: Id;
  subjectId: Id | null;
  accountId: Id | null;
  principalId: Id | null;
  subjectDisplayName: string | null;
  principalDisplayName: string | null;
  moduleCode: string;
  moduleName: string;
  actionCode: string;
  actionName: string;
  result: AuditResult;
  requestUri: string;
  payloadType: string;
  summary: string;
  actorReference: ResourceReferenceView | null;
  targetReference: ResourceReferenceView | null;
  createdAt: string;
}

/** 操作日志筛选器中的动作编码和本地化名称。 */
export interface OperationLogActionOption {
  code: string;
  name: string;
}

/** 操作日志筛选器中的模块及其可选动作。 */
export interface OperationLogModuleOption {
  code: string;
  name: string;
  actions: OperationLogActionOption[];
}

/** 操作日志操作者远程选择器中的用户选项。 */
export interface OperationLogOperatorOption {
  id: Id;
  label: string;
}

/** 操作日志详情包含的请求、错误和类型化审计内容。 */
export interface OperationLogDetailView extends OperationLogView {
  actor: unknown;
  target: unknown;
  request: unknown;
  error: unknown;
  payload: unknown;
}

function compactQuery<T extends object>(query: T): QueryParameters {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== "" && value !== null && value !== undefined)
  ) as QueryParameters;
}

/** 操作日志分页、详情、筛选目录和操作者候选项请求方法。 */
export const operationLogApi = {
  catalog(signal?: AbortSignal): Promise<OperationLogModuleOption[]> {
    return apiClient.get<OperationLogModuleOption[]>("/mg/system/operation-logs/catalog", undefined, signal);
  },
  async operatorOptions(
    search: string,
    page: number,
    size: number,
    signal?: AbortSignal
  ): Promise<PageResult<OperationLogOperatorOption>> {
    const result = await apiClient.get<PageData<OperationLogOperatorOption>>(
      "/mg/system/operation-logs/operator-options",
      compactQuery({ search, page, size }),
      signal
    );
    return normalizePageData(result);
  },
  async page(query: OperationLogQuery, signal?: AbortSignal): Promise<PageResult<OperationLogView>> {
    const page = await apiClient.get<PageData<OperationLogView>>(
      "/mg/system/operation-logs",
      compactQuery(query),
      signal
    );
    return normalizePageData(page);
  },
  detail(id: Id, signal?: AbortSignal): Promise<OperationLogDetailView> {
    return apiClient.get<OperationLogDetailView>(`/mg/system/operation-logs/${id}`, undefined, signal);
  }
};
