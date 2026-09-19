import type { Page, Route } from 'playwright/test';

import { expect, test } from 'playwright/test';

const ACCOUNT = {
  avatarFileId: null,
  createdAt: '2099-01-01T00:00:00.000Z',
  displayName: '目标账号',
  email: 'target@example.com',
  id: '41',
  phoneNumber: null,
  protectedRoot: false,
  status: 'ACTIVE',
  updatedAt: '2099-01-01T00:00:00.000Z',
  username: 'target',
};
const ROLE = {
  builtIn: false,
  code: 'AUDITOR',
  createdAt: '2099-01-01T00:00:00.000Z',
  id: '42',
  name: '审计员',
  status: 'ACTIVE',
  updatedAt: '2099-01-01T00:00:00.000Z',
};
const SENDER = {
  enabled: false,
  fromAddress: 'noreply@example.com',
  fromName: 'System',
  host: 'localhost',
  name: '默认 SMTP',
  passwordConfigured: false,
  port: 1025,
  replyTo: null,
  security: 'NONE',
  senderId: '61',
  username: null,
  version: '1',
};
const TEMPLATE = {
  enabled: false,
  i18n: {
    'zh-CN': { subject: '验证码', text: '您的验证码是 {{code}}' },
  },
  notificationCode: 'authentication.email-code',
  templateId: '62',
  variables: ['code'],
  version: '1',
};
const SESSION = {
  account: { accountId: '9007199254740993', realmKey: 'platform' },
  expiresAt: '2099-01-01T02:00:00.000Z',
  issuedAt: '2099-01-01T01:00:00.000Z',
  permissions: [
    'platform:accounts:read',
    'platform:roles:read',
    'system:audit:read',
    'system:email:read',
  ],
  sessionId: 'session-resource-reference',
  subject: { subjectId: '9007199254740995', type: 'ACCOUNT' },
};
const TOKENS = {
  accessToken: 'resource-reference-access-token',
  accessTokenExpiresAt: '2099-01-01T01:30:00.000Z',
  sessionExpiresAt: '2099-01-01T02:00:00.000Z',
  sessionId: 'session-resource-reference',
  sessionToken: 'resource-reference-session-token',
  tokenMode: 'JWT',
  tokenType: 'Bearer',
};

