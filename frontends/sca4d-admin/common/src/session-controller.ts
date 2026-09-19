import type { AdminApiClient } from './api-client';
import type {
  RealtimePort,
  RealtimeStatus,
  SessionToken,
  TokenPort,
} from './ports';
import type { CurrentSession, LoginOutcome } from './types';

import { ObservableController } from './controller';
import { AdminError } from './errors';
import { AUTHENTICATION_SESSION_CHANGED_EVENT } from './realtime-events';

/** 页面使用的权限结果，避免 Vue 页面解释权限编码。 */
export interface SessionCapabilities {
  readonly accountsManage: boolean;
  readonly accountsRead: boolean;
  readonly rolesManage: boolean;
  readonly rolesRead: boolean;
}

/** 登录、后续因素验证、当前 Session 和 SSE 的不可变状态。 */
export interface SessionState {
  readonly capabilities: SessionCapabilities;
  readonly error: AdminError | null;
  readonly loginAttemptId: null | string;
  readonly phase:
    | 'anonymous'
    | 'authenticated'
    | 'authenticating'
    | 'error'
    | 'initializing'
    | 'totp';
  readonly realtime: RealtimeStatus;
  readonly session: CurrentSession | null;
}

const NO_CAPABILITIES: SessionCapabilities = Object.freeze({
  accountsRead: false,
  accountsManage: false,
  rolesRead: false,
  rolesManage: false,
});

/** 管理完整登录、Session 恢复、权限转换、退出和 SSE 刷新流程。 */
export class SessionController extends ObservableController<SessionState> {
  private realtimeRefresh: null | Promise<void> = null;
  private refreshTimer: null | ReturnType<typeof setTimeout> = null;
  private stopRealtime: (() => void) | null = null;
  private tokenRefresh: null | Promise<void> = null;

  constructor(
    private readonly api: AdminApiClient,
    private readonly tokens: TokenPort,
    private readonly realtimePort: RealtimePort,
  ) {
    super({
      phase: 'anonymous',
      session: null,
      loginAttemptId: null,
      error: null,
      realtime: 'disconnected',
      capabilities: NO_CAPABILITIES,
    });
  }

  /** 清除失效的本地 Session。 */
  clearSession(): void {
    this.stopRealtime?.();
    this.stopRealtime = null;
    if (this.refreshTimer) clearTimeout(this.refreshTimer);
    this.refreshTimer = null;
    this.tokens.clear();
    this.publish({
      phase: 'anonymous',
      session: null,
      loginAttemptId: null,
      error: null,
      realtime: 'disconnected',
      capabilities: NO_CAPABILITIES,
    });
  }

  /** 从 Token Port 恢复 Session；Token 不存在时保持匿名。 */
  async initialize(): Promise<void> {
    const tokens = this.tokens.read();
    if (!tokens) return;
    this.publish({ ...this.state, phase: 'initializing', error: null });
    try {
      if (
        tokens.tokenMode === 'JWT' &&
        Date.parse(tokens.accessTokenExpiresAt) <= Date.now()
      ) {
        await this.refreshAccessToken();
      }
      const currentTokens = this.tokens.read();
      if (currentTokens) this.scheduleAccessRefresh(currentTokens);
      await this.loadCurrent();
    } catch {
      this.clearSession();
    }
  }

  /** 重新读取当前 Session 和权限。 */
  async loadCurrent(): Promise<void> {
    const result = await this.api.get<CurrentSession>('/sessions/current');
    const session = freezeSession(result.data);
    this.publish({
      phase: 'authenticated',
      session,
      loginAttemptId: null,
      error: null,
      realtime: this.state.realtime,
      capabilities: capabilities(session.permissions),
    });
    this.openRealtime();
  }

