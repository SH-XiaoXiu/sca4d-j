import type { CurrentSession, Profile, SessionToken } from '@sca4d/admin';

const MAX_TIMER_DELAY = 2_147_483_647;
const REFRESH_LEAD_MILLISECONDS = 30_000;

/** 需要认证的请求使用哪一种 Token。 */
export type AuthenticationTokenKind = 'access' | 'session';

/**
 * 选择普通请求或 Session 操作使用的 Token。
 *
 * JWT 模式的刷新和注销必须使用 `sessionToken`；SESSION 模式没有独立
 * `sessionToken`，因此使用 `accessToken`。
 */
export function selectAuthenticationToken(
  tokens: SessionToken,
  kind: AuthenticationTokenKind,
): string {
  return kind === 'session'
    ? (tokens.sessionToken ?? tokens.accessToken)
    : tokens.accessToken;
}

/**
 * 计算 JWT 下一次刷新前需要等待的毫秒数。
 *
 * 刷新安排在 access Token 或 backing Session 过期前 30 秒；SESSION 模式
 * 不需要后台刷新。返回 `0` 表示应立即刷新或清除已经过期的 Session。
 */
export function millisecondsUntilTokenRefresh(
  tokens: SessionToken,
  now: number = Date.now(),
): null | number {
  if (tokens.tokenMode !== 'JWT') return null;

  const accessRefreshAt =
    Date.parse(tokens.accessTokenExpiresAt) - REFRESH_LEAD_MILLISECONDS;
  const sessionExpiresAt = Date.parse(tokens.sessionExpiresAt);
  if (accessRefreshAt >= sessionExpiresAt) return null;

  return Math.min(Math.max(0, accessRefreshAt - now), MAX_TIMER_DELAY);
}

/** 当前 Session 转换为 Vben 用户信息时使用的本地化文本。 */
export interface CurrentSessionUserText {
  readonly accountDescription: string;
  readonly fallbackName: string;
  readonly realmDescription: string;
}

/** 按当前权限选择第一个能够访问的 Sca4d Server 业务页面。 */
export function currentSessionHomePath(permissions: readonly string[]): string {
  const codes = new Set(permissions);
  if (
    codes.has('PLATFORM:ACCOUNTS:READ') ||
    codes.has('PLATFORM:ACCOUNTS:MANAGE')
  ) {
    return '/sca4d-server/accounts';
  }
  if (codes.has('PLATFORM:ROLES:READ') || codes.has('PLATFORM:ROLES:MANAGE')) {
    return '/sca4d-server/roles';
  }
  return '/profile';
}

/** 把当前 Session 和个人资料转成 Vben 页面使用的用户信息。 */
export function currentSessionUser(
  session: CurrentSession,
  profile: Profile,
  text: CurrentSessionUserText,
) {
  const accountId = session.account.accountId;
  const displayName = profile.displayName || text.fallbackName;
  return {
    avatar: '',
    accountDescription: text.accountDescription,
    desc: text.realmDescription,
    homePath: currentSessionHomePath(session.permissions),
    realName: displayName,
    roles: [] as string[],
    token: '',
    userId: accountId,
    username: displayName,
  };
}
