import { beforeEach, describe, expect, it, vi } from 'vitest';

import { requestClient } from './request';

const { notifyAuthenticationSessionRefresh } = vi.hoisted(() => ({
  notifyAuthenticationSessionRefresh: vi.fn(),
}));

vi.mock('@vben/hooks', () => ({
  useAppConfig: () => ({ apiURL: 'http://localhost' }),
}));
vi.mock('@vben/preferences', () => ({
  preferences: { app: { locale: 'zh-CN' } },
}));
vi.mock('@vben/stores', () => ({
  useAccessStore: () => ({ accessToken: 'access-token' }),
}));
vi.mock('ant-design-vue', () => ({ message: { error: vi.fn() } }));
vi.mock('#/auth/client-state-refresh', () => ({
  notifyAuthenticationSessionRefresh,
}));

describe('sca4d-j 请求客户端状态刷新', () => {
  beforeEach(() => {
    notifyAuthenticationSessionRefresh.mockReset();
  });

  it('普通 401 不通知 Session 刷新', async () => {
    requestClient.instance.defaults.adapter = async (config) => {
      throw Object.assign(new Error('认证失败'), {
        config,
        response: {
          config,
          data: { code: '1001', message: '认证失败' },
          headers: {},
          status: 401,
          statusText: 'Unauthorized',
        },
      });
    };

    await expect(requestClient.get('/test')).rejects.toMatchObject({
      code: '1001',
    });
    expect(notifyAuthenticationSessionRefresh).not.toHaveBeenCalled();
  });

  it('失败响应携带 authentication.session 时通知 Session 刷新', async () => {
    requestClient.instance.defaults.adapter = async (config) => {
      throw Object.assign(new Error('请求失败'), {
        config,
        response: {
          config,
          data: { code: '1100', message: '请求失败' },
          headers: {
            'x-client-state-refresh': 'unknown,authentication.session',
          },
          status: 400,
          statusText: 'Bad Request',
        },
      });
    };

    await expect(requestClient.get('/test')).rejects.toMatchObject({
      code: '1100',
    });
    expect(notifyAuthenticationSessionRefresh).toHaveBeenCalledOnce();
  });
});
