import type { BrowserContext, Page, Route } from 'playwright/test';

import { expect, test } from 'playwright/test';

const SESSION = {
  account: { accountId: '9007199254740993', realmKey: 'platform' },
  expiresAt: '2099-01-01T02:00:00.000Z',
  issuedAt: '2099-01-01T01:00:00.000Z',
  permissions: ['platform:accounts:read', 'platform:roles:read'],
  sessionId: 'session-1',
  subject: { subjectId: '9007199254740995', type: 'ACCOUNT' },
};

const TOKENS = {
  accessToken: 'access-token',
  accessTokenExpiresAt: '2099-01-01T01:30:00.000Z',
  sessionExpiresAt: '2099-01-01T02:00:00.000Z',
  sessionId: 'session-1',
  sessionToken: 'session-token',
  tokenMode: 'JWT',
  tokenType: 'Bearer',
};

const PROFILE = {
  avatarFileId: null,
  displayName: '示例管理员',
  email: 'admin@example.com',
  phoneNumber: null,
  updatedAt: '2099-01-01T01:00:00.000Z',
};

/** 返回后端统一成功响应。 */
function success(data: unknown) {
  return { code: '0', data, message: null };
}

/** 为认证示例安装一个只实现五个约定接口的浏览器 Mock。 */
async function mockAuthenticationApi(
  page: Page,
  options: { rejectPassword?: boolean; requireTotp?: boolean } = {},
): Promise<{ currentSessionCalls: () => number; logoutCalls: () => number }> {
  let currentSessionCalls = 0;
  let logoutCalls = 0;

  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route: Route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname.replace(/^\/api/u, '');

      if (request.method() === 'POST' && path === '/login/attempts') {
        const body = request.postDataJSON() as Record<string, unknown>;
        expect(body).toHaveProperty('identifier');
        expect(body).not.toHaveProperty('username');
        if (options.rejectPassword) {
          await route.fulfill({
            contentType: 'application/json',
            json: { code: '2200', data: null, message: '认证失败' },
            status: 401,
          });
          return;
        }
        await route.fulfill({
          contentType: 'application/json',
          json: success({
            authentication: {
              attemptId: 'attempt-1',
              availableMethods: options.requireTotp
                ? ['AUTHENTICATION/TOTP']
                : [],
              expiresAt: '2099-01-01T01:05:00.000Z',
              remainingFactorCount: options.requireTotp ? 1 : 0,
              status: options.requireTotp ? 'FACTOR_REQUIRED' : 'SATISFIED',
            },
            session: options.requireTotp ? null : TOKENS,
          }),
          status: 201,
        });
        return;
      }

      if (
        request.method() === 'POST' &&
        path === '/login/attempts/attempt-1/totp/proofs'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: success({
            authentication: {
              attemptId: 'attempt-1',
              availableMethods: [],
              expiresAt: '2099-01-01T01:05:00.000Z',
              remainingFactorCount: 0,
              status: 'SATISFIED',
            },
            session: TOKENS,
          }),
        });
        return;
      }

      if (request.method() === 'GET' && path === '/sessions/current') {
        currentSessionCalls += 1;
        expect(request.headers().authorization).toBe('Bearer access-token');
        await route.fulfill({
          contentType: 'application/json',
          json: success(SESSION),
        });
        return;
      }

      if (request.method() === 'GET' && path === '/me/profile') {
        await route.fulfill({
          contentType: 'application/json',
          json: success(PROFILE),
        });
        return;
      }

      if (request.method() === 'GET' && path === '/platform/accounts') {
        await route.fulfill({
          contentType: 'application/json',
          json: success({ items: [], page: 1, size: 20, total: '0' }),
        });
        return;
      }

      if (
        request.method() === 'GET' &&
        path === '/me/authentication/totp/credential'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: success({ enabled: false }),
        });
        return;
      }

      if (request.method() === 'GET' && path === '/me/passkeys') {
        await route.fulfill({
          contentType: 'application/json',
          json: success([]),
        });
        return;
      }

      if (
        request.method() === 'GET' &&
        path === '/me/authentication/settings'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: success({
            loginFactorCount: 1,
            methods: [],
            reusableAuthenticationMaximumAgeSeconds: '300',
            sensitiveActionFactorCount: 1,
          }),
        });
        return;
      }

      if (request.method() === 'DELETE' && path === '/sessions/current') {
        logoutCalls += 1;
        expect(request.headers().authorization).toBe('Bearer session-token');
        await route.fulfill({ body: '', status: 204 });
        return;
      }

      await route.fulfill({
        contentType: 'application/json',
        json: {
          code: 'not_found',
          data: null,
          message: `Unexpected ${request.method()} ${path}`,
        },
        status: 404,
      });
    },
  );

  return {
    currentSessionCalls: () => currentSessionCalls,
    logoutCalls: () => logoutCalls,
  };
}

