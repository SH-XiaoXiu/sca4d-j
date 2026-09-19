import type { HttpRequest, HttpResponse } from './ports';

import { describe, expect, it } from 'vitest';

import { createAdminApplication } from './application';

describe('system controllers', () => {
  it('manages dynamic dictionaries and preserves frozen aggregate selections', async () => {
    const requests: HttpRequest[] = [];
    const initial = dictionaries([]);
    const created = dictionaries([
      {
        codeDefined: false,
        enabled: true,
        key: 'business.priority',
        label: 'Priority',
        options: [],
        optionsEditable: true,
        sortOrder: 100,
        version: '4',
      },
    ]);
    const withOption = dictionaries([
      {
        ...created.dictionaries[0],
        options: [
          {
            codeDefined: false,
            enabled: true,
            fallbackLabel: 'High',
            i18n: { 'zh-CN': { label: '高' } },
            label: 'High',
            labelKey: null,
            sortOrder: 10,
            value: 'HIGH',
            version: '1',
          },
        ],
      },
    ]);
    const responses: HttpResponse[] = [
      ok(initial),
      ok(localeCatalog()),
      ok(created),
      ok(withOption),
      ok({
        key: 'iam.identity-status',
        options: [
          {
            codeDefined: true,
            disabled: false,
            label: 'Active',
            labelKey: 'dictionary.iam.identity-status.active',
            value: 'ACTIVE',
          },
        ],
      }),
      { status: 204, headers: {} },
      ok(initial),
      ok(localeCatalog()),
    ];
    const app = application(requests, responses);

    await app.systemDictionaries.load();
    await app.systemDictionaries.create({
      enabled: true,
      key: 'business.priority',
      label: 'Priority',
      sortOrder: 100,
    });
    await app.systemDictionaries.createOption('business.priority', {
      enabled: true,
      i18n: { 'zh-CN': { label: '高' } },
      label: 'High',
      sortOrder: 10,
      value: 'HIGH',
    });
    await app.systemDictionaries.loadSelections(['iam.identity-status']);
    const dictionary = app.systemDictionaries.state.dictionaries[0];
    if (!dictionary) throw new Error('Expected a loaded dictionary');
    await app.systemDictionaries.delete(dictionary);

    expect(requests.map((request) => request.path)).toEqual([
      '/system/dictionaries',
      '/system/locales',
      '/system/dictionaries',
      '/system/dictionaries/business.priority/options',
      '/system/dictionaries/iam.identity-status/options',
      '/system/dictionaries/business.priority?version=4',
      '/system/dictionaries',
      '/system/locales',
    ]);
    expect(requests[2]?.body).toEqual({
      enabled: true,
      key: 'business.priority',
      label: 'Priority',
      sortOrder: 100,
    });
    expect(requests[3]?.body).toEqual({
      enabled: true,
      i18n: { 'zh-CN': { label: '高' } },
      label: 'High',
      sortOrder: 10,
      value: 'HIGH',
    });
    expect(
      Object.isFrozen(
        app.systemDictionaries.state.localeCatalog?.supportedLocales,
      ),
    ).toBe(true);
    expect(
      app.systemDictionaries.state.selections['iam.identity-status']?.options,
    ).toEqual([expect.objectContaining({ label: 'Active', value: 'ACTIVE' })]);
    expect(
      Object.isFrozen(
        app.systemDictionaries.state.selections['iam.identity-status']?.options,
      ),
    ).toBe(true);
  });

  it('keeps the loaded dictionary page after a rejected mutation', async () => {
    const requests: HttpRequest[] = [];
    const initial = dictionaries([
      {
        codeDefined: true,
        enabled: true,
        key: 'iam.identity-status',
        label: 'Identity status',
        options: [],
        optionsEditable: false,
        sortOrder: 100,
        version: null,
      },
    ]);
    const app = application(requests, [
      ok(initial),
      ok(localeCatalog()),
      {
        body: {
          callId: 'call-1',
          code: '3100',
          message: 'The dictionary key already exists',
        },
        headers: {},
        status: 409,
      },
    ]);

    await app.systemDictionaries.load();
    await expect(
      app.systemDictionaries.create({
        enabled: true,
        key: 'iam.identity-status',
        label: 'Duplicate',
        sortOrder: 0,
      }),
    ).rejects.toMatchObject({ kind: 'conflict' });

    expect(app.systemDictionaries.state.phase).toBe('ready');
    expect(app.systemDictionaries.state.error?.kind).toBe('conflict');
    expect(app.systemDictionaries.state.dictionaries).toEqual(
      initial.dictionaries,
    );
    expect(requests.map((request) => request.path)).toEqual([
      '/system/dictionaries',
      '/system/locales',
      '/system/dictionaries',
    ]);
  });

  it('replaces and resets registered settings using their encoded text', async () => {
    const requests: HttpRequest[] = [];
    const initial = settings('PT10M', false, '3');
    const overridden = settings('PT15M', true, '4');
    const reset = settings('PT10M', false, '5');
    const responses: HttpResponse[] = [ok(initial), ok(overridden), ok(reset)];
    const app = application(requests, responses);

    await app.systemSettings.load();
    const setting = app.systemSettings.state.groups[0]?.settings[0];
    if (!setting) throw new Error('Expected a loaded setting');
    await app.systemSettings.replace(setting, 'PT15M');
    const replaced = app.systemSettings.state.groups[0]?.settings[0];
    if (!replaced) throw new Error('Expected a replaced setting');
    await app.systemSettings.reset(replaced);

    expect(requests.map((request) => request.path)).toEqual([
      '/system/settings',
      '/system/settings/storage.file.upload-ttl',
      '/system/settings/storage.file.upload-ttl',
    ]);
    expect(requests[1]?.body).toEqual({ value: 'PT15M' });
    expect(requests[1]?.headers['If-Match']).toBe('"3"');
    expect(requests[2]?.method).toBe('DELETE');
    expect(requests[2]?.headers['If-Match']).toBe('"4"');
    expect(Object.isFrozen(app.systemSettings.state.groups)).toBe(true);
    expect(Object.isFrozen(app.systemSettings.state.groups[0]?.settings)).toBe(
      true,
    );
  });
});