test('操作日志统一显示资源引用并打开可定位详情', async ({ page }) => {
  await mockResourceReferenceApi(page);
  await page.goto('/#/auth/login?redirect=%2Fsca4d-server%2Foperationlogs');
  await page.getByPlaceholder('请输入用户名或邮箱').fill('admin');
  await page.getByPlaceholder('请输入密码').fill('secret');
  await page.getByRole('button', { name: 'login' }).click();

  await expect(page).toHaveURL(/#\/sca4d-server\/operationlogs$/u);
  const accountLink = page.getByRole('link', { name: '账号', exact: true });
  await expect(accountLink).toBeVisible();
  await expect(accountLink.locator('xpath=..')).toContainText('41');

  const fileLink = page.getByRole('link', { name: '文件', exact: true });
  await expect(fileLink).toHaveAttribute(
    'href',
    'https://objects.example/avatar-51.png',
  );
  await expect(fileLink).toHaveAttribute('target', '_blank');

  await accountLink.click();
  await expect(page).toHaveURL(/#\/sca4d-server\/accounts\?accountId=41$/u);
  const accountDrawer = page
    .locator('.ant-drawer-content')
    .filter({ hasText: '账号设置' });
  await expect(
    accountDrawer.getByText('账号设置', { exact: true }),
  ).toBeVisible();
  await expect(
    accountDrawer.getByText('目标账号', { exact: true }),
  ).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page).toHaveURL(/#\/sca4d-server\/accounts$/u);

  await page.goto('/#/sca4d-server/roles?roleId=42');
  const roleDialog = page.getByRole('dialog');
  await expect(
    roleDialog.getByRole('textbox', { name: /角色编码/u }),
  ).toHaveValue('AUDITOR');
  await expect(
    roleDialog.getByRole('button', { name: '保 存' }),
  ).toBeDisabled();
  await roleDialog.getByRole('button', { name: '取 消' }).click();
  await expect(page).toHaveURL(/#\/sca4d-server\/roles$/u);

  await page.goto('/#/sca4d-server/email?tab=senders&senderId=61');
  await expect(page).toHaveURL(
    /#\/sca4d-server\/email\?tab=senders&senderId=61$/u,
  );
  const senderDrawer = page
    .locator('.ant-drawer-content')
    .filter({ hasText: 'SMTP 配置' });
  await expect(
    senderDrawer.getByText('SMTP 配置', { exact: true }),
  ).toBeVisible();
  await expect(
    senderDrawer.getByRole('textbox', { name: '* 名称', exact: true }),
  ).toHaveValue('默认 SMTP');
  await page.keyboard.press('Escape');
  await expect(page).toHaveURL(/#\/sca4d-server\/email\?tab=senders$/u);

  await page.goto('/#/sca4d-server/email?tab=templates&templateId=62');
  const templateDrawer = page
    .locator('.ant-drawer-content')
    .filter({ hasText: 'Email 模板' });
  await expect(
    templateDrawer.getByRole('textbox', { name: /通知编码/u }),
  ).toHaveValue('authentication.email-code');
  await page.keyboard.press('Escape');
  await expect(page).toHaveURL(/#\/sca4d-server\/email\?tab=templates$/u);
});

/** 为资源引用验收提供登录、日志、账号、角色、文件和 Email 响应。 */
async function mockResourceReferenceApi(page: Page): Promise<void> {
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route: Route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname.replace(/^\/api/u, '');

      if (request.method() === 'POST' && path === '/login/attempts') {
        await json(
          route,
          {
            authentication: {
              attemptId: 'resource-reference-attempt',
              availableMethods: [],
              expiresAt: '2099-01-01T01:05:00.000Z',
              remainingFactorCount: 0,
              status: 'SATISFIED',
            },
            session: TOKENS,
          },
          201,
        );
        return;
      }
      if (request.method() === 'GET' && path === '/sessions/current') {
        await json(route, SESSION);
        return;
      }
      if (request.method() === 'GET' && path === '/me/profile') {
        await json(route, {
          avatarFileId: null,
          displayName: '审计管理员',
          email: 'admin@example.com',
          phoneNumber: null,
          principalId: '71',
          updatedAt: '2099-01-01T00:00:00.000Z',
        });
        return;
      }
      if (path === '/events') {
        await route.fulfill({ body: '', status: 204 });
        return;
      }
      if (request.method() === 'GET' && path === '/system/audit/records') {
        await json(route, {
          items: [
            operationLog('1', 'account', '41'),
            operationLog('2', 'file', '51'),
          ],
          page: 1,
          size: 20,
          total: '2',
        });
        return;
      }
      if (request.method() === 'POST' && path === '/files/access-urls') {
        expect(request.postDataJSON()).toEqual({ fileIds: ['51'] });
        await json(route, [
          {
            expiresAt: '2099-01-01T01:30:00.000Z',
            fileId: '51',
            url: 'https://objects.example/avatar-51.png',
          },
        ]);
        return;
      }
      if (request.method() === 'GET' && path === '/platform/accounts') {
        await json(route, { items: [ACCOUNT], page: 1, size: 20, total: '1' });
        return;
      }
      if (request.method() === 'GET' && path === '/platform/accounts/41') {
        await json(route, ACCOUNT);
        return;
      }
      if (request.method() === 'GET' && path === '/platform/roles') {
        await json(route, [ROLE]);
        return;
      }
      if (request.method() === 'GET' && path === '/platform/roles/42') {
        await json(route, ROLE, 200, { etag: '"1"' });
        return;
      }
      if (request.method() === 'GET' && path === '/platform/roles/42/i18n') {
        await json(
          route,
          { i18n: { 'zh-CN': { name: '审计员' } }, roleId: '42' },
          200,
          { etag: '"1"' },
        );
        return;
      }
      if (
        request.method() === 'GET' &&
        path === '/platform/accounts/41/roles'
      ) {
        await json(route, { accountId: '41', roleIds: ['42'] }, 200, {
          etag: '"1"',
        });
        return;
      }
      if (
        request.method() === 'GET' &&
        path === '/system/dictionaries/iam.identity-status/options'
      ) {
        await json(route, {
          key: 'iam.identity-status',
          options: [
            {
              codeDefined: true,
              disabled: false,
              label: '启用',
              labelKey: 'dictionary.iam.identity-status.active',
              value: 'ACTIVE',
            },
          ],
        });
        return;
      }
      if (
        request.method() === 'GET' &&
        path === '/system/dictionaries/iam.role-status/options'
      ) {
        await json(route, {
          key: 'iam.role-status',
          options: [
            {
              codeDefined: true,
              disabled: false,
              label: '启用',
              labelKey: 'dictionary.iam.role-status.active',
              value: 'ACTIVE',
            },
          ],
        });
        return;
      }
      if (request.method() === 'GET' && path === '/system/email/senders') {
        await json(route, [SENDER]);
        return;
      }
      if (request.method() === 'GET' && path === '/system/email/senders/61') {
        await json(route, SENDER, 200, { etag: '"1"' });
        return;
      }
      if (request.method() === 'GET' && path === '/system/email/templates') {
        await json(route, [TEMPLATE]);
        return;
      }
      if (request.method() === 'GET' && path === '/system/email/templates/62') {
        await json(route, TEMPLATE, 200, { etag: '"1"' });
        return;
      }
      if (request.method() === 'GET' && path === '/system/locales') {
        await json(route, {
          defaultLocale: 'zh-CN',
          supportedLocales: ['zh-CN', 'en-US'],
        });
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

/** 返回一条带指定目标的最小操作日志。 */
function operationLog(id: string, targetType: string, targetId: string) {
  return {
    accountId: '41',
    clientIp: '127.0.0.1',
    httpMethod: 'PATCH',
    httpPath: '/platform/accounts/41',
    id,
    occurredAt: '2099-01-01T00:00:00.000Z',
    operation: 'platform/account/status/change',
    payloadJson: null,
    payloadType: null,
    payloadVersion: null,
    principalDisplayName: '审计管理员',
    principalId: '71',
    requestId: `request-${id}`,
    result: 'SUCCESS',
    subjectId: '72',
    targetId,
    targetType,
    userAgent: 'Playwright',
  };
}

/** 使用统一成功外壳返回 Mock JSON。 */
async function json(
  route: Route,
  data: unknown,
  status = 200,
  headers: Record<string, string> = {},
): Promise<void> {
  await route.fulfill({
    contentType: 'application/json',
    headers,
    json: { code: '0', data, message: null },
    status,
  });
}
