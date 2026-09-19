/** 管理端持久化的 access Token、可选在线 Session Token 和有效期。 */
export interface SessionToken {
  readonly accessToken: string;
  readonly accessTokenExpiresAt: string;
  readonly sessionExpiresAt: string;
  readonly sessionId: string;
  readonly sessionToken: null | string;
  readonly tokenMode: 'JWT' | 'SESSION';
  readonly tokenType: 'Bearer';
}

/** 单次 HTTP 或 SSE 请求实际使用的 Authorization Token。 */
export interface AuthorizationToken {
  readonly tokenType: 'Bearer';
  readonly tokenValue: string;
}

/** API 请求选择普通 access Token 或在线 Session Token。 */
export type AuthTokenKind = 'access' | 'session';

/** 由宿主实现 Token 的读取、写入和清除。 */
export interface TokenPort {
  clear(): void;
  read(): null | SessionToken;
  write(token: SessionToken): void;
}

/** 管理端支持的请求语言。 */
export type AdminLanguage = 'en-US' | 'zh-CN';

/** 由宿主保存语言，并向纯 TypeScript Controller 提供当前值。 */
export interface LanguagePort {
  read(): AdminLanguage;
  write(language: AdminLanguage): void;
}

/** 与具体 HTTP 库无关的请求。 */
export interface HttpRequest {
  readonly body?: unknown;
  readonly headers: Readonly<Record<string, string>>;
  readonly method: 'DELETE' | 'GET' | 'PATCH' | 'POST' | 'PUT';
  readonly path: string;
  readonly query?: Readonly<Record<string, number | string | undefined>>;
  readonly responseType?: 'arrayBuffer' | 'json';
}

/** 与具体 HTTP 库无关的响应。 */
export interface HttpResponse {
  readonly body?: unknown;
  readonly headers: Readonly<Record<string, string>>;
  readonly status: number;
}

/** 由宿主负责实际发送 HTTP 请求。 */
export interface HttpPort {
  request(request: HttpRequest): Promise<HttpResponse>;
}

/** 后端签发的对象上传请求。 */
export interface ObjectTransferRequest {
  readonly body: ArrayBuffer;
  readonly headers: Readonly<Record<string, string>>;
  readonly method: string;
  readonly url: string;
}

/** 由宿主原样执行对象上传，不添加管理端认证信息。 */
export interface ObjectTransferPort {
  transfer(request: ObjectTransferRequest): Promise<void>;
}

/** SSE 连接状态。 */
export type RealtimeStatus = 'connected' | 'connecting' | 'disconnected';

/** 由宿主使用浏览器或其他运行时能力建立 SSE。 */
export interface RealtimePort {
  subscribe(
    token: AuthorizationToken,
    onEvent: (event: string) => void,
    onStatus: (status: RealtimeStatus) => void,
  ): () => void;
}
