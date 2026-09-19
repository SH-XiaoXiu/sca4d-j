import type { HttpRequest, HttpResponse } from './ports';

import { describe, expect, it } from 'vitest';

import { AdminApiClient } from './api-client';
import { PreferencesController } from './preferences-controller';

describe('preferences controller', () => {
  it('读取 ETag 并按该版本完整替换和恢复偏好', async () => {
    const requests: HttpRequest[] = [];
    const responses: HttpResponse[] = [
      ok(preference({ theme: 'light' }), '"7"'),
      ok(preference({ theme: 'dark' }), '"8"'),
      ok(preference({ theme: 'light' }), '"9"'),
    ];
    const controller = new PreferencesController(api(requests, responses));

    const loaded = await controller.require('admin.ui-preferences');
    const replaced = await controller.replace(
      'admin.ui-preferences',
      { theme: 'dark' },
      loaded.etag,
    );
    const reset = await controller.reset('admin.ui-preferences', replaced.etag);

    expect(requests.map((request) => request.method)).toEqual([
      'GET',
      'PUT',
      'DELETE',
    ]);
    expect(requests[1]?.body).toEqual({ value: { theme: 'dark' } });
    expect(requests[1]?.headers['If-Match']).toBe('"7"');
    expect(requests[2]?.headers['If-Match']).toBe('"8"');
    expect(reset.etag).toBe('"9"');
  });
});

/** 创建不依赖浏览器运行时的管理 API Client。 */
function api(requests: HttpRequest[], responses: HttpResponse[]) {
  return new AdminApiClient(
    {
      async request(request) {
        requests.push(request);
        return responses.shift() ?? { headers: {}, status: 500 };
      },
    },
    { clear: () => undefined, read: () => null, write: () => undefined },
    { read: () => 'zh-CN', write: () => undefined },
    () => undefined,
  );
}

/** 创建一个已注册 UI 偏好的响应值。 */
function preference(value: { readonly theme: string }) {
  return {
    key: 'admin.ui-preferences',
    schemaVersion: 1,
    stored: true,
    value,
  };
}

/** 创建携带强 ETag 的统一 JSON 成功响应。 */
function ok(data: unknown, etag: string): HttpResponse {
  return {
    body: { code: '0', data },
    headers: { etag },
    status: 200,
  };
}
