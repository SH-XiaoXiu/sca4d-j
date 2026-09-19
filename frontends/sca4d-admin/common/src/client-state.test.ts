import { describe, expect, it, vi } from 'vitest';

import { AdminApiClient } from './api-client';
import { requestsAuthenticationSessionRefresh } from './client-state';

describe('客户端状态刷新响应头', () => {
  it('识别 authentication.session 并忽略未知目标', () => {
    expect(
      requestsAuthenticationSessionRefresh(
        'unknown.target, authentication.session, another.target',
      ),
    ).toBe(true);
    expect(requestsAuthenticationSessionRefresh('unknown.target')).toBe(false);
    expect(requestsAuthenticationSessionRefresh(undefined)).toBe(false);
  });

  it('普通 401 不刷新或清除当前 Session', async () => {
    const clear = vi.fn();
    const refresh = vi.fn();
    const api = new AdminApiClient(
      {
        request: async () => ({
          body: { code: '1001', data: null, message: '认证失败' },
          headers: {},
          status: 401,
        }),
      },
      {
        clear,
        read: () => ({
          accessToken: 'token',
          accessTokenExpiresAt: '2026-08-25T09:00:00Z',
          sessionExpiresAt: '2026-08-25T10:00:00Z',
          sessionId: 'session',
          sessionToken: null,
          tokenMode: 'SESSION',
          tokenType: 'Bearer',
        }),
        write: vi.fn(),
      },
      { read: () => 'zh-CN', write: vi.fn() },
      refresh,
    );

    await expect(api.get('/test')).rejects.toMatchObject({
      kind: 'authentication',
    });

    expect(refresh).not.toHaveBeenCalled();
    expect(clear).not.toHaveBeenCalled();
  });

  it('失败响应携带 authentication.session 时通知 Session 刷新', async () => {
    const refresh = vi.fn();
    const api = new AdminApiClient(
      {
        request: async () => ({
          body: { code: '1001', data: null, message: 'Session 已失效' },
          headers: {
            'X-Client-State-Refresh': 'unknown.target,authentication.session',
          },
          status: 401,
        }),
      },
      { clear: vi.fn(), read: () => null, write: vi.fn() },
      { read: () => 'zh-CN', write: vi.fn() },
      refresh,
    );

    await expect(api.get('/test')).rejects.toMatchObject({
      kind: 'authentication',
    });

    expect(refresh).toHaveBeenCalledOnce();
  });
});
