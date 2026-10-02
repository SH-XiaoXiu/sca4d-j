import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type { AdminError } from './errors';
import type {
  AuditResult,
  Int64String,
  OperationLog,
  OperationLogPage,
} from './types';

import { ObservableController } from './controller';
import { AdminError as AdminErrorValue } from './errors';

interface OperationLogPageResponse extends Omit<OperationLogPage, 'total'> {
  readonly total: Int64String;
}

/** 操作日志分页使用的完整筛选条件。 */
export interface OperationLogFilters {
  readonly accountId: string;
  readonly actorPrincipalId: string;
  readonly occurredFrom: string;
  readonly occurredTo: string;
  readonly operation: string;
  readonly page: number;
  readonly requestId: string;
  readonly result: '' | AuditResult;
  readonly size: number;
  readonly targetId: string;
  readonly targetType: string;
}

/** 操作日志列表和详情的独立加载状态。 */
export interface OperationLogsState {
  readonly detailError: AdminError | null;
  readonly detailPhase: ResourcePhase;
  readonly error: AdminError | null;
  readonly filters: OperationLogFilters;
  readonly page: OperationLogPage;
  readonly phase: ResourcePhase;
  readonly selected: null | OperationLog;
}

const EMPTY_PAGE: OperationLogPage = Object.freeze({
  items: Object.freeze([]),
  page: 1,
  size: 20,
  total: 0,
});

const DEFAULT_FILTERS: OperationLogFilters = Object.freeze({
  accountId: '',
  actorPrincipalId: '',
  occurredFrom: '',
  occurredTo: '',
  operation: '',
  page: 1,
  requestId: '',
  result: '',
  size: 20,
  targetId: '',
  targetType: '',
});

/** 查询 Sca4d Server 操作日志分页和单条详情。 */
export class OperationLogsController extends ObservableController<OperationLogsState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      detailError: null,
      detailPhase: 'idle',
      error: null,
      filters: DEFAULT_FILTERS,
      page: EMPTY_PAGE,
      phase: 'idle',
      selected: null,
    });
  }

  /** 清除当前抽屉中的日志详情和详情错误。 */
  clearSelection(): void {
    this.cancelRead('detail');
    this.publish({
      ...this.state,
      detailError: null,
      detailPhase: 'idle',
      selected: null,
    });
  }

  /** 使用完整筛选条件读取操作日志页。 */
  async load(filters: Partial<OperationLogFilters> = {}): Promise<void> {
    const read = this.beginRead('load');
    const next = Object.freeze({ ...this.state.filters, ...filters });
    this.publish({
      ...this.state,
      error: null,
      filters: next,
      phase: 'loading',
    });
    try {
      const result = await this.api.get<OperationLogPageResponse>(
        '/system/audit/records',
        {
          accountId: textOrUndefined(next.accountId),
          actorPrincipalId: textOrUndefined(next.actorPrincipalId),
          occurredFrom: textOrUndefined(next.occurredFrom),
          occurredTo: textOrUndefined(next.occurredTo),
          operation: textOrUndefined(next.operation),
          page: next.page,
          requestId: textOrUndefined(next.requestId),
          result: next.result || undefined,
          size: next.size,
          targetId: textOrUndefined(next.targetId),
          targetType: textOrUndefined(next.targetType),
        },
      );
      const page = freezePage(result.data);
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        ...this.state,
        error: null,
        page,
        phase: page.items.length > 0 ? 'ready' : 'empty',
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      const value = asAdminError(error);
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        ...this.state,
        error: value,
        phase: value.kind === 'permission' ? 'forbidden' : 'error',
      });
      throw error;
    }
  }

  /** 读取指定标识的完整操作日志并作为当前详情。 */
  async require(id: Int64String): Promise<void> {
    const read = this.beginRead('detail');
    this.publish({
      ...this.state,
      detailError: null,
      detailPhase: 'loading',
      selected: null,
    });
    try {
      const result = await this.api.get<OperationLog>(
        `/system/audit/records/${id}`,
      );
      if (!this.isCurrentRead('detail', read)) return;
      this.publish({
        ...this.state,
        detailError: null,
        detailPhase: 'ready',
        selected: Object.freeze({ ...result.data }),
      });
    } catch (error) {
      if (!this.isCurrentRead('detail', read)) return;
      const value = asAdminError(error);
      if (!this.isCurrentRead('detail', read)) return;
      this.publish({
        ...this.state,
        detailError: value,
        detailPhase: value.kind === 'permission' ? 'forbidden' : 'error',
      });
      throw error;
    }
  }
}

function asAdminError(error: unknown): AdminError {
  return error instanceof AdminErrorValue
    ? error
    : new AdminErrorValue('unknown', 'Request failed');
}

function freezePage(page: OperationLogPageResponse): OperationLogPage {
  if (!/^(0|[1-9][0-9]*)$/.test(page.total))
    throw new Error('Invalid operation log total');
  const total = Number(page.total);
  if (!Number.isSafeInteger(total))
    throw new Error('Operation log total exceeds the supported range');
  return Object.freeze({
    ...page,
    items: Object.freeze(page.items.map((item) => Object.freeze({ ...item }))),
    total,
  });
}

function textOrUndefined(value: string): string | undefined {
  const checked = value.trim();
  return checked || undefined;
}
