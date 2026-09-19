import type { HttpRequest, HttpResponse, ObjectTransferRequest } from './ports';

import { describe, expect, it } from 'vitest';

import { AdminApiClient } from './api-client';
import { FilesController } from './files-controller';

describe('filesController', () => {
  it('clears pending access requests and ignores late responses after logout', async () => {
    let complete!: (response: HttpResponse) => void;
    const requests: HttpRequest[] = [];
    const delayed = new Promise<HttpResponse>((resolve) => {
      complete = resolve;
    });
    const files = new FilesController(
      api(requests, [delayed, ok([access('1', '/new')])]),
      { transfer: async () => undefined },
      () => 'session',
    );
    const rejected = expect(files.resolveAccessUrl('1')).rejects.toMatchObject({
      kind: 'authentication',
    });
    await new Promise<void>((resolve) => queueMicrotask(resolve));
    files.clear();
    complete(ok([access('1', '/old')]));
    await rejected;
    expect(await files.resolveAccessUrl('1')).toContain('/new');
  });

  it('sends multipart files without a manually supplied content type', async () => {
    const requests: HttpRequest[] = [];
    const client = api(requests, [ok(null), ok(null)]);
    const file = new File(['content'], 'sample.txt', { type: 'text/plain' });
    await client.postMultipart('/upload', file, {
      'content-type': 'application/json',
      'If-Match': '"1"',
    });
    await client.patchMultipart('/upload', file);
    expect(requests.map((request) => request.method)).toEqual([
      'POST',
      'PATCH',
    ]);
    for (const request of requests) {
      expect(request.body).toBeInstanceOf(FormData);
      expect((request.body as FormData).get('file')).toBeInstanceOf(File);
      expect(
        Object.keys(request.headers).some(
          (name) => name.toLowerCase() === 'content-type',
        ),
      ).toBe(false);
    }
    expect(requests[0]?.headers['If-Match']).toBe('"1"');
  });
  it('原样发送签发的上传请求头且不把 App Authorization 交给对象传输', async () => {
    const requests: HttpRequest[] = [];
    const transfers: ObjectTransferRequest[] = [];
    const responses: HttpResponse[] = [
      ok({
        fileId: '2081764183728193536',
        method: 'PUT',
        url: 'https://objects.example/files/temp',
        headers: { 'Content-Type': 'image/png', 'X-Signed-Header': 'signed' },
        expiresAt: '2026-07-27T16:00:00Z',
      }),
      ok(undefined, 204),
    ];
    const files = new FilesController(
      api(requests, responses),
      {
        transfer: async (request) => void transfers.push(request),
      },
      () => 'session',
    );
    const body = new ArrayBuffer(4);

    const fileId = await files.uploadAvatar('2081764183728193535', {
      filename: 'avatar.png',
      contentType: 'image/png',
      sizeBytes: body.byteLength,
      body,
    });

    expect(fileId).toBe('2081764183728193536');
    expect(requests.map((request) => request.path)).toEqual([
      '/files/uploads',
      '/files/uploads/2081764183728193536/completion',
    ]);
    expect(requests[0]?.body).toEqual({
      targetAccountId: '2081764183728193535',
      filename: 'avatar.png',
      contentType: 'image/png',
      sizeBytes: 4,
    });
    expect(transfers).toEqual([
      {
        method: 'PUT',
        url: 'https://objects.example/files/temp',
        headers: { 'Content-Type': 'image/png', 'X-Signed-Header': 'signed' },
        body,
      },
    ]);
    expect(transfers[0]?.headers).not.toHaveProperty('Authorization');
  });

  it('在读取地址到达安全刷新时间前使用缓存并支持图片失败后强制换签', async () => {
    const requests: HttpRequest[] = [];
    const responses: HttpResponse[] = [
      ok([
        {
          fileId: '2081764183728193536',
          url: 'https://objects.example/files/first',
          expiresAt: new Date(Date.now() + 300_000).toISOString(),
        },
      ]),
      ok([
        {
          fileId: '2081764183728193536',
          url: 'https://objects.example/files/refreshed',
          expiresAt: new Date(Date.now() + 300_000).toISOString(),
        },
      ]),
    ];
    const files = new FilesController(
      api(requests, responses),
      {
        transfer: async () => undefined,
      },
      () => 'session',
    );

    const first = await files.resolveAccessUrls(['2081764183728193536']);
    const cached = await files.resolveAccessUrls(['2081764183728193536']);
    const refreshed = await files.resolveAccessUrls(
      ['2081764183728193536'],
      true,
    );

    expect(first.get('2081764183728193536')).toContain('/first');
    expect(cached.get('2081764183728193536')).toContain('/first');
    expect(refreshed.get('2081764183728193536')).toContain('/refreshed');
    expect(requests).toHaveLength(2);
  });

  it('合并同一微任务中的文件请求并按当前 Session 隔离缓存', async () => {
    const requests: HttpRequest[] = [];
    let sessionId = 'session-a';
    const responses: HttpResponse[] = [
      ok([
        access('2081764183728193536', '/first-a'),
        access('2081764183728193537', '/second-a'),
      ]),
      ok([access('2081764183728193536', '/first-b')]),
    ];
    const files = new FilesController(
      api(requests, responses),
      { transfer: async () => undefined },
      () => sessionId,
    );

    const [first, second] = await Promise.all([
      files.resolveAccessUrl('2081764183728193536'),
      files.resolveAccessUrl('2081764183728193537'),
    ]);
    sessionId = 'session-b';
    const switched = await files.resolveAccessUrl('2081764183728193536');

    expect(first).toContain('/first-a');
    expect(second).toContain('/second-a');
    expect(switched).toContain('/first-b');
    expect(requests).toHaveLength(2);
    expect(requests[0]?.body).toEqual({
      fileIds: ['2081764183728193536', '2081764183728193537'],
    });
  });

  it('复用已经发出的同文件换签请求', async () => {
    const requests: HttpRequest[] = [];
    let complete: (response: HttpResponse) => void = () => undefined;
    const delayed = new Promise<HttpResponse>((resolve) => {
      complete = resolve;
    });
    const files = new FilesController(
      api(requests, [delayed]),
      {
        transfer: async () => undefined,
      },
      () => 'session',
    );

    const first = files.resolveAccessUrl('2081764183728193536');
    await new Promise<void>((resolve) => queueMicrotask(resolve));
    const concurrent = files.resolveAccessUrl('2081764183728193536');
    complete(ok([access('2081764183728193536', '/shared')]));

    await expect(first).resolves.toContain('/shared');
    await expect(concurrent).resolves.toContain('/shared');
    expect(requests).toHaveLength(1);
  });

  it('超过一百个文件时按后端请求上限分批签发', async () => {
    const requests: HttpRequest[] = [];
    const ids = Array.from({ length: 101 }, (_, index) => String(index + 1));
    const responses: HttpResponse[] = [
      ok(ids.slice(0, 100).map((id) => access(id, `/files/${id}`))),
      ok([access('101', '/files/101')]),
    ];
    const files = new FilesController(
      api(requests, responses),
      {
        transfer: async () => undefined,
      },
      () => 'session',
    );

    const resolved = await files.resolveAccessUrls(ids);
    const firstRequest = requests[0];

    expect(resolved).toHaveLength(101);
    expect(requests).toHaveLength(2);
    expect(firstRequest).toBeDefined();
    if (!firstRequest) throw new Error('First access URL request is missing');
    expect(
      (firstRequest.body as { fileIds: readonly string[] }).fileIds,
    ).toHaveLength(100);
    expect(requests[1]?.body).toEqual({ fileIds: ['101'] });
  });
});

function access(fileId: string, path: string) {
  return {
    fileId,
    url: `https://objects.example${path}`,
    expiresAt: new Date(Date.now() + 300_000).toISOString(),
  };
}

function api(
  requests: HttpRequest[],
  responses: Array<HttpResponse | Promise<HttpResponse>>,
): AdminApiClient {
  return new AdminApiClient(
    {
      request: async (request) => {
        requests.push(request);
        return responses.shift() ?? { status: 500, headers: {} };
      },
    },
    {
      read: () => ({
        tokenMode: 'SESSION',
        tokenType: 'Bearer',
        accessToken: 'app-token',
        accessTokenExpiresAt: '2026-07-27T16:00:00Z',
        sessionToken: null,
        sessionId: 'session',
        sessionExpiresAt: '2026-07-27T17:00:00Z',
      }),
      write: () => undefined,
      clear: () => undefined,
    },
    { read: () => 'zh-CN', write: () => undefined },
    () => undefined,
  );
}

function ok(data: unknown, status = 200): HttpResponse {
  return { status, headers: {}, body: { code: '0', data } };
}
