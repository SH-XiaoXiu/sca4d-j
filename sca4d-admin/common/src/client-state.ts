import type { ClientStateChange } from "./types";

function header(headers: Headers, name: string): string | null {
  const value = headers.get(name);
  return value && value.trim() ? value.trim() : null;
}

/** 从响应头解析客户端刷新目标、版本和幂等重放标记。 */
export function readClientState(headers: Headers): ClientStateChange {
  const refresh = header(headers, "X-Client-State-Refresh");
  return {
    stale: header(headers, "X-Client-State-Stale") === "true",
    refreshTargets: refresh
      ? refresh
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean)
      : [],
    version: header(headers, "X-Client-State-Version"),
    idempotencyReplayed: header(headers, "Idempotency-Replayed") === "true"
  };
}

/** 判断响应是否要求刷新任一客户端状态。 */
export function hasClientStateChange(change: ClientStateChange): boolean {
  return (
    change.stale || change.refreshTargets.length > 0 || change.version !== null || change.idempotencyReplayed
  );
}
