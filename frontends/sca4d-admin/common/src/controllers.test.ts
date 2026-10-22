import type { HttpRequest, HttpResponse, SessionToken } from './ports';

import { describe, expect, it, vi } from 'vitest';

import { createAdminApplication } from './application';
import { AUTHENTICATION_SESSION_CHANGED_EVENT } from './realtime-events';

const NOOP_OBJECT_TRANSFERS = {
  transfer: async (): Promise<void> => undefined,
};

describe('admin controllers', () => {
  it('reads account and role details with exact string identifiers', async () => {
    const account = {
      avatarFileId: null,
      createdAt: '2026-08-02T00:00:00Z',
      displayName: 'Administrator',
      email: null,
      id: '9007199254740993',
      phoneNumber: null,
      protectedRoot: true,
      status: 'ACTIVE',
      updatedAt: '2026-08-02T00:00:00Z',
      username: 'admin',
    } as const;
    const role = {
      builtIn: true,
      code: 'ROOT',
      createdAt: '2026-08-02T00:00:00Z',
      id: '9007199254740994',
      name: 'Root',
      status: 'ACTIVE',
      updatedAt: '2026-08-02T00:00:00Z',
    } as const;
    const requests: HttpRequest[] = [];
    const app = createAdminApplication({
      http: {
        async request(request) {
          requests.push(request);
          return requests.length === 1 ? ok(account) : ok(role);
        },
      },
      objectTransfers: NOOP_OBJECT_TRANSFERS,
      tokens: {
        read: () => null,
        write: () => undefined,
        clear: () => undefined,
      },
      languages: { read: () => 'zh-CN', write: () => undefined },
      realtime: { subscribe: () => () => undefined },
    });

    await expect(app.accounts.require(account.id)).resolves.toEqual(account);
    await expect(app.roles.require(role.id)).resolves.toEqual(role);
    expect(requests.map((request) => request.path)).toEqual([
      `/platform/accounts/${account.id}`,
      `/platform/roles/${role.id}`,
    ]);
  });

  it('completes TOTP login and derives capabilities outside Vue', async () => {
    const requests: HttpRequest[] = [];
    let token: null | SessionToken = null;
    const responses: HttpResponse[] = [
      ok({
        authentication: {
          attemptId: 'attempt-1',
          status: 'FACTOR_REQUIRED',
          remainingFactorCount: 1,
          availableMethods: ['AUTHENTICATION/TOTP'],
          expiresAt: '2026-01-01T00:00:00Z',
        },
        session: null,
      }),
      ok({
        authentication: {
          attemptId: 'attempt-1',
          status: 'SATISFIED',
          remainingFactorCount: 0,
          availableMethods: [],
          expiresAt: '2026-01-01T00:00:00Z',
        },
        session: {
          tokenMode: 'SESSION',
          tokenType: 'Bearer',
          accessToken: 'token',
          accessTokenExpiresAt: '2026-01-01T00:00:00Z',
          sessionToken: null,
          sessionId: 'session',
          sessionExpiresAt: '2026-01-01T00:00:00Z',
        },
      }),
      ok({
        sessionId: 'session',
        issuedAt: '2025-01-01T00:00:00Z',
        expiresAt: '2026-01-01T00:00:00Z',
        subject: { subjectId: '1', type: 'HUMAN' },
        account: { accountId: '2', realmKey: 'platform' },
        permissions: ['PLATFORM:ACCOUNTS:MANAGE'],
      }),
    ];
    const app = createAdminApplication({
      http: {
        async request(request) {
          requests.push(request);
          return responses.shift() ?? { status: 500, headers: {} };
        },
      },
      objectTransfers: NOOP_OBJECT_TRANSFERS,
      tokens: {
        read: () => token,
        write: (value) => {
          token = value;
        },
        clear: () => {
          token = null;
        },
      },
      languages: { read: () => 'zh-CN', write: () => undefined },
      realtime: { subscribe: () => () => undefined },
    });

    await app.session.login('admin', 'secret');
    expect(requests[0]?.body).toEqual({
      identifier: 'admin',
      password: 'secret',
    });
    expect(app.session.state.phase).toBe('totp');
    await app.session.submitTotp('123456');

    expect(app.session.state.phase).toBe('authenticated');
    expect(app.session.state.capabilities.accountsManage).toBe(true);
    expect(app.session.state.capabilities.accountsRead).toBe(true);
    expect(requests[2]?.headers['Accept-Language']).toBe('zh-CN');
    expect(requests[2]?.headers.Authorization).toBe('Bearer token');
  });

  it('keeps 64-bit account and role identifiers exact in URLs and request bodies', async () => {
    const accountId = '2081571083525292032';
    const roleId = '2081571083542069248';
    const requests: HttpRequest[] = [];
    const role = {
      id: roleId,
      code: 'DEFAULT',
      name: 'Default',
      status: 'ACTIVE',
      builtIn: true,
      createdAt: '2026-07-27T00:00:00Z',
      updatedAt: '2026-07-27T00:00:00Z',
    } as const;
    const responses: HttpResponse[] = [
      ok([role]),
      ok({ accountId, roleIds: [roleId] }, { etag: '"1"' }),
      ok({ accountId, roleIds: [roleId] }, { etag: '"2"' }),
    ];
    const app = createAdminApplication({
      http: {
        async request(request) {
          requests.push(request);
          return responses.shift() ?? { status: 500, headers: {} };
        },
      },
      objectTransfers: NOOP_OBJECT_TRANSFERS,
      tokens: {
        read: () => null,
        write: () => undefined,
        clear: () => undefined,
      },
      languages: { read: () => 'zh-CN', write: () => undefined },
      realtime: { subscribe: () => () => undefined },
    });

    await app.accounts.loadRoles(accountId);
    await app.accounts.replaceRoles([roleId]);

    expect(requests[1]?.path).toBe(`/platform/accounts/${accountId}/roles`);
    expect(requests[2]?.path).toBe(`/platform/accounts/${accountId}/roles`);
    expect(requests[2]?.body).toEqual({ roleIds: [roleId] });
    expect(requests[2]?.headers['If-Match']).toBe('"1"');
  });

  it('creates roles with complete localized fields', async () => {
    const requests: HttpRequest[] = [];
    const role = {
      id: '2081571083542069248',
      code: 'OPERATIONS',
      name: '运营',
      status: 'ACTIVE',
      builtIn: false,
      createdAt: '2026-08-02T00:00:00Z',
      updatedAt: '2026-08-02T00:00:00Z',
    } as const;
    const responses: HttpResponse[] = [ok(role), ok([role])];
    const app = createAdminApplication({
      http: {
        async request(request) {
          requests.push(request);
          return responses.shift() ?? { status: 500, headers: {} };
        },
      },
      objectTransfers: NOOP_OBJECT_TRANSFERS,
      tokens: {
        read: () => null,
        write: () => undefined,
        clear: () => undefined,
      },
      languages: { read: () => 'zh-CN', write: () => undefined },
      realtime: { subscribe: () => () => undefined },
    });
    const i18n = {
      'zh-CN': { name: '运营' },
      'en-US': { name: 'Operations' },
    };

    await app.roles.create(role.code, i18n);

    expect(requests[0]).toMatchObject({
      method: 'POST',
      path: '/platform/roles',
      body: { code: 'OPERATIONS', i18n, permissionCodes: [] },
    });
  });

  it('uses the shared role ETag for i18n, status, permissions, and deletion', async () => {
    const roleId = '2081571083542069248';
    const requests: HttpRequest[] = [];
    const role = {
      id: roleId,
      code: 'OPERATIONS',
      name: '运营',
      status: 'ACTIVE',
      builtIn: false,
      createdAt: '2026-08-02T00:00:00Z',
      updatedAt: '2026-08-02T00:00:00Z',
    } as const;
    const localeCatalog = {
      defaultLocale: 'zh-CN',
      supportedLocales: ['zh-CN', 'en-US'],
    } as const;
    const permissionCatalog = { items: [], total: 0, tree: [] } as const;
    const initialI18n = {
      roleId,
      i18n: {
        'zh-CN': { name: '运营' },
        'en-US': { name: 'Operations' },
      },
    } as const;
    const updatedI18n = {
      roleId,
      i18n: {
        'zh-CN': { name: '运营管理' },
        'en-US': { name: 'Operations management' },
      },
    } as const;
    const responses: HttpResponse[] = [
      ok(initialI18n, { etag: '"1"' }),
      ok(localeCatalog),
      ok(updatedI18n, { etag: '"2"' }),
      ok([{ ...role, name: '运营管理' }]),
      ok(role, { etag: '"2"' }),
      ok({ ...role, status: 'DISABLED' }, { etag: '"3"' }),
      ok([{ ...role, status: 'DISABLED' }]),
      ok({ roleId, permissionCodes: [] }, { etag: '"3"' }),
      ok(permissionCatalog),
      ok({ roleId, permissionCodes: ['account:read'] }, { etag: '"4"' }),
      ok([role]),
      ok(role, { etag: '"4"' }),
      ok(null),
      ok([]),
    ];
    const app = createAdminApplication({
      http: {
        async request(request) {
          requests.push(request);
          return responses.shift() ?? { status: 500, headers: {} };
        },
      },
      objectTransfers: NOOP_OBJECT_TRANSFERS,
      tokens: {
        read: () => sessionToken(),
        write: () => undefined,
        clear: () => undefined,
      },
      languages: { read: () => 'zh-CN', write: () => undefined },
      realtime: { subscribe: () => () => undefined },
    });

    await app.roles.loadI18n(roleId);
    await app.roles.replaceI18n(updatedI18n.i18n);
    await app.roles.changeStatus(role, 'DISABLED');
    await app.roles.loadPermissions(roleId);
    await app.roles.replacePermissions(['account:read']);
    await app.roles.delete(role);

    expect(requests[2]).toMatchObject({
      method: 'PUT',
      path: `/platform/roles/${roleId}/i18n`,
      body: { i18n: updatedI18n.i18n },
    });
    expect(requests[2]?.headers['If-Match']).toBe('"1"');
    expect(requests[5]).toMatchObject({
      method: 'PUT',
      path: `/platform/roles/${roleId}/status`,
      body: { status: 'DISABLED' },
    });
    expect(requests[5]?.headers['If-Match']).toBe('"2"');
    expect(requests[9]?.headers['If-Match']).toBe('"3"');
    expect(requests[12]).toMatchObject({
      method: 'DELETE',
      path: `/platform/roles/${roleId}`,
    });
    expect(requests[12]?.headers['If-Match']).toBe('"4"');
    expect(app.roles.state.roleEtag).toBeNull();
    expect(app.roles.state.localeCatalog).toEqual(localeCatalog);
  });

  it('refreshes revoked capabilities without replacing the realtime subscription', async () => {
    const requests: HttpRequest[] = [];
    const realtime = {
      emit: (event: string): void => {
        void event;
      },
    };
    let subscriptionCount = 0;
    const sessions = [
      {
        sessionId: 'session',
        issuedAt: '2025-01-01T00:00:00Z',
        expiresAt: '2026-01-01T00:00:00Z',
        subject: { subjectId: '1', type: 'HUMAN' },
        account: { accountId: '2', realmKey: 'platform' },
        permissions: ['PLATFORM:ACCOUNTS:MANAGE'],
      },
      {
        sessionId: 'session',
        issuedAt: '2025-01-01T00:00:00Z',
        expiresAt: '2026-01-01T00:00:00Z',
        subject: { subjectId: '1', type: 'HUMAN' },
        account: { accountId: '2', realmKey: 'platform' },
        permissions: [],
      },
    ];
    let token: null | SessionToken = sessionToken();
    const responses: HttpResponse[] = [
      ok(sessions[0]),
      ok(sessionToken()),
      ok(sessions[1]),
    ];
    const app = createAdminApplication({
      http: {
        request: async (request) => {
          requests.push(request);
          return responses.shift() ?? { status: 500, headers: {} };
        },
      },
      objectTransfers: NOOP_OBJECT_TRANSFERS,
      tokens: {
        read: () => token,
        write: (value) => {
          token = value;
        },
        clear: () => {
          token = null;
        },
      },
      languages: { read: () => 'zh-CN', write: () => undefined },
      realtime: {
        subscribe: (_token, onEvent) => {
          subscriptionCount += 1;
          realtime.emit = onEvent;
          return () => undefined;
        },
      },
    });

    await app.session.initialize();
    expect(app.session.state.capabilities.accountsManage).toBe(true);
    expect(subscriptionCount).toBe(1);

    realtime.emit(AUTHENTICATION_SESSION_CHANGED_EVENT);
    realtime.emit(AUTHENTICATION_SESSION_CHANGED_EVENT);
    await vi.waitFor(() =>
      expect(app.session.state.capabilities.accountsManage).toBe(false),
    );
    expect(subscriptionCount).toBe(1);
    expect(
      requests.filter((request) => request.path === '/sessions/current/tokens'),
    ).toHaveLength(1);
    expect(requests[1]?.headers.Authorization).toBe('Bearer token');
  });

  it('keeps an authenticated session when an API request returns an unmarked 401', async () => {
    const responses: HttpResponse[] = [
      ok({
        sessionId: 'session',
        issuedAt: '2025-01-01T00:00:00Z',
        expiresAt: '2026-01-01T00:00:00Z',
        subject: { subjectId: '1', type: 'HUMAN' },
        account: { accountId: '2', realmKey: 'platform' },
        permissions: ['PLATFORM:ACCOUNTS:READ'],
      }),
      {
        status: 401,
        headers: {},
        body: { code: '1001', message: 'Session expired', data: null },
      },
    ];
    const { app, currentToken } =
      applicationWithAuthenticatedSession(responses);

    await app.session.initialize();
    expect(app.session.state.phase).toBe('authenticated');
    await expect(app.accounts.load()).rejects.toMatchObject({
      kind: 'authentication',
    });

    expect(currentToken()).not.toBeNull();
    expect(app.session.state.phase).toBe('authenticated');
  });

  it('clears an authenticated session when an explicitly requested refresh fails', async () => {
    const responses: HttpResponse[] = [
      ok({
        sessionId: 'session',
        issuedAt: '2025-01-01T00:00:00Z',
        expiresAt: '2026-01-01T00:00:00Z',
        subject: { subjectId: '1', type: 'HUMAN' },
        account: { accountId: '2', realmKey: 'platform' },
        permissions: ['PLATFORM:ACCOUNTS:READ'],
      }),
      {
        status: 401,
        headers: { 'X-Client-State-Refresh': 'authentication.session' },
        body: { code: '1001', message: 'Session expired', data: null },
      },
      {
        status: 401,
        headers: {},
        body: { code: '1001', message: 'Session expired', data: null },
      },
    ];
    const { app, currentToken } =
      applicationWithAuthenticatedSession(responses);

    await app.session.initialize();
    await expect(app.accounts.load()).rejects.toMatchObject({
      kind: 'authentication',
    });

    await vi.waitFor(() => expect(currentToken()).toBeNull());
    expect(app.session.state.phase).toBe('anonymous');
  });

  it('does not refresh JWTs when the backing session is about to expire', async () => {
    const timeout = vi.spyOn(globalThis, 'setTimeout');
    const expiresAt = new Date(Date.now() + 20_000).toISOString();
    let token: null | SessionToken = {
      tokenMode: 'JWT',
      tokenType: 'Bearer',
      accessToken: 'access-token',
      accessTokenExpiresAt: expiresAt,
      sessionToken: 'session-token',
      sessionId: 'session',
      sessionExpiresAt: expiresAt,
    };
    const app = createAdminApplication({
      http: {
        request: async () =>
          ok({
            sessionId: 'session',
            issuedAt: '2025-01-01T00:00:00Z',
            expiresAt,
            subject: { subjectId: '1', type: 'HUMAN' },
            account: { accountId: '2', realmKey: 'platform' },
            permissions: [],
          }),
      },
      objectTransfers: NOOP_OBJECT_TRANSFERS,
      tokens: {
        read: () => token,
        write: (value) => {
          token = value;
        },
        clear: () => {
          token = null;
        },
      },
      languages: { read: () => 'zh-CN', write: () => undefined },
      realtime: { subscribe: () => () => undefined },
    });

    await app.session.initialize();

    expect(timeout).not.toHaveBeenCalled();
    timeout.mockRestore();
  });
});

function applicationWithAuthenticatedSession(responses: HttpResponse[]) {
  let token: null | SessionToken = sessionToken();
  const app = createAdminApplication({
    http: {
      request: async () => responses.shift() ?? { status: 500, headers: {} },
    },
    objectTransfers: NOOP_OBJECT_TRANSFERS,
    tokens: {
      read: () => token,
      write: (value) => {
        token = value;
      },
      clear: () => {
        token = null;
      },
    },
    languages: { read: () => 'zh-CN', write: () => undefined },
    realtime: { subscribe: () => () => undefined },
  });
  return { app, currentToken: () => token };
}

function ok(
  data: unknown,
  headers: Readonly<Record<string, string>> = {},
): HttpResponse {
  return { status: 200, headers, body: { code: '0', data } };
}

function sessionToken(): SessionToken {
  return {
    tokenMode: 'SESSION',
    tokenType: 'Bearer',
    accessToken: 'token',
    accessTokenExpiresAt: '2099-01-01T00:00:00Z',
    sessionToken: null,
    sessionId: 'session',
    sessionExpiresAt: '2099-01-01T00:00:00Z',
  };
}
