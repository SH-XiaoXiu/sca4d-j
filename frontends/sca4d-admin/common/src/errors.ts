import type { ResourcePhase } from './controller';
import type { ApiResponse } from './types';

/** 管理端可稳定处理的错误种类。 */
export type AdminErrorKind =
  | 'authentication'
  | 'conflict'
  | 'network'
  | 'notFound'
  | 'permission'
  | 'precondition'
  | 'refresh'
  | 'unavailable'
  | 'unknown'
  | 'validation'
  | 'versionConflict';

/** Controller 与 Vue 壳之间使用的统一错误。 */
export class AdminError extends Error {
  readonly callId: null | string;
  readonly code: null | string;
  readonly data: unknown;
  readonly kind: AdminErrorKind;
  readonly status: null | number;

  constructor(
    kind: AdminErrorKind,
    message: string,
    status: null | number = null,
    code: null | string = null,
    callId: null | string = null,
    data: unknown = null,
  ) {
    super(message);
    this.name = 'AdminError';
    this.kind = kind;
    this.status = status;
    this.code = code;
    this.callId = callId;
    this.data = data;
  }
}

/** 把权限和并发错误映射为资源页面阶段，其他错误保留调用方给出的阶段。 */
export function resourcePhaseForError(
  error: AdminError,
  fallback: ResourcePhase = 'error',
): ResourcePhase {
  if (error.kind === 'permission') return 'forbidden';
  if (error.kind === 'versionConflict') return 'conflict';
  return fallback;
}

/** 把 HTTP 状态与后端错误外壳转换为统一错误。 */
export function mapHttpError(status: number, body: unknown): AdminError {
  const response = isApiResponse(body) ? body : null;
  const message = response?.message || defaultMessage(status);
  let kind: AdminErrorKind = 'unknown';
  switch (status) {
    case 401: {
      kind = 'authentication';
      break;
    }
    case 403: {
      kind = 'permission';
      break;
    }
    case 404: {
      kind = 'notFound';
      break;
    }
    case 409: {
      kind = 'conflict';
      break;
    }
    case 412: {
      kind = 'versionConflict';
      break;
    }
    case 428: {
      kind = 'precondition';
      break;
    }
    default: {
      if (status >= 500) kind = 'unavailable';
      else if (status >= 400) kind = 'validation';
    }
  }
  return new AdminError(
    kind,
    message,
    status,
    response?.code ?? null,
    response?.callId ?? null,
    response?.data ?? null,
  );
}

/** 把宿主网络异常转换为不泄露实现细节的错误。 */
export function mapNetworkError(error: unknown): AdminError {
  if (error instanceof AdminError) return error;
  return new AdminError('network', 'Network request failed');
}

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof Reflect.get(value, 'code') === 'string'
  );
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Authentication required';
  if (status === 403) return 'Permission denied';
  if (status === 412) return 'The resource has changed';
  if (status === 428) return 'The resource version is required';
  if (status >= 500) return 'Service unavailable';
  return 'Request failed';
}

/** 资源视图已关闭或开始了更新的读取，调用方不得再使用本次结果。 */
export class SupersededReadError extends Error {
  constructor() {
    super('Resource read superseded');
    this.name = 'SupersededReadError';
  }
}

/** 写入已经完成，但重新读取失败；调用方应刷新数据而非重复写入。 */
export class SavedResourceRefreshError extends AdminError {
  constructor() {
    super('refresh', 'The change was saved, but refreshing failed');
  }
}
