import type { HttpRequest, HttpResponse } from './ports';
import type { Verification } from './types';

import { describe, expect, it } from 'vitest';

import {
  ActionVerificationController,
  verificationHeaders,
} from './action-verification-controller';
import { AdminApiClient } from './api-client';

describe('actionVerificationController', () => {
  it('按后端返回的方法连续提交多个认证因素', async () => {
    const requests: HttpRequest[] = [];
    const controller = controllerWith(async (request) => {
      requests.push(request);
      if (request.path === '/verifications') {
        return ok(verification(3, ['password', 'passkey', 'totp']));
      }
      if (request.path.endsWith('/password/proofs')) {
        return ok(verification(2, ['passkey', 'totp']));
      }
      if (request.path.endsWith('/passkey/proofs')) {
        return ok(verification(1, ['totp']));
      }
      if (request.path.endsWith('/totp/proofs')) {
        return ok(verification(0, []));
      }
      return { headers: {}, status: 500 };
    });

    await expect(
      controller.start('authentication.settings.replace'),
    ).resolves.toMatchObject({
      remainingFactorCount: 3,
    });
    await expect(controller.submitPassword('secret')).resolves.toMatchObject({
      availableMethods: ['passkey', 'totp'],
    });
    await expect(
      controller.submitPasskey({
        ceremonyId: 'ceremony-1',
        responseJson: '{}',
      }),
    ).resolves.toMatchObject({ remainingFactorCount: 1 });
    await expect(controller.submitTotp('123456')).resolves.toMatchObject({
      status: 'SATISFIED',
    });

    expect(requests.map((request) => request.path)).toEqual([
      '/verifications',
      '/verifications/verification-1/password/proofs',
      '/verifications/verification-1/passkey/proofs',
      '/verifications/verification-1/totp/proofs',
    ]);
  });

  it('近期认证已经满足动作时不要求提交因素', async () => {
    const controller = controllerWith(async () => ok(verification(0, [])));

    const result = await controller.start('authentication.passkey.delete');

    expect(result.status).toBe('SATISFIED');
    expect(controller.state.phase).toBe('ready');
    expect(verificationHeaders(result)).toEqual({
      'X-Verification': 'verification-1',
    });
  });

  it('拒绝使用尚未满足的 verification 执行写入', () => {
    expect(() => verificationHeaders(verification(1, ['password']))).toThrow(
      '动作认证尚未满足要求',
    );
  });
});

function controllerWith(
  request: (request: HttpRequest) => Promise<HttpResponse>,
): ActionVerificationController {
  return new ActionVerificationController(
    new AdminApiClient(
      { request },
      { clear: () => undefined, read: () => null, write: () => undefined },
      { read: () => 'zh-CN', write: () => undefined },
      () => undefined,
    ),
  );
}

function verification(
  remainingFactorCount: number,
  availableMethods: readonly string[],
): Verification {
  return {
    availableMethods,
    expiresAt: '2026-08-01T08:00:00Z',
    remainingFactorCount,
    status: remainingFactorCount === 0 ? 'SATISFIED' : 'FACTOR_REQUIRED',
    verificationId: 'verification-1',
  };
}

function ok(data: unknown): HttpResponse {
  return { body: { code: '0', data }, headers: {}, status: 200 };
}
