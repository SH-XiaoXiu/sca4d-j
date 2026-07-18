import axios, {
  AxiosHeaders,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  isAxiosError
} from "axios";
import { ApiError } from "./api-error";
import { hasClientStateChange, readClientState } from "./client-state";
import type { ApiResponse, ClientStateChange, QueryParameters, QueryValue, SessionToken } from "./types";

/** 公共 API 客户端支持的 HTTP 方法。 */
export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

/** 一次 API 请求的路径、方法、查询参数、请求体、Header 和 Signal。 */
export interface ApiRequest<TBody = unknown> {
  path: string;
  method?: HttpMethod;
  query?: QueryParameters;
  body?: TBody;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  timeoutMs?: number;
  /** 当前请求的 401 表示 Proof 或短期 Flow 失败时，不结束浏览器已有的登录 Session。 */
  preserveSessionOnUnauthorized?: boolean;
}

/** 同时返回业务数据、响应头和 HTTP 状态的 API 调用结果。 */
export interface ApiResult<T> {
  data: T;
  httpStatus: number;
  location: string | null;
  etag: string | null;
}

/** 创建 API 客户端时传入的基础地址、fetch 实现和状态回调。 */
export interface ApiClientOptions {
  baseUrl: string;
  token?: () => SessionToken | null;
  axios?: AxiosInstance;
  timeoutMs?: number;
  locale?: () => string;
  translate?: (messageKey: string) => string;
  onUnauthorized?: (error: ApiError) => void;
  onClientState?: (change: ClientStateChange) => void;
}

/** 发送后端请求并解析共用 ApiResponse 的客户端接口。 */
export interface ApiClient {
  request<TResponse, TBody = unknown>(request: ApiRequest<TBody>): Promise<TResponse>;
  requestResult<TResponse, TBody = unknown>(request: ApiRequest<TBody>): Promise<ApiResult<TResponse>>;
  get<TResponse>(path: string, query?: QueryParameters, signal?: AbortSignal): Promise<TResponse>;
  post<TResponse, TBody = unknown>(path: string, body?: TBody, signal?: AbortSignal): Promise<TResponse>;
  put<TResponse, TBody = unknown>(path: string, body?: TBody, signal?: AbortSignal): Promise<TResponse>;
  patch<TResponse, TBody = unknown>(path: string, body?: TBody, signal?: AbortSignal): Promise<TResponse>;
  delete<TResponse>(path: string, signal?: AbortSignal): Promise<TResponse>;
  getBlob(path: string, signal?: AbortSignal): Promise<Blob>;
}

const DEFAULT_TIMEOUT_MS = 15_000;

function serializeValue(value: QueryValue): string {
  if (value instanceof Date) {
    return value.toISOString();
  }
  return String(value);
}

function queryString(query: QueryParameters | undefined): URLSearchParams {
  const parameters = new URLSearchParams();
  if (!query) {
    return parameters;
  }
  for (const [name, rawValue] of Object.entries(query)) {
    const values = Array.isArray(rawValue) ? rawValue : [rawValue];
    for (const value of values) {
      if (value !== null && value !== undefined) {
        parameters.append(name, serializeValue(value));
      }
    }
  }
  return parameters;
}

function retryAfter(headers: Headers): number | null {
  const value = headers.get("Retry-After");
  if (!value) {
    return null;
  }
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : null;
}

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (!value || typeof value !== "object") {
    return false;
  }
  const response = value as Partial<ApiResponse<unknown>>;
  return (
    typeof response.code === "number" &&
    (response.message === null || typeof response.message === "string") &&
    (response.messageKey === null || typeof response.messageKey === "string") &&
    Array.isArray(response.messageArgs) &&
    "data" in response
  );
}