/** 使用内存 HTTP Port 创建不依赖浏览器运行时的管理端。 */
function application(requests: HttpRequest[], responses: HttpResponse[]) {
  return createAdminApplication({
    http: {
      async request(request) {
        requests.push(request);
        return responses.shift() ?? { headers: {}, status: 500 };
      },
    },
    languages: { read: () => 'zh-CN', write: () => undefined },
    objectTransfers: { transfer: async () => undefined },
    realtime: { subscribe: () => () => undefined },
    tokens: {
      clear: () => undefined,
      read: () => null,
      write: () => undefined,
    },
  });
}

/** 创建系统字典管理接口的最小成功响应。 */
function dictionaries(dictionaries: readonly object[]) {
  return { dictionaries };
}

function localeCatalog() {
  return { defaultLocale: 'zh-CN', supportedLocales: ['zh-CN', 'en-US'] };
}

/** 创建文件上传时长设置的最小成功响应。 */
function settings(value: string, stored: boolean, version: string) {
  return {
    groups: [
      {
        key: 'storage.file',
        label: 'Files',
        labelKey: 'setting.group.storage-file',
        settings: [
          {
            defaultValue: 'PT10M',
            description: 'Upload lifetime',
            descriptionKey: 'setting.storage.file.upload-ttl.description',
            key: 'storage.file.upload-ttl',
            label: 'Upload lifetime',
            labelKey: 'setting.storage.file.upload-ttl',
            readOnly: false,
            secret: false,
            sortOrder: 100,
            stored,
            value,
            valueType: 'duration',
            version,
          },
        ],
        sortOrder: 300,
      },
    ],
  };
}

/** 创建管理端统一 JSON 成功响应。 */
function ok(data: unknown): HttpResponse {
  return { body: { code: '0', data }, headers: {}, status: 200 };
}
