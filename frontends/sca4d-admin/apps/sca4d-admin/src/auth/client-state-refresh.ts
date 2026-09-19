type AuthenticationSessionRefreshHandler = () => Promise<void> | void;

let handler: AuthenticationSessionRefreshHandler | undefined;

/** 注册响应头和 SSE 握手要求重新读取当前 Session 时执行的操作。 */
export function registerAuthenticationSessionRefreshHandler(
  nextHandler: AuthenticationSessionRefreshHandler,
): void {
  handler = nextHandler;
}

/** 通知认证 Store 重新读取当前 Session；并发通知由 Store 合并。 */
export function notifyAuthenticationSessionRefresh(): void {
  void handler?.();
}
