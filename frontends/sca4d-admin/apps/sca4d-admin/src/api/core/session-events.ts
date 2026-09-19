import { useAppConfig } from '@vben/hooks';

import {
  EventStreamContentType,
  fetchEventSource,
} from '@microsoft/fetch-event-source';
import {
  AUTHENTICATION_SESSION_CHANGED_EVENT,
  CLIENT_STATE_REFRESH_HEADER,
  NOTIFICATION_INBOX_CHANGED_EVENT,
  requestsAuthenticationSessionRefresh,
} from '@sca4d/admin';

const RETRY_DELAY_MILLISECONDS = 1000;

/** 表示请求参数或响应格式无效，继续重连也无法恢复。 */
class FatalSessionEventsError extends Error {}

/** 表示服务端或网络暂时不可用，可以等待后重新连接。 */
class RetriableSessionEventsError extends Error {}

/** 建立 Session SSE 时需要的 Token 和事件回调。 */
export interface SessionEventsOptions {
  readonly onAuthenticationFailure: () => void;
  readonly onInboxChanged?: () => void;
  readonly onSessionChanged: () => void;
  readonly sessionToken: string;
}

/**
 * 使用 backing Session Token 订阅身份变化事件。
 *
 * 网络中断和服务端正常关闭会按固定间隔重连；401、403、其他 4xx 或非
 * SSE 响应会停止连接。返回函数会立即终止当前请求和后续重连。
 */
export function subscribeSessionEvents(
  options: SessionEventsOptions,
): () => void {
  const abort = new AbortController();
  const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
  const url = `${apiURL.replace(/\/$/, '')}/events`;

  void fetchEventSource(url, {
    headers: { Authorization: `Bearer ${options.sessionToken}` },
    openWhenHidden: true,
    signal: abort.signal,
    onclose: () => {
      if (!abort.signal.aborted) {
        throw new RetriableSessionEventsError('Session SSE 已关闭');
      }
    },
    onerror: (error) => {
      if (abort.signal.aborted || error instanceof FatalSessionEventsError) {
        throw error;
      }
      return RETRY_DELAY_MILLISECONDS;
    },
    onmessage: (message) => {
      if (message.event === AUTHENTICATION_SESSION_CHANGED_EVENT) {
        options.onSessionChanged();
      } else if (message.event === NOTIFICATION_INBOX_CHANGED_EVENT) {
        options.onInboxChanged?.();
      }
    },
    onopen: async (response) => {
      if (
        requestsAuthenticationSessionRefresh(
          response.headers.get(CLIENT_STATE_REFRESH_HEADER),
        )
      ) {
        options.onSessionChanged();
      }
      if (response.status === 401 || response.status === 403) {
        options.onAuthenticationFailure();
        throw new FatalSessionEventsError(
          `Session SSE 认证失败: ${response.status}`,
        );
      }
      if (response.status >= 400 && response.status < 500) {
        throw new FatalSessionEventsError(
          `Session SSE 请求无效: ${response.status}`,
        );
      }
      if (!response.ok) {
        throw new RetriableSessionEventsError(
          `Session SSE 暂时不可用: ${response.status}`,
        );
      }
      const contentType = response.headers.get('content-type');
      if (!contentType?.startsWith(EventStreamContentType)) {
        throw new FatalSessionEventsError('Session SSE 响应类型无效');
      }
    },
  }).catch(() => {
    // 连接终止由认证 Store 根据 Token 和 Session 状态决定是否重新建立。
  });

  return () => abort.abort();
}
