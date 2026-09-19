import type { FetchEventSourceInit } from '@microsoft/fetch-event-source';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { subscribeSessionEvents } from './session-events';

const { fetchEventSource } = vi.hoisted(() => ({
  fetchEventSource: vi.fn(
    (_url: RequestInfo, _options: FetchEventSourceInit) =>
      new Promise<void>(() => {}),
  ),
}));

vi.mock('@microsoft/fetch-event-source', () => ({
  EventStreamContentType: 'text/event-stream',
  fetchEventSource,
}));

vi.mock('@vben/hooks', () => ({
  useAppConfig: () => ({ apiURL: '/api' }),
}));

describe('session SSE 契约', () => {
  beforeEach(() => {
    fetchEventSource.mockClear();
  });

  it('使用 Session Token 连接后端并转发身份变化事件', () => {
    const onSessionChanged = vi.fn();
    const stop = subscribeSessionEvents({
      onAuthenticationFailure: vi.fn(),
      onSessionChanged,
      sessionToken: 'session-token',
    });

    const call = fetchEventSource.mock.calls[0];
    expect(call).toBeDefined();
    if (!call) throw new Error('Session SSE 没有创建连接');
    const [url, options] = call;
    expect(url).toBe('/api/events');
    expect(options.headers).toEqual({
      Authorization: 'Bearer session-token',
    });

    options.onmessage?.({
      data: '{}',
      event: 'authentication.session.changed',
      id: '',
      retry: undefined,
    });
    expect(onSessionChanged).toHaveBeenCalledOnce();

    stop();
    expect(options.signal?.aborted).toBe(true);
  });

  it('SSE 握手响应要求刷新 Session 时转发通知', async () => {
    const onSessionChanged = vi.fn();
    subscribeSessionEvents({
      onAuthenticationFailure: vi.fn(),
      onSessionChanged,
      sessionToken: 'session-token',
    });
    const call = fetchEventSource.mock.calls[0];
    if (!call) throw new Error('Session SSE 没有创建连接');

    await call[1].onopen?.(
      new Response(null, {
        headers: {
          'Content-Type': 'text/event-stream',
          'X-Client-State-Refresh': 'unknown.target,authentication.session',
        },
        status: 200,
      }),
    );

    expect(onSessionChanged).toHaveBeenCalledOnce();
  });
});
