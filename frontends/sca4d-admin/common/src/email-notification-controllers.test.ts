import type { HttpRequest, HttpResponse } from './ports';
import type {
  EmailSender,
  EmailTemplate,
  ReplaceEmailSenderInput,
} from './types';

import { describe, expect, it } from 'vitest';

import { AdminApiClient } from './api-client';
import {
  EmailSendersController,
  EmailTemplatesController,
} from './email-notification-controllers';

describe('Email notification controllers', () => {
  it('keeps an empty sender list interactive and submits explicit password semantics with ETag', async () => {
    const requests: HttpRequest[] = [];
    const current = sender({ passwordConfigured: true, version: '3' });
    const updated = sender({ host: 'smtp.example.com', version: '4' });
    const responses: HttpResponse[] = [
      ok([]),
      ok(current, { etag: '"sender-3"' }),
      ok(updated, { etag: '"sender-4"' }),
      ok([updated]),
    ];
    const controller = new EmailSendersController(api(requests, responses));

    await controller.load();
    expect(controller.state.phase).toBe('ready');
    expect(controller.state.senders).toEqual([]);
    expect(Object.isFrozen(controller.state.senders)).toBe(true);

    await controller.require('7');
    const input: ReplaceEmailSenderInput = {
      fromAddress: 'noreply@example.com',
      fromName: 'Sca4d Server',
      host: 'smtp.example.com',
      name: 'Primary',
      password: null,
      passwordChanged: false,
      port: 587,
      replyTo: null,
      security: 'STARTTLS',
      username: 'mailer',
    };
    await controller.replace(input);

    const replace = requests.find((request) => request.method === 'PUT');
    expect(replace?.path).toBe('/system/email/senders/7');
    expect(replace?.headers?.['If-Match']).toBe('"sender-3"');
    expect(replace?.body).toEqual(input);
    expect(controller.state.detailEtag).toBe('"sender-4"');
    expect(controller.state.senders[0]?.host).toBe('smtp.example.com');
  });

  it('uses fresh sender detail ETags for status, connection test, and delete', async () => {
    const requests: HttpRequest[] = [];
    const current = sender({ version: '5' });
    const enabled = sender({ enabled: true, version: '6' });
    const responses: HttpResponse[] = [
      ok(current, { etag: '"sender-5"' }),
      ok(enabled, { etag: '"sender-6"' }),
      ok([enabled]),
      noContent(),
      ok(current, { etag: '"sender-7"' }),
      noContent(),
      ok([]),
    ];
    const controller = new EmailSendersController(api(requests, responses));

    await controller.setEnabled('7', true);
    await controller.testConnection('7');
    await controller.delete('7');

    const patch = requests.find((request) => request.method === 'PATCH');
    const deletion = requests.find((request) => request.method === 'DELETE');
    expect(patch?.headers?.['If-Match']).toBe('"sender-5"');
    expect(patch?.body).toEqual({ enabled: true });
    expect(
      requests.some(
        (request) =>
          request.path === '/system/email/senders/7/connection-tests',
      ),
    ).toBe(true);
    expect(deletion?.headers?.['If-Match']).toBe('"sender-7"');
  });

  it('loads templates with locales and submits preview and test-message bodies', async () => {
    const requests: HttpRequest[] = [];
    const current = template({
      content: {
        'fr-FR': localeContent('Code ', 'Code '),
        'zh-CN': localeContent('验证码 ', '验证码 '),
      },
      version: '2',
    });
    const responses: HttpResponse[] = [
      ok({ items: [current], page: 1, size: 20, total: 1 }),
      ok({
        defaultLocale: 'zh-CN',
        supportedLocales: ['zh-CN', 'en-US'],
      }),
      ok(current, { etag: '"template-2"' }),
      ok({
        html: '<strong>123456</strong>',
        subject: '验证码',
        text: '123456',
      }),
    ];
    const controller = new EmailTemplatesController(api(requests, responses));

    await controller.load();
    await controller.require('11');
    await controller.preview({
      content: current.content,
      languageTag: 'zh-CN',
      variables: { code: '123456', expiresInMinutes: '5' },
    });

    expect(requests[0]?.query).toEqual({ page: 1, size: 20 });
    expect(controller.state.total).toBe(1);
    expect(controller.state.localeCatalog?.supportedLocales).toEqual([
      'zh-CN',
      'en-US',
    ]);
    expect(
      Object.isFrozen(controller.state.templates[0]?.content['zh-CN']),
    ).toBe(true);
    expect(
      controller.state.templates[0]?.content['fr-FR']?.title.content?.[0]
        ?.content?.[0]?.text,
    ).toBe('Code ');
    expect(controller.state.preview?.subject).toBe('验证码');
    expect(requests[3]?.body).toEqual({
      content: current.content,
      languageTag: 'zh-CN',
      variables: { code: '123456', expiresInMinutes: '5' },
    });
  });

  it('publishes a version conflict without retrying a template status change', async () => {
    const requests: HttpRequest[] = [];
    const responses: HttpResponse[] = [
      ok(template(), { etag: '"template-1"' }),
      failure(412, 'resource_changed'),
    ];
    const controller = new EmailTemplatesController(api(requests, responses));

    await expect(controller.setEnabled('11', true)).rejects.toMatchObject({
      kind: 'versionConflict',
    });
    expect(controller.state.phase).toBe('conflict');
    expect(requests).toHaveLength(2);
  });
});

