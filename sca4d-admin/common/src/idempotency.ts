/** 使用浏览器 crypto 或随机数创建一次写请求的幂等键。 */
export function createIdempotencyKey(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

/** 为请求数据补充新的幂等键。 */
export function withIdempotencyKey<T extends object>(
  request: T,
  idempotencyKey = createIdempotencyKey()
): T & {
  idempotencyKey: string;
} {
  return { ...request, idempotencyKey };
}
