import type { ClientStateChange } from "./types";

/** API 请求失败发生在业务响应、HTTP、网络还是响应解析阶段。 */
export type ApiErrorKind = "api" | "network" | "timeout" | "invalid-response";

/** 构造 ApiError 时保存的业务码、HTTP 状态、消息和原始响应。 */
export interface ApiErrorOptions {
  kind: ApiErrorKind;
  code?: number;
  messageKey?: string | null;
  messageArgs?: unknown[];
  httpStatus?: number;
  traceId?: string | null;
  data?: unknown;
  retryAfterSeconds?: number | null;
  clientState?: ClientStateChange | null;
  cause?: unknown;
}

/** API 客户端抛出的结构化错误，页面可按 kind 和 status 选择提示方式。 */
export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly code: number | null;
  readonly messageKey: string | null;
  readonly messageArgs: readonly unknown[];
  readonly httpStatus: number | null;
  readonly traceId: string | null;
  readonly data: unknown;
  readonly retryAfterSeconds: number | null;
  readonly clientState: ClientStateChange | null;

  constructor(message: string, options: ApiErrorOptions) {
    super(message, options.cause === undefined ? undefined : { cause: options.cause });
    this.name = "ApiError";
    this.kind = options.kind;
    this.code = options.code ?? null;
    this.messageKey = options.messageKey ?? null;
    this.messageArgs = Object.freeze([...(options.messageArgs ?? [])]);
    this.httpStatus = options.httpStatus ?? null;
    this.traceId = options.traceId ?? null;
    this.data = options.data;
    this.retryAfterSeconds = options.retryAfterSeconds ?? null;
    this.clientState = options.clientState ?? null;
  }
}

/** 判断未知异常是否为公共 API 客户端抛出的 ApiError。 */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
