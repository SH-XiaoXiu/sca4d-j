import type { HttpRequest, HttpResponse } from './ports';

import { describe, expect, it } from 'vitest';

import { createAdminApplication } from './application';

/** 创建可观测 HTTP 请求的框架无关宿主。 */
function application(request: (input: HttpRequest) => Promise<HttpResponse>) {
  return createAdminApplication({
    http: { request },
    languages: { read: () => 'zh-CN', write: () => {} },
    tokens: { read: () => null, write: () => {}, clear: () => {} },
    objectTransfers: { transfer: async () => {} },
    realtime: { subscribe: () => () => {} },
  });
}

/** 正式通知响应外壳。 */
function ok(data: unknown): HttpResponse {
  return { status: 200, headers: {}, body: { code: '0', data } };
}

/** 构造不含个人数据的站内信。 */
function item(id: string) {
  return {
    id,
    version: '9007199254740993',
    createdAt: '2026-10-01T00:00:00Z',
    html: '<p>Test</p>',
    text: 'Test',
    title: 'Test',
    languageTag: 'zh-CN',
    notificationType: 'TEST',
    readAt: null,
  };
}

describe('notification controllers', () => {
  it('游标页按 ID 去重且请求始终保留十进制字符串', async () => {
    const requests: HttpRequest[] = [];
    const first = Array.from({ length: 20 }, (_, index) =>
      item(String(40 - index)),
    );
    const app = application(async (request) => {
      requests.push(request);
      return requests.length === 1
        ? ok({ messages: first, unreadCount: 21 })
        : ok({ messages: [item('21'), item('20')], unreadCount: 21 });
    });
    await app.inbox.load();
    await app.inbox.loadMore();
    expect(requests[1]?.query).toEqual({ beforeId: '21', limit: 20 });
    expect(app.inbox.state.messages).toHaveLength(21);
    expect(app.inbox.state.beforeId).toBeUndefined();
  });

  it('已读动作使用消息快照的 Version，随后读取真实未读数量', async () => {
    const requests: HttpRequest[] = [];
    const app = application(async (request) => {
      requests.push(request);
      return request.method === 'POST'
        ? { status: 204, headers: {} }
        : ok({ messages: [], unreadCount: 0 });
    });
    await app.inbox.markRead(item('9007199254740994'));
    expect(requests[0]?.path).toBe('/me/notifications/9007199254740994/read');
    expect(requests[0]?.body).toEqual({ version: '9007199254740993' });
    expect(app.inbox.state.unreadCount).toBe(0);
  });

  it('区域写入失败时保留已经保存的语言及其新版本', async () => {
    const initial = {
      languageTag: 'zh-CN',
      languageVersion: '1',
      regionCode: 'GLOBAL',
      regionVersion: '2',
      channels: {},
      channelsVersion: '3',
      definitions: {},
    };
    const requests: HttpRequest[] = [];
    const app = application(async (request) => {
      requests.push(request);
      if (request.path === '/system/locales')
        return ok({
          defaultLocale: 'zh-CN',
          supportedLocales: ['zh-CN', 'en-US'],
        });
      if (request.method === 'GET') return ok(initial);
      if (request.path.endsWith('/language'))
        return ok({ ...initial, languageTag: 'en-US', languageVersion: '4' });
      return {
        status: 412,
        headers: {},
        body: { code: 'TEST_CONFLICT', message: 'Changed' },
      };
    });
    await app.notificationSettings.load();
    await app.notificationSettings.replaceLanguage('en-US');
    await expect(
      app.notificationSettings.replaceRegion('OTHER'),
    ).rejects.toMatchObject({ kind: 'versionConflict' });
    expect(app.notificationSettings.state.settings?.languageTag).toBe('en-US');
    expect(app.notificationSettings.state.settings?.languageVersion).toBe('4');
    const writes = requests.filter((request) => request.method === 'PUT');
    expect(writes[0]?.headers['If-Match']).toBe('"1"');
    expect(writes[1]?.headers['If-Match']).toBe('"2"');
    expect(
      app.notificationSettings.state.localeCatalog?.supportedLocales,
    ).toEqual(['zh-CN', 'en-US']);
  });
});
