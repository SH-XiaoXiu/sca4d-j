import type { HttpRequest, HttpResponse } from './ports';
import type { AuthenticationSettings, Verification } from './types';

import { describe, expect, it, vi } from 'vitest';

import { AdminApiClient } from './api-client';
import { AuthenticationSettingsController } from './authentication-settings-controller';
import { PasskeyController } from './passkey-controller';
import { TotpCredentialController } from './totp-credential-controller';

describe('authentication resource controllers', () => {
  it('分别读取 TOTP、Passkey 和认证设置', async () => {
    const api = apiWith(async (request) => {
      if (request.path === '/me/authentication/totp/credential') {
        return { body: { code: '2404', data: null }, headers: {}, status: 404 };
      }
      if (request.path === '/me/passkeys') return ok([]);
      if (request.path === '/me/authentication/settings')
        return ok(settings(), { etag: '"1"' });
      return { headers: {}, status: 500 };
    });
    const totp = new TotpCredentialController(api, () => undefined);
    const passkeys = new PasskeyController(api);
    const authenticationSettings = new AuthenticationSettingsController(api);

    await Promise.all([
      totp.load(),
      passkeys.load(),
      authenticationSettings.load(),
    ]);

    expect(totp.state.phase).toBe('empty');
    expect(passkeys.state.phase).toBe('empty');
    expect(authenticationSettings.state.settings).toEqual(settings());
    expect(authenticationSettings.state.etag).toBe('"1"');
  });

  it('按认证设置 ETag 和动作认证结果完整替换设置', async () => {
    const requests: HttpRequest[] = [];
    const controller = new AuthenticationSettingsController(
      apiWith(async (request) => {
        requests.push(request);
        return request.method === 'GET'
          ? ok(settings(), { etag: '"4"' })
          : ok(settings(), { etag: '"5"' });
      }),
    );

    await controller.load();
    await controller.replace(settings(), satisfiedVerification());

    expect(requests[1]?.headers['If-Match']).toBe('"4"');
    expect(requests[1]?.headers['X-Verification']).toBe('verification-1');
    expect(controller.state.etag).toBe('"5"');
  });

  it('创建并激活首次 TOTP enrollment，不修改认证设置', async () => {
    const requests: HttpRequest[] = [];
    const invalidated = vi.fn();
    const controller = new TotpCredentialController(
      apiWith(async (request) => {
        requests.push(request);
        if (request.method === 'GET' && request.path.endsWith('/credential')) {
          return {
            body: { code: '2404', data: null },
            headers: {},
            status: 404,
          };
        }
        if (request.path.endsWith('/enrollments')) {
          return ok({
            enrollmentId: 'enrollment-1',
            expiresAt: '2026-08-01T08:00:00Z',
          });
        }
        if (request.path.endsWith('/qr-code')) {
          return { body: new ArrayBuffer(8), headers: {}, status: 200 };
        }
        if (request.method === 'POST' && request.path.endsWith('/credential')) {
          return ok({ enabled: true });
        }
        return { headers: {}, status: 500 };
      }),
      invalidated,
    );

    await controller.load();
    await controller.createEnrollment(satisfiedVerification());
    await controller.activate('123456');

    expect(controller.state.credential).toEqual({ enabled: true });
    expect(controller.state.enrollment).toBeNull();
    expect(invalidated).not.toHaveBeenCalled();
    expect(requests).not.toContainEqual(
      expect.objectContaining({ path: '/me/authentication/settings' }),
    );
  });

  it('注册和删除 Passkey 后只更新 Passkey 列表', async () => {
    const controller = new PasskeyController(
      apiWith(async (request) => {
        if (request.method === 'GET') return ok([]);
        if (request.method === 'POST') return ok(passkey('7'));
        if (request.method === 'DELETE') return ok(null);
        return { headers: {}, status: 500 };
      }),
    );

    await controller.load();
    await controller.register(
      'Laptop',
      { ceremonyId: 'ceremony-1', responseJson: '{}' },
      satisfiedVerification(),
    );
    expect(
      controller.state.credentials.map((credential) => credential.id),
    ).toEqual(['7']);

    await controller.remove('7', satisfiedVerification());
    expect(controller.state.credentials).toEqual([]);
    expect(controller.state.phase).toBe('empty');
  });
});

function apiWith(
  request: (request: HttpRequest) => Promise<HttpResponse>,
): AdminApiClient {
  return new AdminApiClient(
    { request },
    { clear: () => undefined, read: () => null, write: () => undefined },
    { read: () => 'zh-CN', write: () => undefined },
    () => undefined,
  );
}

function passkey(id: string) {
  return {
    backedUp: false,
    backupEligible: true,
    createdAt: '2026-08-01T06:00:00Z',
    id,
    lastUsedAt: null,
    name: 'Laptop',
    transports: ['internal'],
    version: '0',
  } as const;
}

function satisfiedVerification(): Verification {
  return {
    availableMethods: [],
    expiresAt: '2026-08-01T08:00:00Z',
    remainingFactorCount: 0,
    status: 'SATISFIED',
    verificationId: 'verification-1',
  };
}

function settings(): AuthenticationSettings {
  return {
    loginFactorCount: 1,
    methods: [
      {
        loginAdditionalEnabled: true,
        loginStartEnabled: true,
        methodCode: 'AUTHENTICATION/PASSWORD',
        sensitiveActionEnabled: true,
      },
    ],
    reusableAuthenticationMaximumAgeSeconds: '300',
    sensitiveActionFactorCount: 1,
  };
}

function ok(
  data: unknown,
  headers: Readonly<Record<string, string>> = {},
): HttpResponse {
  return { body: { code: '0', data }, headers, status: 200 };
}
