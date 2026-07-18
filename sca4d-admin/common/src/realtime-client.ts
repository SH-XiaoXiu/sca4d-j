import { createEventSource, type EventSourceClient } from "eventsource-client";
import type { SessionToken } from "./types";

/** 服务端发送的一个实时事件。 */
export interface RealtimeEvent {
  event: string;
  data: string;
  id: string | null;
}

/** 创建实时客户端所需的运行时依赖和回调。 */
export interface RealtimeClientOptions {
  baseUrl: string;
  token: () => SessionToken | null;
  onEvent: (event: RealtimeEvent) => void;
  onReconnect: () => void;
  onUnauthorized: () => void;
  fetch?: typeof globalThis.fetch;
}

/** 管理当前登录 Session 对应的单条 SSE 连接。 */
export interface RealtimeClient {
  start(): void;
  stop(): void;
}

/**
 * 创建支持 Token Header、事件 ID 和自动重连的 SSE 客户端。
 *
 * <p>服务端没有事件回放存储，因此首次连接不触发刷新，后续每次成功重连都要求调用方重新读取完整状态。
 *
 * @param options 当前应用的 API 根地址、Session Token 和状态回调
 * @returns 由登录和退出操作显式控制的实时客户端
 */
export function createRealtimeClient(options: RealtimeClientOptions): RealtimeClient {
  let source: EventSourceClient | null = null;
  let connected = false;
  let stopped = true;

  function stop(): void {
    stopped = true;
    source?.close();
    source = null;
    connected = false;
  }

  function start(): void {
    if (source || !options.token()) {
      return;
    }
    stopped = false;
    const token = options.token();
    if (!token?.tokenName || !token.tokenValue) {
      return;
    }
    const baseUrl = options.baseUrl.replace(/\/$/, "");
    const requestFetch = options.fetch ?? globalThis.fetch.bind(globalThis);
    source = createEventSource({
      url: `${baseUrl}/realtime/sse`,
      headers: { [token.tokenName]: token.tokenValue },
      fetch: async (url, init) => {
        const response = await requestFetch(url, init as RequestInit);
        if (response.status === 401) {
          stopped = true;
          options.onUnauthorized();
          return new Response(null, { status: 204 });
        }
        const contentType = response.headers.get("Content-Type") ?? "";
        if (!response.ok || !contentType.toLowerCase().includes("text/event-stream")) {
          throw new Error(`SSE 响应无效: HTTP ${response.status}`);
        }
        return response;
      },
      onConnect: () => {
        if (stopped) {
          source?.close();
          return;
        }
        if (connected) {
          options.onReconnect();
        }
        connected = true;
      },
      onMessage: ({ event, data, id }) => {
        if (event) {
          options.onEvent({ event, data, id: id ?? null });
        }
      }
    });
  }

  return { start, stop };
}
