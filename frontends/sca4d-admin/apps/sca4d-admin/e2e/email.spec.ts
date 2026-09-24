import type { Page, Route } from 'playwright/test';

import { expect, test } from 'playwright/test';

const EMAIL = 'admin@example.com';
const SESSION = {
  account: { accountId: '9007199254740993', realmKey: 'platform' },
  expiresAt: '2099-01-01T02:00:00.000Z',
  issuedAt: '2099-01-01T01:00:00.000Z',
  permissions: [
    'platform:accounts:read',
    'system:email:manage',
    'system:email:read',
  ],
  sessionId: 'session-email',
  subject: { subjectId: '9007199254740995', type: 'ACCOUNT' },
};
const TOKENS = {
  accessToken: 'email-access-token',
  accessTokenExpiresAt: '2099-01-01T01:30:00.000Z',
  sessionExpiresAt: '2099-01-01T02:00:00.000Z',
  sessionId: 'session-email',
  sessionToken: 'email-session-token',
  tokenMode: 'JWT',
  tokenType: 'Bearer',
};
const PROFILE = {
  avatarFileId: null,
  displayName: '邮件管理员',
  email: EMAIL,
  phoneNumber: null,
  updatedAt: '2099-01-01T01:00:00.000Z',
};

/** 返回后端统一成功响应。 */
function success(data: unknown) {
  return { code: '0', data, message: null };
}

/** 返回指定后续认证方法的登录结果。 */
function loginOutcome(
  availableMethods: readonly string[],
  session: null | typeof TOKENS,
) {
  return {
    authentication: {
      attemptId: 'email-attempt-1',
      availableMethods,
      expiresAt: '2099-01-01T01:05:00.000Z',
      remainingFactorCount: session ? 0 : 1,
      status: session ? 'SATISFIED' : 'FACTOR_REQUIRED',
    },
    session,
  };
}

/** 返回不包含完整邮箱和验证码的公开 Challenge。 */
function emailChallenge(challengeId: string) {
  return {
    challengeId,
    expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
    maskedRecipient: 'a***@example.com',
    resendAt: new Date(Date.now() + 60_000).toISOString(),
  };
}

/** 响应登录后每个 Email E2E 都会读取的 Session、资料和安全设置。 */
async function fulfillAuthenticatedShell(route: Route): Promise<boolean> {
  const request = route.request();
  const path = new URL(request.url()).pathname.replace(/^\/api/u, '');

  if (request.method() === 'GET' && path === '/sessions/current') {
    expect(request.headers().authorization).toBe('Bearer email-access-token');
    await route.fulfill({
      contentType: 'application/json',
      json: success(SESSION),
    });
    return true;
  }
  if (request.method() === 'GET' && path === '/me/profile') {
    await route.fulfill({
      contentType: 'application/json',
      json: success(PROFILE),
    });
    return true;
  }
  if (request.method() === 'GET' && path === '/me/notifications') {
    await route.fulfill({
      contentType: 'application/json',
      json: success({ messages: [], unreadCount: 0 }),
    });
    return true;
  }
  if (request.method() === 'GET' && path === '/me/notification-settings') {
    await route.fulfill({
      contentType: 'application/json',
      json: success({
        channels: {},
        channelsVersion: '0',
        definitions: {},
        languageTag: 'zh-CN',
        languageVersion: '0',
        regionCode: 'GLOBAL',
        regionVersion: '0',
      }),
    });
    return true;
  }
  if (
    request.method() === 'GET' &&
    path === '/me/authentication/totp/credential'
  ) {
    await route.fulfill({
      contentType: 'application/json',
      json: success({ enabled: false }),
    });
    return true;
  }
  if (request.method() === 'GET' && path === '/me/passkeys') {
    await route.fulfill({ contentType: 'application/json', json: success([]) });
    return true;
  }
  if (request.method() === 'GET' && path === '/me/authentication/settings') {
    await route.fulfill({
      contentType: 'application/json',
      json: success({
        loginFactorCount: 1,
        methods: [],
        reusableAuthenticationMaximumAgeSeconds: '300',
        sensitiveActionFactorCount: 1,
      }),
    });
    return true;
  }
  if (path === '/events') {
    await route.fulfill({ body: '', status: 204 });
    return true;
  }
  return false;
}

