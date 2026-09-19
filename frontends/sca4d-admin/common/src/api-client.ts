import type {
  AuthTokenKind,
  HttpPort,
  HttpRequest,
  LanguagePort,
  TokenPort,
} from './ports';
import type { ApiResponse } from './types';

import { mapHttpError, mapNetworkError } from './errors';
import { responseEtag, responseLocation } from './etag';
import {
  CLIENT_STATE_REFRESH_HEADER,
  requestsAuthenticationSessionRefresh,
} from './client-state';

/** 包含数据与资源响应头的 API 结果。 */
export interface ResourceResult<T> {
  readonly data: T;
  readonly etag: null | string;
  readonly location: null | string;
  readonly status: number;
}

/** 使用 Ports 发送统一 REST 请求，并集中处理语言、Token、错误和 ETag。 */
export class AdminApiClient {
  constructor(
    private readonly http: HttpPort,
    private readonly tokens: TokenPort,
    private readonly languages: LanguagePort,
    private readonly onAuthenticationSessionRefresh: () => void = () => {},
  ) {}

  /** 发送 DELETE 请求。 */
  delete<T = void>(
    path: string,
    headers?: Readonly<Record<string, string>>,
    tokenKind: AuthTokenKind = 'access',
  ): Promise<ResourceResult<T>> {
    return this.request<T>(
      { method: 'DELETE', path, ...(headers ? { headers } : {}) },
      tokenKind,
    );
  }

  /** 发送 GET 请求。 */
  get<T>(
    path: string,
    query?: HttpRequest['query'],
    tokenKind: AuthTokenKind = 'access',
  ): Promise<ResourceResult<T>> {
    return this.request<T>(
      { method: 'GET', path, ...(query ? { query } : {}) },
      tokenKind,
    );
  }

  /**
   * 读取不使用统一 JSON 外壳的二进制资源。
   *
   * @param path 二进制资源路径
   * @param acceptedMediaType 当前调用方能够处理的响应媒体类型
   * @return 原始响应字节与资源响应头
   */
  async getBinary(
    path: string,
    acceptedMediaType: string,
  ): Promise<ResourceResult<ArrayBuffer>> {
    const token = this.authorizationToken('access');
    const headers: Record<string, string> = {
      Accept: acceptedMediaType,
      'Accept-Language': this.languages.read(),
    };
    if (token) headers.Authorization = `${token.tokenType} ${token.tokenValue}`;
    try {
      const response = await this.http.request({
        method: 'GET',
        path,
        headers,
        responseType: 'arrayBuffer',
      });
      this.handleClientStateRefresh(response.headers);
      if (response.status < 200 || response.status >= 300) {
        const error = mapHttpError(response.status, response.body);
        throw error;
      }
      return Object.freeze({
        data: response.body as ArrayBuffer,
        status: response.status,
        etag: responseEtag(response.headers),
        location: responseLocation(response.headers),
      });
    } catch (error) {
      throw mapNetworkError(error);
    }
  }

  /** 发送 PATCH 请求。 */
  patch<T>(
    path: string,
    body: unknown,
    headers?: Readonly<Record<string, string>>,
    tokenKind: AuthTokenKind = 'access',
  ): Promise<ResourceResult<T>> {
    return this.request<T>(
      { method: 'PATCH', path, body, ...(headers ? { headers } : {}) },
      tokenKind,
    );
  }

  /** 以 file 字段发送 multipart POST；浏览器负责生成 boundary。 */
  postMultipart<T>(
    path: string,
    file: File,
    headers?: Readonly<Record<string, string>>,
    tokenKind: AuthTokenKind = 'access',
  ): Promise<ResourceResult<T>> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.request<T>(
      { method: 'POST', path, body, ...(headers ? { headers } : {}) },
      tokenKind,
    );
  }

  /** 以 file 字段发送 multipart PATCH；浏览器负责生成 boundary。 */
  patchMultipart<T>(
    path: string,
    file: File,
    headers?: Readonly<Record<string, string>>,
    tokenKind: AuthTokenKind = 'access',
  ): Promise<ResourceResult<T>> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.request<T>(
      { method: 'PATCH', path, body, ...(headers ? { headers } : {}) },
      tokenKind,
    );
  }

  /** 发送 POST 请求。 */
  post<T>(
    path: string,
    body?: unknown,
    headers?: Readonly<Record<string, string>>,
    tokenKind: AuthTokenKind = 'access',
  ): Promise<ResourceResult<T>> {
    return this.request<T>(
      {
        method: 'POST',
        path,
        ...(body === undefined ? {} : { body }),
        ...(headers ? { headers } : {}),
      },
      tokenKind,
    );
  }

  /** 发送 PUT 请求。 */
  put<T>(
    path: string,
    body: unknown,
    headers?: Readonly<Record<string, string>>,
    tokenKind: AuthTokenKind = 'access',
  ): Promise<ResourceResult<T>> {
    return this.request<T>(
      { method: 'PUT', path, body, ...(headers ? { headers } : {}) },
      tokenKind,
    );
  }

  private authorizationToken(
    kind: AuthTokenKind,
  ): null | { tokenType: 'Bearer'; tokenValue: string } {
    const tokens = this.tokens.read();
    if (!tokens) return null;
    const tokenValue =
      kind === 'session'
        ? (tokens.sessionToken ?? tokens.accessToken)
        : tokens.accessToken;
    return { tokenType: tokens.tokenType, tokenValue };
  }

  private async request<T>(
    input: Omit<HttpRequest, 'headers'> & {
      readonly headers?: Readonly<Record<string, string>>;
    },
    tokenKind: AuthTokenKind,
  ): Promise<ResourceResult<T>> {
    const token = this.authorizationToken(tokenKind);
    const headers: Record<string, string> = {
      Accept: 'application/json',
      'Accept-Language': this.languages.read(),
      ...input.headers,
    };
    if (typeof FormData !== 'undefined' && input.body instanceof FormData) {
      for (const name of Object.keys(headers)) {
        if (name.toLowerCase() === 'content-type') delete headers[name];
      }
    } else if (input.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (token) headers.Authorization = `${token.tokenType} ${token.tokenValue}`;
    try {
      const response = await this.http.request({ ...input, headers });
      this.handleClientStateRefresh(response.headers);
      if (response.status < 200 || response.status >= 300) {
        const error = mapHttpError(response.status, response.body);
        throw error;
      }
      const envelope = response.body as ApiResponse<T> | undefined;
      return Object.freeze({
        data: envelope?.data as T,
        status: response.status,
        etag: responseEtag(response.headers),
        location: responseLocation(response.headers),
      });
    } catch (error) {
      throw mapNetworkError(error);
    }
  }

  /** 只分发当前管理端认识的客户端状态目标。 */
  private handleClientStateRefresh(
    headers: Readonly<Record<string, string>>,
  ): void {
    const value = Object.entries(headers).find(
      ([name]) => name.toLowerCase() === CLIENT_STATE_REFRESH_HEADER,
    )?.[1];
    if (requestsAuthenticationSessionRefresh(value)) {
      this.onAuthenticationSessionRefresh();
    }
  }
}