/** 在登录页提交账号标识和密码。 */
async function submitPassword(page: Page): Promise<void> {
  await page.getByPlaceholder('请输入用户名或邮箱').fill('admin');
  await page.getByPlaceholder('请输入密码').fill('secret');
  await page.getByRole('button', { name: 'login' }).click();
}

/** 为当前 Chromium 页面安装支持 discoverable credential 的虚拟认证器。 */
async function installVirtualAuthenticator(
  context: BrowserContext,
  page: Page,
): Promise<void> {
  const client = await context.newCDPSession(page);
  await client.send('WebAuthn.enable');
  await client.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      automaticPresenceSimulation: true,
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      protocol: 'ctap2',
      transport: 'internal',
    },
  });
}

/** 安装覆盖 Passkey 注册、登录、动作认证和删除的 HTTP Mock。 */
async function mockPasskeyLifecycleApi(page: Page): Promise<void> {
  let passkeys: Array<Record<string, unknown>> = [];
  let registrationResponseId = '';

  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route: Route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname.replace(/^\/api/u, '');

      if (request.method() === 'POST' && path === '/login/attempts') {
        await route.fulfill({
          contentType: 'application/json',
          json: success(loginOutcome(TOKENS)),
          status: 201,
        });
        return;
      }

      if (
        request.method() === 'POST' &&
        path === '/passkey/assertions/options'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: success({
            ceremonyId: 'assertion-1',
            expiresAt: '2099-01-01T01:05:00.000Z',
            optionsJson: JSON.stringify({
              publicKey: {
                challenge: 'YXNzZXJ0aW9uLWNoYWxsZW5nZQ',
                rpId: 'localhost',
                timeout: 60_000,
                userVerification: 'required',
              },
            }),
          }),
        });
        return;
      }

      if (request.method() === 'POST' && path === '/login/attempts/passkey') {
        const response = JSON.parse(
          request.postDataJSON().responseJson,
        ) as Record<string, unknown>;
        expect(response.id).toBe(registrationResponseId);
        await route.fulfill({
          contentType: 'application/json',
          json: success(loginOutcome(TOKENS)),
          status: 201,
        });
        return;
      }

      if (request.method() === 'GET' && path === '/sessions/current') {
        await route.fulfill({
          contentType: 'application/json',
          json: success(SESSION),
        });
        return;
      }

      if (request.method() === 'GET' && path === '/me/profile') {
        await route.fulfill({
          contentType: 'application/json',
          json: success(PROFILE),
        });
        return;
      }

      if (request.method() === 'GET' && path === '/platform/accounts') {
        await route.fulfill({
          contentType: 'application/json',
          json: success({ items: [], page: 1, size: 20, total: '0' }),
        });
        return;
      }

      if (request.method() === 'GET' && path === '/me/password') {
        await route.fulfill({
          contentType: 'application/json',
          json: success({
            status: 'ACTIVE',
            updatedAt: '2099-01-01T01:00:00.000Z',
          }),
        });
        return;
      }

      if (
        request.method() === 'GET' &&
        path === '/me/authentication/totp/credential'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: { code: 'not_found', data: null, message: 'Not found' },
          status: 404,
        });
        return;
      }

      if (request.method() === 'GET' && path === '/me/passkeys') {
        await route.fulfill({
          contentType: 'application/json',
          json: success(passkeys),
        });
        return;
      }

      if (
        request.method() === 'GET' &&
        path === '/me/authentication/settings'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: success({
            loginFactorCount: 2,
            methods: [
              methodSettings('AUTHENTICATION/PASSWORD'),
              methodSettings('AUTHENTICATION/TOTP'),
              methodSettings('AUTHENTICATION/PASSKEY'),
            ],
            reusableAuthenticationMaximumAgeSeconds: '1',
            sensitiveActionFactorCount: 2,
          }),
        });
        return;
      }

      if (
        request.method() === 'POST' &&
        path === '/me/passkeys/registrations/options'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: success({
            ceremonyId: 'registration-1',
            expiresAt: '2099-01-01T01:05:00.000Z',
            optionsJson: JSON.stringify({
              publicKey: {
                attestation: 'none',
                authenticatorSelection: {
                  requireResidentKey: true,
                  residentKey: 'required',
                  userVerification: 'required',
                },
                challenge: 'cmVnaXN0cmF0aW9uLWNoYWxsZW5nZQ',
                pubKeyCredParams: [
                  { alg: -7, type: 'public-key' },
                  { alg: -257, type: 'public-key' },
                ],
                rp: { id: 'localhost', name: 'Sca4d Server Admin' },
                timeout: 60_000,
                user: {
                  displayName: '示例管理员',
                  id: 'YWNjb3VudC0x',
                  name: 'admin',
                },
              },
            }),
          }),
        });
        return;
      }

      if (request.method() === 'POST' && path === '/verifications') {
        const actionCode = request.postDataJSON().actionCode as string;
        const verificationId = actionCode.endsWith('.register')
          ? 'verification-register'
          : 'verification-delete';
        const methods = actionCode.endsWith('.register')
          ? ['AUTHENTICATION/PASSWORD']
          : ['AUTHENTICATION/PASSWORD'];
        await route.fulfill({
          contentType: 'application/json',
          json: success(
            verification(
              verificationId,
              actionCode.endsWith('.register') ? 1 : 2,
              methods,
            ),
          ),
          status: 201,
        });
        return;
      }

      if (
        request.method() === 'POST' &&
        path === '/verifications/verification-register/password/proofs'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: success(verification('verification-register', 0, [])),
        });
        return;
      }

      if (
        request.method() === 'POST' &&
        path === '/verifications/verification-delete/password/proofs'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: success(
            verification('verification-delete', 1, ['totp', 'passkey']),
          ),
        });
        return;
      }

      if (
        request.method() === 'POST' &&
        path === '/verifications/verification-delete/passkey/proofs'
      ) {
        const response = JSON.parse(
          request.postDataJSON().responseJson,
        ) as Record<string, unknown>;
        expect(response.id).toBe(registrationResponseId);
        await route.fulfill({
          contentType: 'application/json',
          json: success(verification('verification-delete', 0, [])),
        });
        return;
      }

      if (request.method() === 'POST' && path === '/me/passkeys') {
        expect(request.headers()['x-sca4d-verification']).toBe(
          'verification-register',
        );
        const registration = request.postDataJSON() as {
          name: string;
          responseJson: string;
        };
        const response = JSON.parse(registration.responseJson) as Record<
          string,
          unknown
        >;
        expect(response.type).toBe('public-key');
        registrationResponseId = String(response.id);
        const credential = {
          backedUp: false,
          backupEligible: true,
          createdAt: '2099-01-01T01:00:00.000Z',
          id: '7',
          lastUsedAt: null,
          name: registration.name,
          transports: ['internal'],
          version: '0',
        };
        passkeys = [credential];
        await route.fulfill({
          contentType: 'application/json',
          json: success(credential),
          status: 201,
        });
        return;
      }

      if (request.method() === 'DELETE' && path === '/me/passkeys/7') {
        expect(request.headers()['x-sca4d-verification']).toBe(
          'verification-delete',
        );
        passkeys = [];
        await route.fulfill({ body: '', status: 204 });
        return;
      }

      if (request.method() === 'DELETE' && path === '/sessions/current') {
        await route.fulfill({ body: '', status: 204 });
        return;
      }

      if (path === '/events') {
        await route.fulfill({ body: '', status: 204 });
        return;
      }

      await route.fulfill({
        contentType: 'application/json',
        json: {
          code: 'not_found',
          data: null,
          message: `Unexpected ${request.method()} ${path}`,
        },
        status: 404,
      });
    },
  );
}