/** 按指定因素顺序模拟 Email 登录及登录后的公共请求。 */
async function mockEmailLoginApi(
  page: Page,
  flow: 'email-only' | 'email-then-password' | 'password-then-email',
): Promise<void> {
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route: Route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname.replace(/^\/api/u, '');

      if (
        request.method() === 'POST' &&
        path === '/login/email-code/challenges'
      ) {
        expect(request.postDataJSON()).toEqual({ email: EMAIL });
        await route.fulfill({
          contentType: 'application/json',
          json: success(emailChallenge('initial-challenge')),
        });
        return;
      }
      if (request.method() === 'POST' && path === '/login/email-code/proofs') {
        expect(request.postDataJSON()).toEqual({
          challengeId: 'initial-challenge',
          code: '123456',
        });
        await route.fulfill({
          contentType: 'application/json',
          json: success(
            flow === 'email-then-password'
              ? loginOutcome(['AUTHENTICATION/PASSWORD'], null)
              : loginOutcome([], TOKENS),
          ),
          status: 201,
        });
        return;
      }
      if (request.method() === 'POST' && path === '/login/attempts') {
        expect(request.postDataJSON()).toEqual({
          identifier: 'admin',
          password: 'secret',
        });
        await route.fulfill({
          contentType: 'application/json',
          json: success(loginOutcome(['AUTHENTICATION/EMAIL_CODE'], null)),
          status: 201,
        });
        return;
      }
      if (
        request.method() === 'POST' &&
        path === '/login/attempts/email-attempt-1/password/proofs'
      ) {
        expect(request.postDataJSON()).toEqual({ password: 'secret' });
        await route.fulfill({
          contentType: 'application/json',
          json: success(loginOutcome([], TOKENS)),
        });
        return;
      }
      if (
        request.method() === 'POST' &&
        path === '/login/attempts/email-attempt-1/email-code/challenges'
      ) {
        await route.fulfill({
          contentType: 'application/json',
          json: success(emailChallenge('pending-challenge')),
        });
        return;
      }
      if (
        request.method() === 'POST' &&
        path === '/login/attempts/email-attempt-1/email-code/proofs'
      ) {
        expect(request.postDataJSON()).toEqual({
          challengeId: 'pending-challenge',
          code: '654321',
        });
        await route.fulfill({
          contentType: 'application/json',
          json: success(loginOutcome([], TOKENS)),
        });
        return;
      }
      if (await fulfillAuthenticatedShell(route)) return;

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

/** 逐格输入 VbenPinInput 的六位验证码。 */
async function fillEmailCode(page: Page, code: string): Promise<void> {
  for (const [index, digit] of [...code].entries()) {
    await page.getByLabel(`pin input ${index + 1} of 6`).fill(digit);
  }
}

/** 从密码登录页进入邮箱验证码登录页并签发 Challenge。 */
async function requestInitialEmailCode(page: Page): Promise<void> {
  await page.getByRole('button', { name: '使用邮箱验证码登录' }).click();
  await expect(page).toHaveURL(/#\/auth\/code-login\?redirect=%2Fprofile$/u);
  await page.getByPlaceholder('请输入绑定邮箱').fill(EMAIL);
  await page.getByRole('button', { name: '发送验证码' }).click();
  await expect(
    page.getByText('验证码已发送至 a***@example.com').first(),
  ).toBeVisible();
}

test('邮箱验证码单因素登录并保留 redirect', async ({ page }) => {
  await mockEmailLoginApi(page, 'email-only');
  await page.goto('/#/auth/login?redirect=%2Fprofile');
  await requestInitialEmailCode(page);
  await fillEmailCode(page, '123456');
  await page.getByRole('button', { name: '登录', exact: true }).click();

  await expect(page).toHaveURL(/#\/profile$/u);
  await expect(page.getByRole('button', { name: '邮件管理员' })).toBeVisible();
});

test('邮箱验证码登录后在同一 attempt 提交密码', async ({ page }) => {
  await mockEmailLoginApi(page, 'email-then-password');
  await page.goto('/#/auth/login?redirect=%2Fprofile');
  await requestInitialEmailCode(page);
  await fillEmailCode(page, '123456');
  await page.getByRole('button', { name: '登录', exact: true }).click();

  await expect(page).toHaveURL(/#\/auth\/login\?redirect=(?:%2F|\/)profile$/u);
  await expect(page.getByRole('heading', { name: '继续认证' })).toBeVisible();
  await page.getByRole('textbox', { name: '密码' }).fill('secret');
  await page.getByRole('button', { name: '继续登录' }).click();

  await expect(page).toHaveURL(/#\/profile$/u);
});

test('密码登录后在同一 attempt 提交邮箱验证码', async ({ page }) => {
  await mockEmailLoginApi(page, 'password-then-email');
  await page.goto('/#/auth/login?redirect=%2Fprofile');
  await page.getByPlaceholder('请输入用户名或邮箱').fill('admin');
  await page.getByPlaceholder('请输入密码').fill('secret');
  await page.getByRole('button', { name: 'login' }).click();

  await expect(page.getByRole('heading', { name: '继续认证' })).toBeVisible();
  await page.getByRole('button', { name: '发送验证码' }).click();
  await expect(
    page.getByText('验证码已发送至 a***@example.com').first(),
  ).toBeVisible();
  await fillEmailCode(page, '654321');
  await page.getByRole('button', { name: '继续登录' }).click();

  await expect(page).toHaveURL(/#\/profile$/u);
});

test('邮件通知页面管理 SMTP 配置和统一多语言模板', async ({ page }) => {
  let senders: Record<string, unknown>[] = [];
  let templates: Record<string, unknown>[] = [];
  let connectionTests = 0;

  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route: Route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname.replace(/^\/api/u, '');

      if (request.method() === 'POST' && path === '/login/attempts') {
        await route.fulfill({
          contentType: 'application/json',
          json: success(loginOutcome([], TOKENS)),
          status: 201,
        });
        return;
      }
      if (request.method() === 'GET' && path === '/system/email/senders') {
        await route.fulfill({
          contentType: 'application/json',
          json: success(senders),
        });
        return;
      }
      if (request.method() === 'POST' && path === '/system/email/senders') {
        expect(request.postDataJSON()).toEqual({
          fromAddress: 'noreply@example.com',
          fromName: null,
          host: 'localhost',
          name: '本地 Mailpit',
          password: null,
          port: 1025,
          replyTo: null,
          security: 'NONE',
          username: null,
        });
        const sender = {
          ...request.postDataJSON(),
          enabled: false,
          passwordConfigured: false,
          version: '0',
          senderId: '1',
        };
        senders = [sender];
        await route.fulfill({
          contentType: 'application/json',
          json: success(sender),
          status: 201,
        });
        return;
      }
      if (request.method() === 'GET' && path === '/system/email/senders/1') {
        await route.fulfill({
          contentType: 'application/json',
          headers: { ETag: '"sender-0"' },
          json: success(senders[0]),
        });
        return;
      }
      if (
        request.method() === 'POST' &&
        path === '/system/email/senders/1/connection-tests'
      ) {
        connectionTests += 1;
        await route.fulfill({ body: '', status: 204 });
        return;
      }
      if (
        request.method() === 'PATCH' &&
        path === '/system/email/senders/1/status'
      ) {
        expect(request.headers()['if-match']).toBe('"sender-0"');
        senders = [{ ...senders[0], enabled: true, version: '1' }];
        await route.fulfill({
          contentType: 'application/json',
          headers: { ETag: '"sender-1"' },
          json: success(senders[0]),
        });
        return;
      }
      if (request.method() === 'GET' && path === '/system/email/templates') {
        await route.fulfill({
          contentType: 'application/json',
          json: success(templates),
        });
        return;
      }
      if (request.method() === 'GET' && path === '/system/locales') {
        await route.fulfill({
          contentType: 'application/json',
          json: success({
            defaultLocale: 'zh-CN',
            supportedLocales: ['zh-CN', 'en-US'],
          }),
        });
        return;
      }
      if (request.method() === 'POST' && path === '/system/email/templates') {
        const input = request.postDataJSON();
        expect(input.notificationCode).toBe('authentication.email-code');
        expect(Object.keys(input.content)).toEqual(['zh-CN', 'fr-FR']);
        expect(JSON.stringify(input.content)).toContain('notificationVariable');
        const template = {
          ...input,
          enabled: false,
          version: '0',
          templateId: '2',
          variables: ['code', 'expiresInMinutes'],
        };
        templates = [template];
        await route.fulfill({
          contentType: 'application/json',
          json: success(template),
          status: 201,
        });
        return;
      }
      if (
        request.method() === 'POST' &&
        path === '/system/email/templates/previews'
      ) {
        expect(request.postDataJSON().languageTag).toBe('fr-FR');
        await route.fulfill({
          contentType: 'application/json',
          json: success({
            html: '<strong>123456</strong>',
            subject: 'Code de connexion 123456',
            text: 'Code 123456, valable 5 minutes.',
          }),
        });
        return;
      }
      if (await fulfillAuthenticatedShell(route)) return;

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

  await page.goto('/#/auth/login?redirect=%2Fsca4d-server%2Femail');
  await page.getByPlaceholder('请输入用户名或邮箱').fill('admin');
  await page.getByPlaceholder('请输入密码').fill('secret');
  await page.getByRole('button', { name: 'login' }).click();
  await expect(page).toHaveURL(/#\/sca4d-server\/email$/u);

  await expect(
    page.getByRole('button', { name: '创建 SMTP 配置' }),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: '创建 Email 模板' }),
  ).toHaveCount(0);
  await page.getByRole('button', { name: '创建 SMTP 配置' }).click();
  const senderDrawer = page
    .locator('.ant-drawer')
    .filter({ hasText: '创建 SMTP 配置' });
  await senderDrawer.getByLabel('名称').fill('本地 Mailpit');
  await senderDrawer.getByLabel('SMTP 主机').fill('localhost');
  await senderDrawer.getByLabel('发件邮箱').fill('noreply@example.com');
  await page.getByRole('button', { name: /保\s*存/u }).click();
  await expect(page.getByText('本地 Mailpit', { exact: true })).toBeVisible();

  const senderRow = page.getByRole('row').filter({ hasText: '本地 Mailpit' });
  await senderRow.getByRole('button', { name: '测试连接' }).click();
  await expect.poll(() => connectionTests).toBe(1);
  await senderRow.getByRole('switch').click();
  await expect(senderRow.getByRole('switch')).toBeChecked();

  await page.getByRole('tab', { name: 'Email 模板' }).click();
  await page.getByRole('button', { name: '创建 Email 模板' }).click();
  const templateDrawer = page
    .locator('.ant-drawer')
    .filter({ hasText: '创建 Email 模板' });
  await templateDrawer.getByLabel('通知编码').fill('authentication.email-code');
  await fillNotificationDocument(templateDrawer, '主题', '登录验证码 ', [
    'code',
  ]);
  await fillNotificationDocument(
    templateDrawer,
    '正文',
    '验证码 ， 分钟内有效。',
    ['code', 'expiresInMinutes'],
  );
  await templateDrawer.getByLabel('语言标签').fill('fr-fr');
  await page.getByRole('option', { name: 'fr-FR' }).click();
  await templateDrawer.getByRole('tab', { name: 'fr-FR' }).click();
  await fillNotificationDocument(templateDrawer, '主题', 'Code de connexion ', [
    'code',
  ]);
  await fillNotificationDocument(
    templateDrawer,
    '正文',
    'Code , valable  minutes.',
    ['code', 'expiresInMinutes'],
  );
  await page.getByRole('button', { name: /保\s*存/u }).click();
  await expect(
    page.getByText('authentication.email-code', { exact: true }),
  ).toBeVisible();

  const templateRow = page.getByRole('row').filter({
    hasText: 'authentication.email-code',
  });
  await templateRow.getByRole('button', { name: '预览' }).click();
  const previewDrawer = page.locator('.ant-drawer').filter({ hasText: '预览' });
  await previewDrawer.getByLabel('语言').click();
  await page.getByRole('option', { name: 'fr-FR' }).click();
  await page.getByRole('button', { name: '生成预览' }).click();
  await expect(
    page.getByText('Code de connexion 123456', { exact: true }),
  ).toBeVisible();
  await page.keyboard.press('Escape');
});

/** 向一个通知文档输入文本，并依次插入结构化变量节点。 */
async function fillNotificationDocument(
  drawer: ReturnType<Page['locator']>,
  label: string,
  text: string,
  variables: readonly string[],
): Promise<void> {
  const item = drawer
    .locator('.ant-form-item')
    .filter({ hasText: label })
    .last();
  await item.locator('[contenteditable="true"]').fill(text);
  for (const variable of variables) {
    await item.getByPlaceholder('变量名，例如 code').fill(variable);
    await item.getByRole('button', { name: '插入变量' }).click();
  }
}
