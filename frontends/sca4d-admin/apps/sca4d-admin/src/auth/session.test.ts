import type { CurrentSession, SessionToken } from '@sca4d/admin';

import { describe, expect, it } from 'vitest';

import {
  currentSessionHomePath,
  currentSessionUser,
  millisecondsUntilTokenRefresh,
  selectAuthenticationToken,
} from './session';

const JWT_TOKENS: SessionToken = {
  accessToken: 'access-token',
  accessTokenExpiresAt: '2026-07-30T01:10:00.000Z',
  sessionExpiresAt: '2026-07-30T02:00:00.000Z',
  sessionId: 'session-1',
  sessionToken: 'session-token',
  tokenMode: 'JWT',
  tokenType: 'Bearer',
};

describe('session Token 契约', () => {
  it('普通请求使用 access Token，刷新和注销使用 JWT Session Token', () => {
    expect(selectAuthenticationToken(JWT_TOKENS, 'access')).toBe(
      'access-token',
    );
    expect(selectAuthenticationToken(JWT_TOKENS, 'session')).toBe(
      'session-token',
    );
  });

  it('session 模式的 Session 操作回退到 access Token', () => {
    expect(
      selectAuthenticationToken(
        { ...JWT_TOKENS, sessionToken: null, tokenMode: 'SESSION' },
        'session',
      ),
    ).toBe('access-token');
  });

  it('在 access Token 过期前 30 秒安排 JWT 刷新', () => {
    expect(
      millisecondsUntilTokenRefresh(
        JWT_TOKENS,
        Date.parse('2026-07-30T01:00:00.000Z'),
      ),
    ).toBe(570_000);
  });

  it('不会在 backing Session 过期之后安排刷新', () => {
    expect(
      millisecondsUntilTokenRefresh(
        {
          ...JWT_TOKENS,
          accessTokenExpiresAt: '2026-07-30T02:10:00.000Z',
          sessionExpiresAt: '2026-07-30T02:00:00.000Z',
        },
        Date.parse('2026-07-30T01:00:00.000Z'),
      ),
    ).toBeNull();
  });

  it('使用个人资料显示名称和本地化账号说明生成 Vben 用户信息', () => {
    const session: CurrentSession = {
      account: { accountId: '9007199254740993', realmKey: 'platform' },
      expiresAt: '2026-07-30T02:00:00.000Z',
      issuedAt: '2026-07-30T01:00:00.000Z',
      permissions: ['platform:accounts:read'],
      sessionId: 'session-1',
      subject: { subjectId: '9007199254740995', type: 'ACCOUNT' },
    };

    expect(
      currentSessionUser(
        session,
        {
          avatarFileId: null,
          displayName: '示例管理员',
          email: null,
          phoneNumber: null,
          principalId: '41',
          updatedAt: '2026-07-30T01:00:00.000Z',
        },
        {
          accountDescription: '账号 9007199254740993',
          fallbackName: '账号 9007199254740993',
          realmDescription: '平台账号',
        },
      ),
    ).toMatchObject({
      accountDescription: '账号 9007199254740993',
      desc: '平台账号',
      homePath: '/sca4d-server/accounts',
      realName: '示例管理员',
      userId: '9007199254740993',
      username: '示例管理员',
    });
  });

  it('按账号、角色和个人资料顺序选择首个业务页面', () => {
    expect(currentSessionHomePath(['platform:accounts:read'])).toBe(
      '/sca4d-server/accounts',
    );
    expect(currentSessionHomePath(['platform:roles:manage'])).toBe(
      '/sca4d-server/roles',
    );
    expect(currentSessionHomePath([])).toBe('/profile');
  });
});
