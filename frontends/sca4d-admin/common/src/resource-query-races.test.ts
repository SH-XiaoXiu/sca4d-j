import type { HttpRequest, HttpResponse } from './ports';

import { describe, expect, it } from 'vitest';

import { createAdminApplication } from './application';

/** 使用可控完成顺序验证公开 Controller 的读取和生命周期结果。 */
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
}

/** 创建没有 Vue 或 DOM 依赖的测试宿主。 */
function application(request: (input: HttpRequest) => Promise<HttpResponse>) {
  return createAdminApplication({
    http: { request },
    languages: { read: () => 'zh-CN', write: () => {} },
    tokens: { read: () => null, write: () => {}, clear: () => {} },
    objectTransfers: { transfer: async () => {} },
    realtime: { subscribe: () => () => {} },
  });
}

/** 使用正式响应外壳，保留资源的读取版本。 */
function ok(data: unknown, etag = '"1"'): HttpResponse {
  return { status: 200, headers: { etag }, body: { code: '0', data } };
}

/** 用完整账号输入验证大 ID 保持字符串。 */
function account(id: string, name: string) {
  return {
    id,
    username: name,
    displayName: name,
    email: null,
    phoneNumber: null,
    avatarFileId: null,
    protectedRoot: false,
    status: 'ACTIVE',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
  };
}

describe('resource query races', () => {
  it('迟到的旧筛选结果不会覆盖最新页和查询条件', async () => {
    const first = deferred<HttpResponse>();
    const second = deferred<HttpResponse>();
    let count = 0;
    const app = application(async () =>
      ++count === 1 ? first.promise : second.promise,
    );
    const old = app.accounts.load({ search: 'old' });
    const current = app.accounts.load({ search: 'current' });
    second.resolve(
      ok({
        items: [account('9007199254740993', 'current')],
        page: 1,
        size: 20,
        total: '1',
      }),
    );
    await current;
    first.resolve(
      ok({
        items: [account('9007199254740994', 'old')],
        page: 1,
        size: 20,
        total: '1',
      }),
    );
    await old;
    expect(app.accounts.state.filters.search).toBe('current');
    expect(app.accounts.state.page.items[0]?.id).toBe('9007199254740993');
  });

  it('Session 状态清除后旧请求不能重新填回资源', async () => {
    const response = deferred<HttpResponse>();
    const app = application(async () => response.promise);
    const pending = app.accounts.load();
    app.accounts.resetState();
    response.resolve(
      ok({ items: [account('1', 'old')], page: 1, size: 20, total: '1' }),
    );
    await pending;
    expect(app.accounts.state.phase).toBe('idle');
    expect(app.accounts.state.page.items).toEqual([]);
  });

  it('关闭详情不会取消另一份列表读取', async () => {
    const response = deferred<HttpResponse>();
    const app = application(async () => response.promise);
    const pending = app.emailSenders.load();
    app.emailSenders.clearDetail();
    response.resolve(ok([]));
    await pending;
    expect(app.emailSenders.state.phase).toBe('ready');
  });

  it('刷新失败保留上次读取的数据并发布错误', async () => {
    let count = 0;
    const app = application(async () =>
      ++count === 1
        ? ok({ items: [account('1', 'loaded')], page: 1, size: 20, total: '1' })
        : {
            status: 503,
            headers: {},
            body: { code: 'TEST_UNAVAILABLE', message: 'Unavailable' },
          },
    );
    await app.accounts.load();
    await expect(app.accounts.load()).rejects.toMatchObject({
      kind: 'unavailable',
    });
    expect(app.accounts.state.page.items[0]?.username).toBe('loaded');
    expect(app.accounts.state.error?.kind).toBe('unavailable');
  });
});

describe('role resource versions', () => {
  it('读取名称的新版本不能替换权限草稿实际读取的旧版本', async () => {
    const requests: HttpRequest[] = [];
    const role = {
      id: '1',
      code: 'OPERATOR',
      name: 'Operator',
      builtIn: false,
      status: 'ACTIVE',
      createdAt: '2026-10-01T00:00:00Z',
      updatedAt: '2026-10-01T00:00:00Z',
    };
    const app = application(async (request) => {
      requests.push(request);
      if (request.path === '/platform/roles') return ok([role]);
      if (request.path === '/system/permissions')
        return ok({ total: 0, tree: [] });
      if (request.path === '/system/locales')
        return ok({
          defaultLocale: 'zh-CN',
          supportedLocales: ['zh-CN', 'en-US'],
        });
      if (request.path.endsWith('/i18n'))
        return ok(
          { roleId: '1', i18n: { 'zh-CN': { name: 'Operator' } } },
          '"2"',
        );
      if (request.method === 'PUT')
        return {
          status: 412,
          headers: {},
          body: { code: 'TEST_CONFLICT', message: 'Changed' },
        };
      return ok({ roleId: '1', permissionCodes: [] }, '"1"');
    });
    await app.roles.load();
    await app.roles.loadPermissions('1');
    await app.roles.loadI18n('1');
    await expect(app.roles.replacePermissions([])).rejects.toMatchObject({
      kind: 'versionConflict',
    });
    expect(
      requests.find((request) => request.method === 'PUT')?.headers['If-Match'],
    ).toBe('"1"');
  });
});

describe('saved changes with failed refresh', () => {
  it('保存成功后的读取失败明确标记为 refresh，不再次发送创建请求', async () => {
    const requests: HttpRequest[] = [];
    const app = application(async (request) => {
      requests.push(request);
      if (request.method === 'POST') return ok(account('1', 'created'));
      return {
        status: 503,
        headers: {},
        body: { code: 'TEST_UNAVAILABLE', message: 'Unavailable' },
      };
    });
    await expect(
      app.accounts.create({
        username: 'created',
        initialPassword: 'test-only-password',
        displayName: 'Created',
        email: null,
        phoneNumber: null,
      }),
    ).rejects.toMatchObject({ kind: 'refresh' });
    expect(
      requests.filter((request) => request.method === 'POST'),
    ).toHaveLength(1);
  });
});
