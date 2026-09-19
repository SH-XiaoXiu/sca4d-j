import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createEmailCodeChallengeApi,
  createEmailCodeLoginAttemptApi,
  createLoginAttemptApi,
  createPendingEmailCodeChallengeApi,
  getCurrentProfileApi,
  getCurrentSessionApi,
  logoutSessionApi,
  refreshSessionTokensApi,
  submitLoginEmailCodeApi,
  submitLoginPasswordApi,
  submitLoginTotpApi,
} from './auth';

const clients = vi.hoisted(() => ({
  baseDelete: vi.fn(),
  basePost: vi.fn(),
  requestGet: vi.fn(),
}));

vi.mock('#/api/request', () => ({
  baseRequestClient: {
    delete: clients.baseDelete,
    post: clients.basePost,
  },
  requestClient: {
    get: clients.requestGet,
  },
}));

describe('认证 HTTP 契约', () => {
  beforeEach(() => vi.clearAllMocks());

  it('使用后端既有路由发送密码和 TOTP', async () => {
    await createLoginAttemptApi({
      identifier: 'admin@example.com',
      password: 'secret',
    });
    await submitLoginTotpApi('attempt/a', { code: '123456' });

    expect(clients.basePost).toHaveBeenNthCalledWith(1, '/login/attempts', {
      identifier: 'admin@example.com',
      password: 'secret',
    });
    expect(clients.basePost).toHaveBeenNthCalledWith(
      2,
      '/login/attempts/attempt%2Fa/totp/proofs',
      { code: '123456' },
    );
  });

  it('使用后端既有路由签发并提交 Email Code', async () => {
    await createEmailCodeChallengeApi({ email: 'admin@example.com' });
    await createEmailCodeLoginAttemptApi({
      challengeId: 'challenge-1',
      code: '123456',
    });
    await createPendingEmailCodeChallengeApi('attempt/a');
    await submitLoginEmailCodeApi('attempt/a', {
      challengeId: 'challenge-2',
      code: '654321',
    });
    await submitLoginPasswordApi('attempt/a', { password: 'secret' });

    expect(clients.basePost).toHaveBeenNthCalledWith(
      1,
      '/login/email-code/challenges',
      { email: 'admin@example.com' },
    );
    expect(clients.basePost).toHaveBeenNthCalledWith(
      2,
      '/login/email-code/proofs',
      { challengeId: 'challenge-1', code: '123456' },
    );
    expect(clients.basePost).toHaveBeenNthCalledWith(
      3,
      '/login/attempts/attempt%2Fa/email-code/challenges',
    );
    expect(clients.basePost).toHaveBeenNthCalledWith(
      4,
      '/login/attempts/attempt%2Fa/email-code/proofs',
      { challengeId: 'challenge-2', code: '654321' },
    );
    expect(clients.basePost).toHaveBeenNthCalledWith(
      5,
      '/login/attempts/attempt%2Fa/password/proofs',
      { password: 'secret' },
    );
  });

  it('读取当前 Session 和个人资料时使用普通认证客户端', async () => {
    await getCurrentSessionApi();
    await getCurrentProfileApi();
    expect(clients.requestGet).toHaveBeenCalledWith('/sessions/current');
    expect(clients.requestGet).toHaveBeenCalledWith('/me/profile');
  });

  it('刷新和注销都把 Session Token 放入 Bearer 请求头', async () => {
    await refreshSessionTokensApi('session-token');
    await logoutSessionApi('session-token');

    expect(clients.basePost).toHaveBeenCalledWith(
      '/sessions/current/tokens',
      undefined,
      { headers: { Authorization: 'Bearer session-token' } },
    );
    expect(clients.baseDelete).toHaveBeenCalledWith('/sessions/current', {
      headers: { Authorization: 'Bearer session-token' },
    });
  });
});
