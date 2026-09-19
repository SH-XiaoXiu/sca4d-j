/** 后端通知客户端重新读取本地状态时使用的响应头。 */
export const CLIENT_STATE_REFRESH_HEADER = 'x-client-state-refresh';

/** 当前 Session、权限和关联资料共同使用的客户端状态目标。 */
export const AUTHENTICATION_SESSION_CLIENT_STATE = 'authentication.session';

/** 判断响应头是否要求重新读取当前 Session；未知目标不会触发任何操作。 */
export function requestsAuthenticationSessionRefresh(
  headerValue: null | string | undefined,
): boolean {
  if (!headerValue) return false;
  return headerValue
    .split(',')
    .map((target) => target.trim())
    .some((target) => target === AUTHENTICATION_SESSION_CLIENT_STATE);
}