/** 返回已经满足并建立 Session 的登录结果。 */
function loginOutcome(session: typeof TOKENS) {
  return {
    authentication: {
      attemptId: 'login-attempt-1',
      availableMethods: [],
      expiresAt: '2099-01-01T01:05:00.000Z',
      remainingFactorCount: 0,
      status: 'SATISFIED',
    },
    session,
  };
}

/** 返回动作认证 Mock 使用的公开进度。 */
function verification(
  verificationId: string,
  remainingFactorCount: number,
  availableMethods: readonly string[],
) {
  return {
    availableMethods,
    expiresAt: '2099-01-01T01:05:00.000Z',
    remainingFactorCount,
    status: remainingFactorCount === 0 ? 'SATISFIED' : 'FACTOR_REQUIRED',
    verificationId,
  };
}

/** 返回认证策略列表中的单个方法用途。 */
function methodSettings(methodCode: string) {
  return {
    loginAdditionalEnabled: true,
    loginStartEnabled: true,
    methodCode,
    sensitiveActionEnabled: true,
  };
}

test('密码登录后恢复 Session，并使用 Session Token 注销', async ({ page }) => {
  const calls = await mockAuthenticationApi(page);
  await page.goto('/#/auth/login?redirect=%2Fprofile');
  await submitPassword(page);

  await expect(page).toHaveURL(/#\/profile$/u);
  await expect(page.getByRole('button', { name: '示例管理员' })).toBeVisible();
  expect(calls.currentSessionCalls()).toBe(1);

  await page.reload();
  await expect(page.getByRole('button', { name: '示例管理员' })).toBeVisible();
  expect(calls.currentSessionCalls()).toBeGreaterThanOrEqual(2);

  await page.getByRole('button', { name: '示例管理员' }).click();
  await page.getByRole('menuitem', { name: '退出登录' }).dispatchEvent('click');
  await page.getByRole('button', { name: /确\s*认/u }).click();
  await expect(page).toHaveURL(/#\/auth\/login$/u);
  expect(calls.logoutCalls()).toBe(1);
});

test('密码错误显示认证失败，不误报登录认证过期', async ({ page }) => {
  await mockAuthenticationApi(page, { rejectPassword: true });
  await page.goto('/#/auth/login');
  await submitPassword(page);

  await expect(page.getByText('认证失败', { exact: true })).toBeVisible();
  await expect(page.getByText('登录认证过期，请重新登录后继续。')).toHaveCount(
    0,
  );
  await expect(page).toHaveURL(/#\/auth\/login$/u);
});

test('邮箱验证码入口与其他登录方式同级并保留重定向', async ({ page }) => {
  await page.goto('/#/auth/login?redirect=%2Fprofile');

  const emailCodeLogin = page.getByRole('button', {
    name: '使用邮箱验证码登录',
  });
  await expect(emailCodeLogin).toBeVisible();
  await emailCodeLogin.click();

  await expect(page).toHaveURL(
    /#\/auth\/code-login\?redirect=(?:%2F|\/)profile$/u,
  );
});

test('密码策略要求 TOTP 时继续同一个登录 attempt', async ({ page }) => {
  await mockAuthenticationApi(page, { requireTotp: true });
  await page.goto('/#/auth/login');
  await submitPassword(page);

  await expect(page.getByRole('heading', { name: '继续认证' })).toBeVisible();
  await page.getByRole('textbox', { name: 'TOTP 验证码' }).fill('123456');
  await page.getByRole('button', { name: '继续登录' }).click();

  await expect(page).toHaveURL(/#\/sca4d-server\/accounts$/u);
  await expect(page.getByRole('button', { name: '示例管理员' })).toBeVisible();
});

test('真实 WebAuthn ceremony 完成 Passkey 注册、登录、多因素选择和删除', async ({
  context,
  page,
}) => {
  test.slow();
  await installVirtualAuthenticator(context, page);
  await mockPasskeyLifecycleApi(page);

  await page.goto('http://localhost:5666/#/auth/login?redirect=%2Fsecurity');
  await submitPassword(page);
  await expect(page).toHaveURL(/#\/security$/u);

  await page.getByRole('button', { name: '添加 Passkey' }).click();
  await page.getByLabel('Passkey 名称').fill('工作笔记本');
  await page.getByRole('button', { name: '注册 Passkey', exact: true }).click();

  await page.getByLabel('当前密码').fill('secret');
  await page.getByRole('button', { name: /验\s*证/u }).click();
  await expect(page.getByText('工作笔记本', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: '示例管理员' }).click();
  await page.getByRole('menuitem', { name: '退出登录' }).dispatchEvent('click');
  await page.getByRole('button', { name: /确\s*认/u }).click();
  await expect(page).toHaveURL(/#\/auth\/login$/u);

  await page.getByRole('button', { name: '使用 Passkey 登录' }).click();
  await expect(page.getByRole('button', { name: '示例管理员' })).toBeVisible();

  await page.goto('http://localhost:5666/#/security');
  await expect(page.getByText('工作笔记本', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '删除', exact: true }).click();
  await page
    .locator('.ant-popconfirm')
    .getByRole('button', { name: /删\s*除/u })
    .click();

  await page.getByLabel('当前密码').fill('secret');
  await page.getByRole('button', { name: /验\s*证/u }).click();
  await page.getByText('Passkey', { exact: true }).last().click();
  await page.getByRole('button', { name: /验\s*证/u }).click();

  await expect(page.getByText('尚未添加 Passkey')).toBeVisible();
});
