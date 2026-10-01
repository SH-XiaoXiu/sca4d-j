import type { OperationLog } from './types';

import { describe, expect, it } from 'vitest';

import {
  operationLogActionText,
  operationLogTargetReference,
  operationLogTargetText,
  readableCode,
} from './operation-log-display';

const text = {
  empty: '-',
  operationLabels: {
    'PLATFORM/ACCOUNT/DELETE': '删除账号',
  },
  targetTypeLabels: {
    'IAM/ACCOUNT': '账号',
    'STORAGE/FILE': '文件',
  },
  targetNames: {
    'IAM/ACCOUNT:41': 'Administrator',
  },
};

describe('operation log display', () => {
  it('uses registered operation labels before falling back to readable codes', () => {
    expect(
      operationLogActionText(operationLog('PLATFORM/ACCOUNT/DELETE'), text),
    ).toBe('删除账号');
    expect(
      operationLogActionText(operationLog('legacy:operation.read'), text),
    ).toBe('legacy operation read');
  });

  it('uses friendly target resource text without losing the identifier', () => {
    expect(
      operationLogTargetText(operationLog('PLATFORM/ACCOUNT/DELETE'), text),
    ).toBe('Administrator 41');
    expect(
      operationLogTargetReference(
        operationLog('PLATFORM/ACCOUNT/DELETE'),
        text,
      ),
    ).toEqual({
      id: '41',
      name: 'Administrator',
      resourceType: 'IAM/ACCOUNT',
      typeLabel: '账号',
    });
    expect(
      operationLogTargetText(
        { ...operationLog('storage/file/access-url/create'), targetType: null },
        text,
      ),
    ).toBe('-');
  });

  it('formats unknown codes with readable separators', () => {
    expect(readableCode('storage/file/access-url/create')).toBe(
      'storage file access-url create',
    );
    expect(readableCode('passkey:credential')).toBe('passkey credential');
  });
});

/** 创建操作日志展示测试所需的最小记录。 */
function operationLog(operation: string): OperationLog {
  return {
    accountId: '21',
    clientIp: '127.0.0.1',
    httpMethod: 'DELETE',
    httpPath: '/platform/accounts/31',
    id: '1',
    occurredAt: '2026-08-02T12:00:00Z',
    operation,
    payloadJson: null,
    payloadType: null,
    payloadVersion: null,
    principalDisplayName: 'Administrator',
    principalId: '31',
    requestId: 'request-1',
    result: 'SUCCESS',
    subjectId: '11',
    targetId: '41',
    targetType: 'IAM/ACCOUNT',
    userAgent: 'test client',
  };
}