  /** 创建登录 attempt；需要 TOTP 时保留 attemptId，完成时保存 Token 并读取当前 Session。 */
  async login(username: string, password: string): Promise<void> {
    this.publish({
      ...this.state,
      phase: 'authenticating',
      error: null,
      loginAttemptId: null,
    });
    try {
      const result = await this.api.post<LoginOutcome>('/login/attempts', {
        username,
        password,
      });
      await this.acceptLoginOutcome(result.data);
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 删除当前 Session；无论远端结果如何都清除本地 Token。 */
  async logout(): Promise<void> {
    try {
      if (this.tokens.read())
        await this.api.delete('/sessions/current', undefined, 'session');
    } finally {
      this.clearSession();
    }
  }

  /** 向当前登录 attempt 提交 TOTP，并在满足策略后建立 Session。 */
  async submitTotp(code: string): Promise<void> {
    const attemptId = this.state.loginAttemptId;
    if (!attemptId) throw new Error('No login attempt is pending');
    this.publish({ ...this.state, phase: 'authenticating', error: null });
    try {
      const result = await this.api.post<LoginOutcome>(
        `/login/attempts/${encodeURIComponent(attemptId)}/totp/proofs`,
        { code },
      );
      await this.acceptLoginOutcome(result.data);
    } catch (error) {
      this.publish({
        ...this.state,
        phase: 'totp',
        error: asAdminError(error),
      });
      throw error;
    }
  }

  private async acceptLoginOutcome(outcome: LoginOutcome): Promise<void> {
    if (!outcome.session) {
      this.publish({
        ...this.state,
        phase: 'totp',
        loginAttemptId: outcome.authentication.attemptId,
        error: null,
      });
      return;
    }
    this.tokens.write(outcome.session);
    this.scheduleAccessRefresh(outcome.session);
    await this.loadCurrent();
  }

  private fail(error: unknown): void {
    this.tokens.clear();
    this.publish({
      phase: 'error',
      session: null,
      loginAttemptId: null,
      error: asAdminError(error),
      realtime: 'disconnected',
      capabilities: NO_CAPABILITIES,
    });
  }

  private openRealtime(): void {
    if (this.stopRealtime) return;
    const token = this.tokens.read();
    if (!token) return;
    const tokenValue = token.sessionToken ?? token.accessToken;
    this.stopRealtime = this.realtimePort.subscribe(
      { tokenType: token.tokenType, tokenValue },
      (event) => {
        if (event === AUTHENTICATION_SESSION_CHANGED_EVENT)
          this.refreshFromServer();
      },
      (realtime) => this.publish({ ...this.state, realtime }),
    );
  }

  /** 合并并发 refresh，成功后原子替换 access Token 和过期时间。 */
  private refreshAccessToken(): Promise<void> {
    if (this.tokenRefresh) return this.tokenRefresh;
    if (!this.tokens.read())
      return Promise.reject(new Error('No Session token is available'));
    this.tokenRefresh = this.api
      .post<SessionToken>(
        '/sessions/current/tokens',
        undefined,
        undefined,
        'session',
      )
      .then((result) => {
        const current = this.tokens.read();
        if (!current || current.sessionId !== result.data.sessionId) return;
        this.tokens.write(result.data);
        this.scheduleAccessRefresh(result.data);
      })
      .finally(() => {
        this.tokenRefresh = null;
      });
    return this.tokenRefresh;
  }

  /** 合并响应头和实时事件触发的 Session 刷新，失败时清除失效的本地 Session。 */
  refreshFromServer(): void {
    if (this.realtimeRefresh) return;
    this.realtimeRefresh = this.refreshAccessToken()
      .then(() => this.loadCurrent())
      .catch(() => this.clearSession())
      .finally(() => {
        this.realtimeRefresh = null;
      });
  }

  /** JWT 在过期前 30 秒刷新；SESSION 模式不安排后台刷新。 */
  private scheduleAccessRefresh(tokens: SessionToken): void {
    if (this.refreshTimer) clearTimeout(this.refreshTimer);
    this.refreshTimer = null;
    if (tokens.tokenMode !== 'JWT') return;
    if (Date.parse(tokens.sessionExpiresAt) - Date.now() <= 30_000) return;
    const delay = Math.max(
      0,
      Math.min(
        Date.parse(tokens.accessTokenExpiresAt) - Date.now() - 30_000,
        2_147_483_647,
      ),
    );
    this.refreshTimer = setTimeout(() => {
      void this.refreshAccessToken().catch(() => this.clearSession());
    }, delay);
  }
}

function capabilities(permissions: readonly string[]): SessionCapabilities {
  const values = new Set(permissions);
  return Object.freeze({
    accountsRead:
      values.has('platform:accounts:read') ||
      values.has('platform:accounts:manage'),
    accountsManage: values.has('platform:accounts:manage'),
    rolesRead:
      values.has('platform:roles:read') || values.has('platform:roles:manage'),
    rolesManage: values.has('platform:roles:manage'),
  });
}

function freezeSession(session: CurrentSession): CurrentSession {
  return Object.freeze({
    ...session,
    permissions: Object.freeze([...session.permissions]),
  });
}

function asAdminError(error: unknown): AdminError {
  return error instanceof AdminError
    ? error
    : new AdminError('unknown', 'Request failed');
}