/** 创建不依赖浏览器的管理 API 客户端。 */
function api(requests: HttpRequest[], responses: HttpResponse[]) {
  return new AdminApiClient(
    {
      async request(request) {
        requests.push(request);
        return responses.shift() ?? failure(500, 'unexpected_request');
      },
    },
    { clear: () => undefined, read: () => null, write: () => undefined },
    { read: () => 'zh-CN', write: () => undefined },
    () => undefined,
  );
}

/** 返回统一成功响应。 */
function ok(
  data: unknown,
  headers: Readonly<Record<string, string>> = {},
): HttpResponse {
  return { body: { code: '0', data }, headers, status: 200 };
}

/** 返回无正文成功响应。 */
function noContent(): HttpResponse {
  return { headers: {}, status: 204 };
}

/** 返回统一错误响应。 */
function failure(status: number, code: string): HttpResponse {
  return {
    body: { code, data: null, message: code },
    headers: {},
    status,
  };
}

/** 创建 SMTP 配置测试数据。 */
function sender(overrides: Partial<EmailSender> = {}): EmailSender {
  return {
    enabled: false,
    fromAddress: 'noreply@example.com',
    fromName: 'Sca4d Server',
    host: 'localhost',
    name: 'Primary',
    passwordConfigured: false,
    port: 1025,
    replyTo: null,
    version: '1',
    security: 'NONE',
    senderId: '7',
    username: null,
    ...overrides,
  };
}

/** 创建 Email 模板测试数据。 */
function template(overrides: Partial<EmailTemplate> = {}): EmailTemplate {
  return {
    content: {
      'zh-CN': localeContent('验证码 ', '验证码 '),
    },
    enabled: false,
    notificationCode: 'authentication.email-code',
    version: '1',
    templateId: '11',
    variables: ['code', 'expiresInMinutes'],
    ...overrides,
  };
}

/** 创建包含一个变量节点的标题和正文。 */
function localeContent(title: string, body: string) {
  return {
    body: document(body),
    title: document(title),
  };
}

/** 创建最小 ProseMirror 文档。 */
function document(text: string) {
  return {
    content: [
      {
        content: [
          { text, type: 'text' },
          { attrs: { name: 'code' }, type: 'notificationVariable' },
        ],
        type: 'paragraph',
      },
    ],
    type: 'doc',
  };
}
