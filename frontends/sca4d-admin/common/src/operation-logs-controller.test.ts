import type { HttpRequest, HttpResponse } from './ports';
import type { OperationLog } from './types';

import { describe, expect, it } from 'vitest';

import { createAdminApplication } from './application';

describe('operation logs controller', () => {
  it('preserves 64-bit filters and normalizes list and detail responses', async () => {
    const requests: HttpRequest[] = [];
    const item = operationLog('2081571083525292032');
    const responses: HttpResponse[] = [
      ok({ items: [item], page: 2, size: 50, total: '9007199254740991' }),
      ok(item),
    ];
    const app = createAdminApplication({
      http: {
        async request(request) {
          requests.push(request);
          return responses.shift() ?? { status: 500, headers: {} };
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

    await app.operationLogs.load({
      accountId: '2081571083525292033',
      actorPrincipalId: '2081571083525292034',
      operation: 'platform:account:delete',
      page: 2,
      result: 'SUCCESS',
      size: 50,
    });
    await app.operationLogs.require(item.id);

    expect(requests[0]?.query).toMatchObject({
      accountId: '2081571083525292033',
      actorPrincipalId: '2081571083525292034',
      operation: 'platform:account:delete',
      page: 2,
      result: 'SUCCESS',
      size: 50,
    });
    expect(requests[1]?.path).toBe(`/system/audit/records/${item.id}`);
    expect(app.operationLogs.state.page.total).toBe(9_007_199_254_740_991);
    expect(app.operationLogs.state.selected).toEqual(item);
    expect(Object.isFrozen(app.operationLogs.state.page.items)).toBe(true);
  });
});

function ok(data: unknown): HttpResponse {
  return { status: 200, headers: {}, body: { code: '0', data } };
}

function operationLog(id: string): OperationLog {
  return {
    accountId: '21',
    clientIp: '127.0.0.1',
    httpMethod: 'DELETE',
    httpPath: '/platform/accounts/31',
    id,
    occurredAt: '2026-08-02T12:00:00Z',
    operation: 'platform:account:delete',
    payloadJson: null,
    payloadType: null,
    payloadVersion: null,
    principalDisplayName: 'Administrator',
    principalId: '31',
    requestId: 'request-1',
    result: 'SUCCESS',
    subjectId: '11',
    targetId: '41',
    targetType: 'account',
    userAgent: 'test client',
  };
}