/** 创建自动序列化查询参数、解析共用响应并报告客户端刷新状态的 API 客户端。 */
export function createApiClient(options: ApiClientOptions): ApiClient {
  const clientMessage = (messageKey: string, fallback: string): string =>
    options.translate?.(messageKey) || fallback;
  const requestAxios =
    options.axios ??
    axios.create({
      baseURL: options.baseUrl,
      timeout: options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
      validateStatus: () => true,
      paramsSerializer: (parameters) => queryString(parameters as QueryParameters).toString()
    });

  async function requestResult<TResponse, TBody = unknown>(request: ApiRequest<TBody>): Promise<ApiResult<TResponse>> {
    const method = request.method ?? "GET";
    const headers = new AxiosHeaders(request.headers);
    headers.set("Accept", "application/json");
    const locale = options.locale?.();
    if (locale) {
      headers.set("Accept-Language", locale);
    }
    const token = options.token?.();
    if (token?.tokenName && token.tokenValue) {
      headers.set(token.tokenName, token.tokenValue);
    }
    const binaryBody = typeof Blob !== "undefined" && request.body instanceof Blob;
    const formBody = typeof FormData !== "undefined" && request.body instanceof FormData;
    if (request.body !== undefined && !headers.has("Content-Type") && !binaryBody && !formBody) {
      headers.set("Content-Type", "application/json");
    }

    try {
      const config: AxiosRequestConfig<TBody> = {
        url: request.path,
        method: method.toLowerCase(),
        headers,
        timeout: request.timeoutMs ?? options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
        validateStatus: () => true
      };
      if (request.query) {
        config.params = request.query;
      }
      if (request.signal) {
        config.signal = request.signal;
      }
      if (request.body !== undefined) {
        config.data = request.body;
      }
      const response: AxiosResponse<unknown> = await requestAxios.request(config);
      const responseHeaders = new Headers();
      for (const [name, value] of Object.entries(response.headers)) {
        if (value !== undefined && value !== null) {
          responseHeaders.set(name, Array.isArray(value) ? value.join(",") : String(value));
        }
      }
      const clientState = readClientState(responseHeaders);
      if (hasClientStateChange(clientState)) {
        options.onClientState?.(clientState);
      }

      const payload = response.data;
      if (response.status === 204) {
        return {
          data: undefined as TResponse,
          httpStatus: response.status,
          location: responseHeaders.get("Location"),
          etag: responseHeaders.get("ETag")
        };
      }
      if (!isApiResponse(payload)) {
        throw new ApiError(clientMessage("client.api.invalidResponse", "服务端返回了无法识别的响应"), {
          kind: "invalid-response",
          messageKey: "client.api.invalidResponse",
          httpStatus: response.status,
          clientState
        });
      }
      if (response.status < 200 || response.status >= 300 || payload.code !== 0) {
        const error = new ApiError(payload.message || "请求失败", {
          kind: "api",
          code: payload.code,
          messageKey: payload.messageKey,
          messageArgs: payload.messageArgs,
          httpStatus: response.status,
          traceId: payload.traceId,
          data: payload.data,
          retryAfterSeconds: retryAfter(responseHeaders),
          clientState
        });
        if (response.status === 401 && !request.preserveSessionOnUnauthorized) {
          options.onUnauthorized?.(error);
        }
        throw error;
      }
      return {
        data: payload.data as TResponse,
        httpStatus: response.status,
        location: responseHeaders.get("Location"),
        etag: responseHeaders.get("ETag")
      };
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }
      if (isAxiosError(error)) {
        if (error.code === "ECONNABORTED" || error.code === "ETIMEDOUT") {
          throw new ApiError(clientMessage("client.api.timeout", "请求超时"), {
            kind: "timeout",
            messageKey: "client.api.timeout",
            cause: error
          });
        }
        if (error.code === "ERR_CANCELED") {
          throw new ApiError(clientMessage("client.api.cancelled", "请求已取消"), {
            kind: "network",
            messageKey: "client.api.cancelled",
            cause: error
          });
        }
      }
      throw new ApiError(clientMessage("client.api.network", "网络连接失败"), {
        kind: "network",
        messageKey: "client.api.network",
        cause: error
      });
    }
  }

  async function request<TResponse, TBody = unknown>(apiRequest: ApiRequest<TBody>): Promise<TResponse> {
    return (await requestResult<TResponse, TBody>(apiRequest)).data;
  }

  /** 读取受当前认证和语言 Header 保护、但不使用 ApiResponse 包装的二进制资源。 */
  async function getBlob(path: string, signal?: AbortSignal): Promise<Blob> {
    const headers = new AxiosHeaders();
    headers.set("Accept", "application/octet-stream, image/*");
    const locale = options.locale?.();
    if (locale) headers.set("Accept-Language", locale);
    const token = options.token?.();
    if (token?.tokenName && token.tokenValue) headers.set(token.tokenName, token.tokenValue);
    try {
      const config: AxiosRequestConfig = {
        url: path,
        method: "get",
        headers,
        responseType: "arraybuffer",
        timeout: options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
        validateStatus: () => true
      };
      if (signal) config.signal = signal;
      const response: AxiosResponse<ArrayBuffer> = await requestAxios.request(config);
      const responseHeaders = new Headers();
      for (const [name, value] of Object.entries(response.headers)) {
        if (value !== undefined && value !== null) {
          responseHeaders.set(name, Array.isArray(value) ? value.join(",") : String(value));
        }
      }
      const clientState = readClientState(responseHeaders);
      if (hasClientStateChange(clientState)) options.onClientState?.(clientState);
      if (response.status >= 200 && response.status < 300) {
        return new Blob([response.data], {
          type: responseHeaders.get("Content-Type") ?? "application/octet-stream"
        });
      }
      let payload: unknown;
      try {
        payload = JSON.parse(new TextDecoder().decode(response.data));
      } catch {
        payload = null;
      }
      if (isApiResponse(payload)) {
        const error = new ApiError(payload.message || "请求失败", {
          kind: "api",
          code: payload.code,
          messageKey: payload.messageKey,
          messageArgs: payload.messageArgs,
          httpStatus: response.status,
          traceId: payload.traceId,
          data: payload.data,
          retryAfterSeconds: retryAfter(responseHeaders),
          clientState
        });
        if (response.status === 401) options.onUnauthorized?.(error);
        throw error;
      }
      throw new ApiError(clientMessage("client.api.invalidResponse", "服务端返回了无法识别的响应"), {
        kind: "invalid-response",
        messageKey: "client.api.invalidResponse",
        httpStatus: response.status,
        clientState
      });
    } catch (error) {
      if (error instanceof ApiError) throw error;
      if (isAxiosError(error) && (error.code === "ECONNABORTED" || error.code === "ETIMEDOUT")) {
        throw new ApiError(clientMessage("client.api.timeout", "请求超时"), {
          kind: "timeout",
          messageKey: "client.api.timeout",
          cause: error
        });
      }
      throw new ApiError(clientMessage("client.api.network", "网络连接失败"), {
        kind: "network",
        messageKey: "client.api.network",
        cause: error
      });
    }
  }

  return {
    request,
    requestResult,
    get(path, query, signal) {
      const apiRequest: ApiRequest = { path };
      if (query) apiRequest.query = query;
      if (signal) apiRequest.signal = signal;
      return request(apiRequest);
    },
    post(path, body, signal) {
      const apiRequest: ApiRequest<typeof body> = { path, method: "POST" };
      if (body !== undefined) apiRequest.body = body;
      if (signal) apiRequest.signal = signal;
      return request(apiRequest);
    },
    put(path, body, signal) {
      const apiRequest: ApiRequest<typeof body> = { path, method: "PUT" };
      if (body !== undefined) apiRequest.body = body;
      if (signal) apiRequest.signal = signal;
      return request(apiRequest);
    },
    patch(path, body, signal) {
      const apiRequest: ApiRequest<typeof body> = { path, method: "PATCH" };
      if (body !== undefined) apiRequest.body = body;
      if (signal) apiRequest.signal = signal;
      return request(apiRequest);
    },
    delete(path, signal) {
      const apiRequest: ApiRequest = { path, method: "DELETE" };
      if (signal) apiRequest.signal = signal;
      return request(apiRequest);
    },
    getBlob
  };
}
