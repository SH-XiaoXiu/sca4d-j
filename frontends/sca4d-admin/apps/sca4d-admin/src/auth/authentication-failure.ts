type AuthenticationFailureHandler = () => Promise<void> | void;

let handler: AuthenticationFailureHandler | undefined;

/** 注册确认当前登录 Session 已经失效时执行的本地状态清理操作。 */
export function registerAuthenticationFailureHandler(
  nextHandler: AuthenticationFailureHandler,
): void {
  handler = nextHandler;
}

/** 通知认证 Store 清除已经确认失效的本地 Session。 */
export async function notifyAuthenticationFailure(): Promise<void> {
  await handler?.();
}
