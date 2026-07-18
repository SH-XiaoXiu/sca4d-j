import { expect, type Locator, type Page } from "@playwright/test";
import type { IdentityProviderView, MailSenderView, MailTemplateView, ManagedMenuView, MenuWriteInput, NavigationRouteView } from "@sca4d/admin-common";

export const session = {
  sessionId: "session-current",
  tokenName: "Authorization",
  tokenValue: "e2e-token",
  subjectId: "10001",
  accountId: "10001",
  deviceId: "e2e-device",
  terminalName: "Playwright",
  loginTime: "2026-07-10T08:00:00+08:00",
  expireTime: "2026-08-10T08:00:00+08:00"
};

export async function mockApi(page: Page): Promise<{
  setAvatarReadUrl: (url: string) => void;
  failSignatureReads: () => void;
  failNextMeRefresh: () => void;
}> {
  await page.addInitScript((value) => {
    sessionStorage.setItem("sca4d-admin.session.tab", JSON.stringify({ session: value, remember: false }));
  }, session);
  let menuConflictPending = true;
  let menuOrderConflictPending = true;
  let dictionaryOrderConflictPending = true;
  let nextMenuId = 90001;
  const schedules = [
    {
      scheduleId: "80001",
      name: "每周维护通知",
      scheduleType: "CRON",
      executeAt: null,
      cronExpression: "0 0 9 ? * MON",
      timeZone: "Asia/Shanghai",
      status: "ACTIVE",
      taskType: "notification.sse",
      taskPayload: {
        title: "维护提醒",
        content: "本周例行维护将在今晚进行。",
        category: "SYSTEM",
        recipients: [{ subjectId: "10001", principalType: "DEFAULT", principalId: "10001" }]
      },
      taskPayloadVersion: 1,
      synchronizationError: null,
      createdByPrincipalId: "11001",
      version: "1",
      createdAt: "2026-07-10T08:00:00+08:00",
      updatedAt: "2026-07-14T08:00:00+08:00"
    }
  ];
  const mailSenders: MailSenderView[] = [
    {
      senderId: "61001",
      name: "Mailpit",
      host: "127.0.0.1",
      port: 1025,
      security: "NONE",
      username: "mailer",
      passwordConfigured: true,
      fromAddress: "no-reply@example.test",
      fromName: "SCA4D",
      replyTo: "support@example.test",
      status: "DISABLED",
      version: "0"
    },
    {
      senderId: "61003",
      name: "Transactional SMTP",
      host: "127.0.0.1",
      port: 1025,
      security: "NONE",
      username: null,
      passwordConfigured: false,
      fromAddress: "transactional@example.test",
      fromName: "SCA4D",
      replyTo: null,
      status: "ACTIVE",
      version: "1"
    }
  ];
  const mailTemplates: MailTemplateView[] = [
    {
      templateId: "62001",
      family: "auth.email-code",
      locale: "zh-CN",
      subjectTemplate: "验证码 {{code}}",
      textTemplate: "你好，{{displayName}}。验证码是 {{code}}",
      htmlTemplate: "<h1>验证码</h1><p>你好，{{displayName}}</p><strong>{{code}}</strong>",
      variableNames: ["code", "displayName"],
      status: "DISABLED",
      version: "0"
    }
  ];
  const identityProviders: IdentityProviderView[] = [
    {
      providerId: "63001",
      code: "oidc-e2e",
      name: "OIDC E2E",
      scopeType: "GLOBAL",
      scopeId: null,
      protocol: "OIDC",
      registrationPolicy: "JIT",
      emailAutoLink: false,
      status: "DISABLED",
      version: "1"
    },
    {
      providerId: "63002",
      code: "oauth2-e2e",
      name: "OAuth2 E2E",
      scopeType: "GLOBAL",
      scopeId: null,
      protocol: "OAUTH2",
      registrationPolicy: "JIT",
      emailAutoLink: false,
      status: "ACTIVE",
      version: "2"
    },
    {
      providerId: "63003",
      code: "ldap-e2e",
      name: "LDAP E2E",
      scopeType: "GLOBAL",
      scopeId: null,
      protocol: "LDAP",
      registrationPolicy: "DISABLED",
      emailAutoLink: false,
      status: "DISABLED",
      version: "3"
    }
  ];
  const avatarDataUrl =
    "data:image/webp;base64,UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJQBOgCHwAP7+4AAAAA==";
  let avatarReadUrl = avatarDataUrl;
  let nextAvatarFileId = 50001;
  let currentAvatar: { scene: string; resourceId: string; fileId: string } | null = null;
  let currentProfileVersion = 1;
  let platformRoleConflictPending = true;
  let signatureReadsFail = false;
  let nextMeRefreshFails = false;
  let stepUpSequence = 0;
  const accountSessions: Record<string, Array<Record<string, string | null>>> = {
    "10001": [
      {
        sessionId: "session-current",
        deviceId: "e2e-device",
        terminalName: "Playwright",
        loginTime: "2026-07-10T08:00:00+08:00"
      },
      {
        sessionId: "session-password",
        deviceId: "desktop-firefox",
        terminalName: "Firefox on Linux",
        loginTime: "2026-07-11T09:00:00+08:00"
      },
      {
        sessionId: "session-email",
        deviceId: "desktop-edge",
        terminalName: "Edge on Windows",
        loginTime: "2026-07-12T10:00:00+08:00"
      }
    ],
    "10002": [
      {
        sessionId: "session-tenant",
        deviceId: "mobile-safari",
        terminalName: "Safari on iPhone",
        loginTime: "2026-07-13T11:00:00+08:00"
      }
    ]
  };
  const managedAccounts = [
    {
      accountId: "10001",
      subjectId: "10001",
      defaultPrincipalId: "10011",
      displayName: "平台管理员",
      primaryIdentifier: "admin",
      avatar: null,
      subjectVersion: "1",
      profileVersion: "1",
      scopeType: "GLOBAL",
      scopeId: null,
      status: "ACTIVE",
      secondFactorRequired: true,
      version: "1",
      createdAt: "2026-07-10T08:00:00+08:00",
      updatedAt: "2026-07-16T08:00:00+08:00"
    },
    {
      accountId: "10002",
      subjectId: "10002",
      defaultPrincipalId: "10012",
      displayName: "租户管理员",
      primaryIdentifier: "tenant-admin",
      avatar: null,
      subjectVersion: "0",
      profileVersion: "0",
      scopeType: "TENANT",
      scopeId: "20001",
      status: "ACTIVE",
      secondFactorRequired: false,
      version: "0",
      createdAt: "2026-07-10T08:00:00+08:00",
      updatedAt: "2026-07-10T08:00:00+08:00"
    }
  ];
  const managedAccountSecurity: Record<string, Record<string, unknown>> = {
    "10001": {
      accountId: "10001",
      scopeType: "GLOBAL",
      scopeId: null,
      status: "ACTIVE",
      secondFactorRequired: true,
      version: "1",
      recoveryCodesIssuedAt: "2026-07-16T08:00:00+08:00",
      recoveryCodesRemaining: 8,
      identifiers: [{ identifierId: "30001", identifierType: "USERNAME", value: "admin", status: "ACTIVE", version: "0" }],
      credentials: [{ credentialId: "40001", credentialType: "PASSWORD", status: "ACTIVE", createdAt: "2026-07-10T08:00:00+08:00", updatedAt: "2026-07-14T08:00:00+08:00", version: "1" }],
      passkeyCredentials: []
    },
    "10002": {
      accountId: "10002",
      scopeType: "TENANT",
      scopeId: "20001",
      status: "ACTIVE",
      secondFactorRequired: false,
      version: "0",
      recoveryCodesIssuedAt: null,
      recoveryCodesRemaining: 0,
      identifiers: [{ identifierId: "30002", identifierType: "USERNAME", value: "tenant-admin", status: "ACTIVE", version: "0" }],
      credentials: [{ credentialId: "40002", credentialType: "PASSWORD", status: "ACTIVE", createdAt: "2026-07-10T08:00:00+08:00", updatedAt: "2026-07-10T08:00:00+08:00", version: "0" }],
      passkeyCredentials: []
    }
  };
  const platformOperators = [{
    principalId: "11001",
    displayName: "平台管理员",
    status: "ACTIVE",
    version: "1",
    roles: [{ roleId: "30001", code: "PLATFORM_ADMIN", name: "平台管理员" }]
  }];
  const dictionaryTypeOrder = [
    { id: "51001", label: "字典状态", description: "system.dictionary-status", version: "1" },
    { id: "51002", label: "客户类型", description: "customer.type", version: "2" }
  ];
  const dictionaryItemOrder = [
    { id: "52001", label: "重要客户", description: "VIP", version: "3" },
    { id: "52002", label: "合作伙伴", description: "PARTNER", version: "1" }
  ];
  const menus: ManagedMenuView[] = [
    {
      menuId: "-400",
      parentId: null,
      routeCode: null,
      pathTemplate: null,
      permissionCode: null,
      titles: { "zh-CN": "系统管理", "en-US": "System" },
      icon: "MENU",
      sortOrder: 40,
      visible: true,
      version: "0",
      children: [
        {
          menuId: "-420",
          parentId: "-400",
          routeCode: "system.menus",
          pathTemplate: "/system/menus",
          permissionCode: "system:menus:read",
          titles: { "zh-CN": "菜单管理", "en-US": "Menus" },
          icon: "MENUS",
          sortOrder: 20,
          visible: true,
          version: "0",
          children: []
        },
        {
          menuId: "-425",
          parentId: "-400",
          routeCode: "system.scheduled-jobs",
          pathTemplate: "/system/scheduled-jobs",
          permissionCode: "system:scheduled-jobs:read",
          titles: { "zh-CN": "系统任务", "en-US": "System jobs" },
          icon: "SYSTEM_JOBS",
          sortOrder: 25,
          visible: true,
          version: "0",
          children: []
        },
        {
          menuId: "-430",
          parentId: "-400",
          routeCode: "system.operation-logs",
          pathTemplate: "/system/operation-logs",
          permissionCode: "system:operation-logs:read",
          titles: { "zh-CN": "操作日志", "en-US": "Operation logs" },
          icon: "AUDIT_LOGS",
          sortOrder: 30,
          visible: true,
          version: "0",
          children: []
        },
        {
          menuId: "-435",
          parentId: "-400",
          routeCode: "system.mail-senders",
          pathTemplate: "/system/mail-senders",
          permissionCode: "system:mail-senders:read",
          titles: { "zh-CN": "邮件发送配置", "en-US": "Mail senders" },
          icon: "MailCheck",
          sortOrder: 28,
          visible: true,
          version: "0",
          children: []
        },
        {
          menuId: "-436",
          parentId: "-400",
          routeCode: "system.mail-templates",
          pathTemplate: "/system/mail-templates",
          permissionCode: "system:mail-templates:read",
          titles: { "zh-CN": "邮件模板", "en-US": "Mail templates" },
          icon: "FileText",
          sortOrder: 29,
          visible: true,
          version: "0",
          children: []
        }
      ]
    }
  ];
  const menuRoutes: NavigationRouteView[] = [
    {
      code: "workspace.dashboard",
      pathTemplate: "/dashboard",
      principalTypes: ["DEFAULT", "PLATFORM", "TENANT"],
      permissionCode: null,
      defaultTitles: { "zh-CN": "工作台", "en-US": "Dashboard" },
      defaultIcon: "DASHBOARD"
    },
    {
      code: "system.menus",
      pathTemplate: "/system/menus",
      principalTypes: ["PLATFORM"],
      permissionCode: "system:menus:read",
      defaultTitles: { "zh-CN": "菜单管理", "en-US": "Menus" },
      defaultIcon: "MENUS"
    },
    {
      code: "system.scheduled-jobs",
      pathTemplate: "/system/scheduled-jobs",
      principalTypes: ["PLATFORM"],
      permissionCode: "system:scheduled-jobs:read",
      defaultTitles: { "zh-CN": "系统任务", "en-US": "System jobs" },
      defaultIcon: "SYSTEM_JOBS"
    },
    {
      code: "system.operation-logs",
      pathTemplate: "/system/operation-logs",
      principalTypes: ["PLATFORM"],
      permissionCode: "system:operation-logs:read",
      defaultTitles: { "zh-CN": "操作日志", "en-US": "Operation logs" },
      defaultIcon: "AUDIT_LOGS"
    },
    {
      code: "system.mail-senders",
      pathTemplate: "/system/mail-senders",
      principalTypes: ["PLATFORM"],
      permissionCode: "system:mail-senders:read",
      defaultTitles: { "zh-CN": "邮件发送配置", "en-US": "Mail senders" },
      defaultIcon: "MailCheck"
    },
    {
      code: "system.mail-templates",
      pathTemplate: "/system/mail-templates",
      principalTypes: ["PLATFORM"],
      permissionCode: "system:mail-templates:read",
      defaultTitles: { "zh-CN": "邮件模板", "en-US": "Mail templates" },
      defaultIcon: "FileText"
    }
  ];
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api/, "");
    const method = route.request().method();
    const english = route.request().headers()["accept-language"]?.startsWith("en") ?? false;
    let data: unknown = null;
    if (path === "/authentication-methods") {
      data = [
        { type: "PASSWORD", capabilities: ["LOGIN", "STEP_UP"] },
        { type: "TOTP", capabilities: ["STEP_UP"] },
        { type: "EMAIL_CODE", capabilities: ["STEP_UP"] }
      ];
    } else if (path === "/authentications" && method === "POST") {
      stepUpSequence += 1;
      data = {
        authenticationId: `authentication-step-up-${stepUpSequence}`,
        purpose: "STEP_UP",
        activityId: "primary-proof",
        subjectId: "10001",
        accountId: "10001",
        expiresAt: "2026-07-17T01:00:00Z"
      };
    } else if (/^\/authentications\/authentication-step-up-\d+\/password-proofs$/.test(path)) {
      expect(route.request().postDataJSON()).toEqual({
        identifierType: "USERNAME",
        identifier: "admin",
        password: "current-secret"
      });
      data = {
        authenticationId: path.split("/")[2],
        purpose: "STEP_UP",
        activityId: "completion",
        subjectId: "10001",
        accountId: "10001",
        expiresAt: "2026-07-17T01:00:00Z"
      };
    } else if (/^\/authentications\/authentication-step-up-\d+\/email-code-challenges$/.test(path)) {
      data = {
        challengeId: `challenge-${stepUpSequence}`,
        maskedRecipient: "ad***@example.test",
        expiresAt: "2026-07-17T01:00:00Z",
        resendAt: "2026-07-17T00:30:00Z"
      };
    } else if (/^\/authentications\/authentication-step-up-\d+\/email-code-proofs$/.test(path)) {
      expect(route.request().postDataJSON()).toEqual({
        challengeId: `challenge-${stepUpSequence}`,
        code: "123456"
      });
      data = {
        authenticationId: path.split("/")[2],
        purpose: "STEP_UP",
        activityId: "completion",
        subjectId: "10001",
        accountId: "10001",
        expiresAt: "2026-07-17T01:00:00Z"
      };
    } else if (/^\/authentications\/authentication-step-up-\d+\/totp-proofs$/.test(path)) {
      expect(route.request().postDataJSON()).toEqual({ code: "654321" });
      data = {
        authenticationId: path.split("/")[2],
        purpose: "STEP_UP",
        activityId: "completion",
        subjectId: "10001",
        accountId: "10001",
        expiresAt: "2026-07-17T01:00:00Z"
      };
    } else if (path === "/me/accounts") {
      data = [
        {
          accountId: "10001",
          scopeType: "GLOBAL",
          scopeId: null,
          status: "ACTIVE",
          secondFactorRequired: true,
          version: "1",
          recoveryCodesIssuedAt: "2026-07-16T08:00:00+08:00",
          recoveryCodesRemaining: 8,
          identifiers: [
            {
              identifierId: "30001",
              identifierType: "USERNAME",
              value: "admin",
              status: "ACTIVE",
              version: "0"
            },
            {
              identifierId: "30002",
              identifierType: "EMAIL",
              value: "admin@example.test",
              status: "ACTIVE",
              version: "0"
            }
          ],
          credentials: [
            {
              credentialId: "40001",
              credentialType: "PASSWORD",
              status: "ACTIVE",
              createdAt: "2026-07-10T08:00:00+08:00",
              updatedAt: "2026-07-14T08:00:00+08:00",
              version: "1"
            },
            {
              credentialId: "40002",
              credentialType: "TOTP",
              status: "ACTIVE",
              createdAt: "2026-07-15T08:00:00+08:00",
              updatedAt: "2026-07-15T08:00:00+08:00",
              version: "0"
            }
          ],
          passkeyCredentials: []
        },
        {
          accountId: "10002",
          scopeType: "TENANT",
          scopeId: "20001",
          status: "ACTIVE",
          secondFactorRequired: false,
          version: "0",
          recoveryCodesIssuedAt: null,
          recoveryCodesRemaining: 0,
          identifiers: [
            {
              identifierId: "30003",
              identifierType: "USERNAME",
              value: "tenant-admin",
              status: "ACTIVE",
              version: "0"
            }
          ],
          credentials: [],
          passkeyCredentials: []
        }
      ];
    } else if (/^\/me\/accounts\/\d+\/totp\/enrollments$/.test(path) && method === "POST") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      data = {
        enrollmentId: `enrollment-${stepUpSequence}`,
        expiresAt: "2026-07-17T01:00:00Z"
      };
    } else if (/^\/me\/accounts\/\d+\/totp\/enrollments\/[^/]+\/qr-code$/.test(path) && method === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "image/png",
        body: Buffer.from(
          "iVBORw0KGgoAAAANSUhEUgAAAB0AAAAdAQAAAAB+6FqiAAAAbElEQVR4XmPIuro6gKEhbAUHw77Zxi8YlGyqFBgW/f+lwaBU8UmBYd/m5BcMDFdXMDCsDlsbwXB2tlkGg5xNxQeG/////2Cwqfj4gOHm5rQTDGBT8CthACnZB1KyCKRECaRkEUiJHUhJA8wUAA5jQldqdfpzAAAAAElFTkSuQmCC",
          "base64"
        )
      });
      return;
    } else if (/^\/me\/accounts\/\d+\/totp\/credentials$/.test(path) && method === "POST") {
      expect(route.request().postDataJSON()).toMatchObject({ code: "111111" });
      data = { credentialId: "40003", version: "0" };
    } else if (/^\/me\/accounts\/\d+\/totp\/credentials\/40003$/.test(path) && method === "PUT") {
      expect(route.request().postDataJSON()).toMatchObject({ code: "222222" });
      expect(route.request().headers()["if-match"]).toBe('"0"');
      data = { credentialId: "40003", version: "1" };
    } else if (/^\/me\/accounts\/\d+\/totp\/credentials\/40003$/.test(path) && method === "DELETE") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      expect(route.request().headers()["if-match"]).toBe('"1"');
      await route.fulfill({ status: 204, body: "" });
      return;
    } else if (/^\/me\/accounts\/\d+\/second-factor\/requirement$/.test(path) && method === "PUT") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      const accountId = path.split("/")[3] ?? "";
      const input = route.request().postDataJSON() as { required: boolean; version: string };
      accountSessions[accountId] = [];
      data = {
        required: input.required,
        accountVersion: String(Number(input.version) + 1),
        recoveryCodesIssuedAt: input.required ? "2026-07-17T10:30:00+08:00" : null,
        recoveryCodesRemaining: input.required ? 10 : 0,
        recoveryCodes: input.required
          ? Array.from({ length: 10 }, (_, index) =>
              `${String(index).padStart(5, "A")}-BBBBB-CCCCC-DDDDD`
            )
          : []
      };
    } else if (/^\/me\/accounts\/\d+\/external-identities$/.test(path) && method === "GET") {
      data = [];
    } else if (/^\/me\/accounts\/\d+\/external-identities\/providers$/.test(path) && method === "GET") {
      data = [];
    } else if (/^\/me\/accounts\/\d+\/sessions$/.test(path)) {
      data = accountSessions[path.split("/")[3] ?? ""] ?? [];
    } else if (/^\/me\/accounts\/\d+\/sessions\/[^/]+$/.test(path) && method === "DELETE") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      const accountId = path.split("/")[3] ?? "";
      const sessionId = decodeURIComponent(path.split("/")[5] ?? "");
      accountSessions[accountId] = (accountSessions[accountId] ?? []).filter(
        (candidate) => candidate.sessionId !== sessionId
      );
      await route.fulfill({ status: 204, body: "" });
      return;
    } else if (path === "/mg/system/auth/providers" && method === "GET") {
      data = identityProviders;
    } else if (path === "/mg/system/auth/providers/63001/oidc" && method === "GET") {
      data = {
        providerId: "63001",
        issuer: "https://oidc.example.test",
        clientId: "oidc-client",
        clientSecretConfigured: true,
        scopes: ["openid", "profile", "email"],
        version: "1"
      };
    } else if (path === "/mg/system/auth/providers/63001/oidc/discoveries" && method === "POST") {
      data = {
        issuer: "https://oidc.example.test",
        authorizationUri: "https://oidc.example.test/authorize",
        tokenUri: "https://oidc.example.test/token",
        userInfoUri: "https://oidc.example.test/userinfo",
        jwksUri: "https://oidc.example.test/jwks"
      };
    } else if (path === "/mg/system/auth/providers/63002/oauth2" && method === "GET") {
      data = {
        providerId: "63002",
        authorizationUri: "https://oauth2.example.test/authorize",
        tokenUri: "https://oauth2.example.test/token",
        profileUri: "https://oauth2.example.test/profile",
        auxiliaryProfileUri: null,
        clientId: "oauth2-client",
        clientSecretConfigured: true,
        scopes: ["profile", "email"],
        profileMapping: {
          subjectPath: "$.id",
          displayNamePath: "$.name",
          emailPath: "$.email",
          emailVerifiedPath: "$.email_verified",
          avatarPath: null
        },
        version: "2"
      };
    } else if (path === "/mg/system/auth/providers/63002/oauth2/previews" && method === "POST") {
      data = {
        subject: "alice",
        displayName: "Alice",
        email: "alice@example.test",
        emailVerified: true,
        avatar: null,
        missingFields: []
      };
    } else if (path === "/mg/system/auth/providers/63003/ldap" && method === "GET") {
      data = {
        providerId: "63003",
        url: "ldap://directory.example.test:389",
        baseDn: "dc=example,dc=test",
        serviceAccountDn: "cn=reader,dc=example,dc=test",
        serviceAccountPasswordConfigured: true,
        userSearchBase: "ou=people",
        userSearchFilter: "(uid={0})",
        externalSubjectAttribute: "entryUUID",
        displayNameAttribute: "displayName",
        emailAttribute: "mail",
        transportSecurity: "STARTTLS",
        version: "3"
      };
    } else if (path === "/mg/system/auth/providers/63003/ldap/tests" && method === "POST") {
      data = null;
    } else if (path === "/me") {
      if (nextMeRefreshFails) {
        nextMeRefreshFails = false;
        await route.fulfill({
          status: 503,
          contentType: "application/json",
          body: JSON.stringify({
            code: 1000,
            messageKey: "error.system.unavailable",
            messageArgs: [],
            message: "默认资料暂时无法刷新",
            data: null,
            traceId: "me-refresh-failed",
            timestamp: String(Date.now())
          })
        });
        return;
      }
      const principalType = url.searchParams.get("principalType") ?? "DEFAULT";
      const tenantId = url.searchParams.get("tenantId");
      const permissions =
        principalType === "TENANT"
          ? ["tenant:members:read", "tenant:members:create", "tenant:members:update"]
          : principalType === "PLATFORM"
            ? [
                "platform:accounts:read",
                "platform:accounts:create",
                "platform:accounts:update",
                "platform:accounts:delete",
                "account.security.read",
                "account.credentials.manage",
                "account.mfa.reset",
                "account.recovery.manage",
                "account.sessions.read",
                "account.sessions.revoke",
                "platform:operators:read",
                "platform:operators:manage",
                "platform:roles:read",
                "platform:roles:manage",
                "platform:schedules:read",
                "platform:schedules:manage",
                "platform:tenants:read",
                "platform:tenants:create",
                "platform:tenants:update",
                "platform:tenants:delete",
                "platform:tenants:members:read",
                "platform:tenants:members:create",
                "platform:tenants:members:update",
                "platform:tenants:roles:read",
                "platform:tenants:roles:manage",
                "system:menus:read",
                "system:menus:manage",
                "system:dictionaries:read",
                "system:dictionaries:manage",
                "system:mail-senders:read",
                "system:mail-senders:manage",
                "system:mail-templates:read",
                "system:mail-templates:manage",
                "system:identity-providers:read",
                "system:identity-providers:manage",
                "system:scheduled-jobs:read",
                "system:operation-logs:read"
              ]
            : [];
      if (principalType === "TENANT") {
        permissions.push("tenant:roles:read", "tenant:roles:manage");
      }
      data = {
        subject: { subjectId: "10001", displayName: "平台管理员" },
        account: { accountId: "10001", scopeType: "GLOBAL", scopeId: null },
        avatar: currentAvatar,
        profileVersion: String(currentProfileVersion),
        principal: {
          principalId: principalType === "TENANT" ? "12001" : principalType === "PLATFORM" ? "11001" : "10001",
          principalType,
          tenantId: principalType === "TENANT" ? tenantId : null,
          displayName: "平台管理员"
        },
        session: { ...session, tokenValue: undefined },
        roles: principalType === "PLATFORM" ? ["PLATFORM_ADMIN"] : [],
        permissions,
        availablePrincipals: [
          { principalId: "10001", principalType: "DEFAULT", tenantId: null, displayName: "平台管理员", status: "ACTIVE" },
          { principalId: "11001", principalType: "PLATFORM", tenantId: null, displayName: "平台管理员", status: "ACTIVE" },
          { principalId: "12001", principalType: "TENANT", tenantId: "20001", displayName: "平台管理员", status: "ACTIVE" }
        ],
        tenants: [
          {
            principalId: "12001",
            tenantId: "20001",
            tenantCode: "acme",
            tenantName: "示例租户",
            tenantType: "DEFAULT",
            tenantStatus: "ACTIVE",
            membershipStatus: "ACTIVE"
          }
        ],
        navigation:
          principalType === "TENANT"
            ? [
                {
                  menuId: "-310",
                  routeCode: "tenant.members",
                  path: `/tenant/tenants/${tenantId}/members`,
                  title: "成员管理",
                  icon: "USERS",
                  children: []
                }
              ]
            : principalType === "PLATFORM"
              ? [
                  {
                    menuId: "-100",
                    routeCode: "workspace.dashboard",
                    path: "/dashboard",
                    title: "工作台",
                    icon: "DASHBOARD",
                    children: []
                  },
                  {
                    menuId: "-400",
                    routeCode: null,
                    path: null,
                    title: "系统管理",
                    icon: "MENU",
                    children: [
                      {
                        menuId: "-420",
                        routeCode: "system.menus",
                        path: "/system/menus",
                        title: "菜单管理",
                        icon: "MENUS",
                        children: []
                      },
                      {
                        menuId: "-425",
                        routeCode: "system.scheduled-jobs",
                        path: "/system/scheduled-jobs",
                        title: "系统任务",
                        icon: "SYSTEM_JOBS",
                        children: []
                      },
                      {
                        menuId: "-430",
                        routeCode: "system.operation-logs",
                        path: "/system/operation-logs",
                        title: "操作日志",
                        icon: "AUDIT_LOGS",
                        children: []
                      },
                      {
                        menuId: "-435",
                        routeCode: "system.mail-senders",
                        path: "/system/mail-senders",
                        title: "邮件发送配置",
                        icon: "MailCheck",
                        children: []
                      },
                      {
                        menuId: "-436",
                        routeCode: "system.mail-templates",
                        path: "/system/mail-templates",
                        title: "邮件模板",
                        icon: "FileText",
                        children: []
                      },
                      {
                        menuId: "-437",
                        routeCode: "system.identity-providers",
                        path: "/system/identity-providers",
                        title: "身份源",
                        icon: "Network",
                        children: []
                      }
                    ]
                  },
                  {
                    menuId: "-240",
                    routeCode: "platform.schedules",
                    path: "/platform/schedules",
                    title: "业务计划",
                    icon: "SCHEDULES",
                    children: []
                  }
                ]
              : [
                  {
                    menuId: "-100",
                    routeCode: "workspace.dashboard",
                    path: "/dashboard",
                    title: "工作台",
                    icon: "DASHBOARD",
                    children: []
                  }
                ]
      };
    } else if (path === "/system/languages") {
      data = [
        { code: "zh-CN", nativeName: "简体中文", defaultLanguage: true, required: true },
        { code: "en-US", nativeName: "English", defaultLanguage: false, required: true },
        ...Array.from({ length: 18 }, (_, index) => ({
          code: `x-${index + 1}`,
          nativeName: `Language ${index + 1}`,
          defaultLanguage: false,
          required: false
        }))
      ];
    } else if (path === "/mg/platform/accounts" && method === "GET") {
      const search = (url.searchParams.get("search") ?? "").toLowerCase();
      const status = url.searchParams.get("status");
      const records = managedAccounts.filter((account) =>
        account.scopeType === "GLOBAL"
          && (!status || account.status === status)
          && (!search
            || account.displayName.toLowerCase().includes(search)
            || account.primaryIdentifier?.toLowerCase().includes(search)
            || account.accountId === search)
      );
      data = {
        records,
        page: "1",
        size: "20",
        total: String(records.length)
      };
    } else if (path === "/mg/platform/accounts" && method === "POST") {
      const input = route.request().postDataJSON() as { displayName: string; identifier: string };
      const accountId = "10003";
      managedAccounts.push({
        accountId,
        subjectId: "10003",
        defaultPrincipalId: "10013",
        displayName: input.displayName,
        primaryIdentifier: input.identifier,
        avatar: null,
        subjectVersion: "0",
        profileVersion: "0",
        scopeType: "GLOBAL",
        scopeId: null,
        status: "ACTIVE",
        secondFactorRequired: false,
        version: "0",
        createdAt: "2026-07-18T12:00:00+08:00",
        updatedAt: "2026-07-18T12:00:00+08:00"
      });
      managedAccountSecurity[accountId] = {
        accountId,
        scopeType: "GLOBAL",
        scopeId: null,
        status: "ACTIVE",
        secondFactorRequired: false,
        version: "0",
        recoveryCodesIssuedAt: null,
        recoveryCodesRemaining: 0,
        identifiers: [],
        credentials: [],
        passkeyCredentials: []
      };
      data = accountId;
    } else if (/^\/mg\/(?:platform\/accounts|(?:platform|tenant)\/tenants\/20001\/accounts)$/.test(path) && method === "GET") {
      data = {
        records: managedAccounts.filter((account) => account.scopeType === "TENANT" && account.scopeId === "20001"),
        page: "1",
        size: "20",
        total: String(managedAccounts.filter((account) => account.scopeType === "TENANT" && account.scopeId === "20001").length)
      };
    } else if (/^\/mg\/platform\/accounts\/[^/]+\/profile$/.test(path) && method === "GET") {
      const account = managedAccounts.find((item) => item.accountId === path.split("/").at(-2));
      data = account ? {
        accountId: account.accountId,
        subjectId: account.subjectId,
        displayName: account.displayName,
        phone: "13800000000",
        email: `${account.primaryIdentifier ?? "account"}@example.test`,
        avatar: account.avatar,
        profileVersion: account.profileVersion,
        subjectVersion: account.subjectVersion
      } : null;
    } else if (/^\/mg\/platform\/accounts\/[^/]+\/profile$/.test(path) && method === "PUT") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      const account = managedAccounts.find((item) => item.accountId === path.split("/").at(-2));
      const input = route.request().postDataJSON() as { displayName: string };
      if (account) {
        account.displayName = input.displayName;
        account.subjectVersion = String(Number(account.subjectVersion) + 1);
        account.updatedAt = "2026-07-18T12:30:00+08:00";
      }
      data = null;
    } else if (/^\/mg\/(?:platform\/accounts|(?:platform|tenant)\/tenants\/20001\/accounts)\/[^/]+\/security$/.test(path)) {
      data = managedAccountSecurity[path.split("/").at(-2) ?? ""] ?? null;
    } else if (/^\/mg\/(?:platform\/accounts|(?:platform|tenant)\/tenants\/20001\/accounts)\/[^/]+\/sessions$/.test(path) && method === "GET") {
      data = accountSessions[path.split("/").at(-2) ?? ""] ?? [];
    } else if (/^\/mg\/(?:platform\/accounts|(?:platform|tenant)\/tenants\/20001\/accounts)\/[^/]+\/second-factor\/requirement$/.test(path) && method === "PUT") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      expect(route.request().headers()["if-match"]).toMatch(/^"\d+"$/);
      const accountId = path.split("/").at(-3) ?? "";
      const input = route.request().postDataJSON() as { required: boolean };
      const account = managedAccounts.find((item) => item.accountId === accountId);
      const security = managedAccountSecurity[accountId];
      if (account && security) {
        account.secondFactorRequired = input.required;
        account.version = String(Number(account.version) + 1);
        security.secondFactorRequired = input.required;
        security.version = account.version;
        security.recoveryCodesIssuedAt = input.required ? "2026-07-18T10:30:00+08:00" : null;
        security.recoveryCodesRemaining = input.required ? 10 : 0;
      }
      accountSessions[accountId] = [];
      data = {
        required: input.required,
        accountVersion: account?.version ?? "0",
        recoveryCodesIssuedAt: security?.recoveryCodesIssuedAt ?? null,
        recoveryCodesRemaining: security?.recoveryCodesRemaining ?? 0,
        recoveryCodes: input.required ? Array.from({ length: 10 }, (_, index) => `AAAA${index}-BBBBB-CCCCC-DDDDD`) : []
      };
    } else if (/^\/mg\/(?:platform\/accounts|(?:platform|tenant)\/tenants\/20001\/accounts)\/[^/]+\/recovery-codes$/.test(path) && method === "POST") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      expect(route.request().headers()["if-match"]).toMatch(/^"\d+"$/);
      const accountId = path.split("/").at(-2) ?? "";
      const account = managedAccounts.find((item) => item.accountId === accountId);
      const security = managedAccountSecurity[accountId];
      if (account && security) {
        account.version = String(Number(account.version) + 1);
        security.version = account.version;
        security.recoveryCodesIssuedAt = "2026-07-18T11:30:00+08:00";
        security.recoveryCodesRemaining = 10;
      }
      accountSessions[accountId] = [];
      data = {
        required: account?.secondFactorRequired ?? false,
        accountVersion: account?.version ?? "0",
        recoveryCodesIssuedAt: security?.recoveryCodesIssuedAt ?? null,
        recoveryCodesRemaining: security?.recoveryCodesRemaining ?? 0,
        recoveryCodes: Array.from({ length: 10 }, (_, index) => `CCCC${index}-DDDD-EEEE-FFFF`)
      };
    } else if (/^\/mg\/(?:platform\/accounts|(?:platform|tenant)\/tenants\/20001\/accounts)\/[^/]+\/credentials\/password$/.test(path) && method === "POST") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      expect(route.request().headers()["if-match"]).toMatch(/^"\d+"$/);
      const accountId = path.split("/").at(-3) ?? "";
      const security = managedAccountSecurity[accountId];
      if (security) {
        security.credentials = [{ credentialId: "40003", credentialType: "PASSWORD", status: "ACTIVE", createdAt: "2026-07-18T10:30:00+08:00", updatedAt: "2026-07-18T10:30:00+08:00", version: "0" }];
      }
      data = "40003";
    } else if (/^\/mg\/(?:platform\/accounts|(?:platform|tenant)\/tenants\/20001\/accounts)\/[^/]+\/credentials\/[^/]+\/password$/.test(path) && method === "PUT") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      expect(route.request().headers()["if-match"]).toMatch(/^"\d+"$/);
      data = null;
    } else if (/^\/mg\/(?:platform\/accounts|(?:platform|tenant)\/tenants\/20001\/accounts)\/[^/]+\/sessions\/[^/]+$/.test(path) && method === "DELETE") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      expect(route.request().headers()["if-match"]).toMatch(/^"\d+"$/);
      const accountId = path.split("/").at(-3) ?? "";
      const sessionId = decodeURIComponent(path.split("/").at(-1) ?? "");
      accountSessions[accountId] = (accountSessions[accountId] ?? []).filter((item) => item.sessionId !== sessionId);
      await route.fulfill({ status: 204, body: "" });
      return;
    } else if (/^\/mg\/(?:platform\/accounts|(?:platform|tenant)\/tenants\/20001\/accounts)\/[^/]+\/sessions$/.test(path) && method === "DELETE") {
      expect(route.request().headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
      expect(route.request().headers()["if-match"]).toMatch(/^"\d+"$/);
      accountSessions[path.split("/").at(-2) ?? ""] = [];
      await route.fulfill({ status: 204, body: "" });
      return;
    } else if (/^\/mg\/platform\/accounts\/[^/]+$/.test(path) && method === "PATCH") {
      expect(route.request().headers()["if-match"]).toMatch(/^"\d+"$/);
      const account = managedAccounts.find((item) => item.accountId === path.split("/").at(-1));
      const input = route.request().postDataJSON() as { status: "ACTIVE" | "DISABLED" };
      if (account) {
        account.status = input.status;
        account.version = String(Number(account.version) + 1);
      }
      data = null;
    } else if (/^\/mg\/platform\/accounts\/[^/]+$/.test(path) && method === "DELETE") {
      expect(route.request().headers()["if-match"]).toMatch(/^"\d+"$/);
      const index = managedAccounts.findIndex((item) => item.accountId === path.split("/").at(-1));
      if (index >= 0) managedAccounts.splice(index, 1);
      await route.fulfill({ status: 204, body: "" });
      return;
    } else if (path === "/mg/platform/operators" && method === "GET") {
      data = { records: platformOperators, page: "1", size: "20", total: String(platformOperators.length) };
    } else if (path === "/mg/platform/operators" && method === "POST") {
      const input = route.request().postDataJSON() as { displayName: string };
      const created = { principalId: "11002", displayName: input.displayName, status: "ACTIVE", version: "0", roles: [] };
      platformOperators.push(created);
      data = created.principalId;
    } else if (/^\/mg\/platform\/operators\/[^/]+\/assignable-roles$/.test(path)) {
      data = [{ roleId: "30001", code: "PLATFORM_ADMIN", name: "平台管理员" }];
    } else if (/^\/mg\/platform\/operators\/[^/]+\/roles$/.test(path) && method === "PUT") {
      const principalId = path.split("/").at(-2) ?? "";
      const operator = platformOperators.find((item) => item.principalId === principalId);
      const input = route.request().postDataJSON() as { roleIds: string[]; version: string };
      if (operator) {
        operator.roles = input.roleIds.map((roleId) => ({ roleId, code: "PLATFORM_ADMIN", name: "平台管理员" }));
        operator.version = String(Number(operator.version) + 1);
      }
      data = null;
    } else if (/^\/mg\/platform\/operators\/[^/]+\/tenant-access-grants$/.test(path)) {
      const principalId = path.split("/").at(-2) ?? "";
      if (method === "GET") {
        data = { principalId, version: platformOperators.find((item) => item.principalId === principalId)?.version ?? "0", defaultAction: "MANAGE", grants: [] };
      } else {
        data = null;
      }
    } else if (/^\/mg\/platform\/operators\/[^/]+$/.test(path) && method === "PATCH") {
      const principalId = path.split("/").at(-1) ?? "";
      const operator = platformOperators.find((item) => item.principalId === principalId);
      const input = route.request().postDataJSON() as { status: "ACTIVE" | "DISABLED" };
      if (operator) {
        operator.status = input.status;
        operator.version = String(Number(operator.version) + 1);
      }
      data = null;
    } else if (/^\/mg\/platform\/operators\/[^/]+$/.test(path) && method === "GET") {
      data = platformOperators.find((item) => item.principalId === path.split("/").at(-1)) ?? null;
    } else if (
      path === "/mg/platform/roles/permissions" ||
      path === "/mg/platform/tenants/20001/roles/permissions" ||
      path === "/mg/tenant/tenants/20001/roles/permissions"
    ) {
      data = [
        {
          permissionId: "40001",
          parentId: null,
          code: "tenant",
          nameKey: "permission.tenant",
          name: english ? "Tenant management" : "租户管理",
          permissionType: "MODULE",
          sortOrder: 10
        },
        {
          permissionId: "40002",
          parentId: "40001",
          code: "tenant:roles",
          nameKey: "permission.tenant.roles",
          name: english ? "Roles" : "角色",
          permissionType: "RESOURCE",
          sortOrder: 20
        }
      ];
    } else if (
      path === "/mg/platform/roles/30001" ||
      path === "/mg/platform/tenants/20001/roles/30001" ||
      path === "/mg/tenant/tenants/20001/roles/30001"
    ) {
      data = {
        roleId: "30001",
        tenantId: path === "/mg/platform/roles/30001" ? null : "20001",
        code: path === "/mg/platform/roles/30001" ? "PLATFORM_ADMIN" : "TENANT_ADMIN",
        name: path === "/mg/platform/roles/30001" ? "平台管理员" : "租户管理员",
        status: "ACTIVE",
        builtIn: true,
        principalCount: 1,
        permissionCount: 2,
        permissions: [
          {
            permissionId: "40001",
            parentId: null,
            code: "tenant",
            nameKey: "permission.tenant",
            name: english ? "Tenant management" : "租户管理",
            permissionType: "MODULE",
            sortOrder: 10
          },
          {
            permissionId: "40002",
            parentId: "40001",
            code: "tenant:roles",
            nameKey: "permission.tenant.roles",
            name: english ? "Roles" : "角色",
            permissionType: "RESOURCE",
            sortOrder: 20
          }
        ],
        createdAt: "2026-07-10T08:00:00+08:00",
        updatedAt: "2026-07-10T08:00:00+08:00",
        version: "1"
      };
    } else if (
      path === "/mg/platform/roles" ||
      path === "/mg/platform/tenants/20001/roles" ||
      path === "/mg/tenant/tenants/20001/roles"
    ) {
      const platform = path === "/mg/platform/roles";
      data = {
        records: [
          {
            roleId: "30001",
            tenantId: platform ? null : "20001",
            code: platform ? "PLATFORM_ADMIN" : "TENANT_ADMIN",
            name: platform ? "平台管理员" : "租户管理员",
            status: "ACTIVE",
            builtIn: true,
            principalCount: 1,
            permissionCount: 2,
            permissions: [],
            createdAt: "2026-07-10T08:00:00+08:00",
            updatedAt: "2026-07-10T08:00:00+08:00",
            version: "1"
          }
        ],
        page: "1",
        size: "20",
        total: "1"
      };
    } else if (path === "/mg/platform/principals/11001/assignable-roles") {
      data = [{ roleId: "30001", code: "PLATFORM_ADMIN", name: english ? "Platform administrator" : "平台管理员" }];
    } else if (path === "/mg/platform/principals/11001/roles" && method === "PUT") {
      if (platformRoleConflictPending) {
        platformRoleConflictPending = false;
        await route.fulfill({
          status: 409,
          headers: { "X-Client-State-Stale": "true", "X-Client-State-Refresh": "me" },
          contentType: "application/json",
          body: JSON.stringify({
            code: 1009,
            messageKey: "error.iam.platform-principal-roles-changed",
            messageArgs: [],
            message: english ? "The platform principal roles changed elsewhere" : "平台主体角色已被其他请求修改",
            data: null,
            traceId: "platform-role-conflict",
            timestamp: String(Date.now())
          })
        });
        return;
      }
    } else if (path === "/me/profile/avatar" && method === "PUT") {
      const input = route.request().postDataJSON() as { fileId: string };
      currentAvatar = { scene: "iam.default-profile.avatar", resourceId: "10001", fileId: input.fileId };
      currentProfileVersion += 1;
    } else if (path === "/me/profile/avatar" && method === "DELETE") {
      currentAvatar = null;
      currentProfileVersion += 1;
    } else if (path === "/files/signatures") {
      const input = route.request().postDataJSON() as {
        items: Array<{ requestId: string; operation: "UPLOAD" | "READ"; resourceId?: string; fileId?: string }>;
      };
      if (signatureReadsFail && input.items.some((item) => item.operation === "READ")) {
        await route.fulfill({
          status: 503,
          contentType: "application/json",
          body: JSON.stringify({
            code: 1000,
            messageKey: "error.system.unavailable",
            messageArgs: [],
            message: "文件读取服务暂不可用",
            data: null,
            traceId: "avatar-read-failed",
            timestamp: String(Date.now())
          })
        });
        return;
      }
      data = {
        items: input.items.map((item) =>
          item.operation === "UPLOAD"
            ? {
                requestId: item.requestId,
                operation: item.operation,
                upload: {
                  fileId: String(nextAvatarFileId++),
                  uploadMode: "PROXY",
                  method: "PUT",
                  url: `/files/uploads/${nextAvatarFileId - 1}/content`,
                  headers: { "Content-Type": "image/webp" },
                  formFields: {},
                  expiresAt: "2099-01-01T00:00:00Z",
                  uploadGrant: `avatar-grant-${nextAvatarFileId - 1}`
                }
              }
            : {
                requestId: item.requestId,
                operation: item.operation,
                files: [{ fileId: item.fileId, url: avatarReadUrl, expiresAt: "2099-01-01T00:00:00Z" }]
              }
        )
      };
    } else if (/^\/files\/uploads\/[^/]+\/content$/.test(path) && method === "PUT") {
      data = null;
    } else if (/^\/files\/uploads\/[^/]+\/complete$/.test(path) && method === "POST") {
      data = null;
    } else if (path === "/mg/system/dictionaries/options") {
      data = [
        {
          code: "system.dictionary-kind",
          kind: "CODE_ENUM",
          labelKey: "dictionary.system.dictionary-kind",
          label: "字典类型",
          options: [
            { value: "CODE_ENUM", labelKey: null, label: "代码枚举", tone: "DEFAULT", disabled: false },
            { value: "BUSINESS_CODE", labelKey: null, label: "业务编码", tone: "DEFAULT", disabled: false },
            { value: "INPUT_SUGGESTION", labelKey: null, label: "输入建议", tone: "DEFAULT", disabled: false }
          ]
        },
        {
          code: "system.dictionary-status",
          kind: "CODE_ENUM",
          labelKey: "dictionary.system.dictionary-status",
          label: "字典状态",
          options: [
            { value: "ACTIVE", labelKey: null, label: "启用", tone: "SUCCESS", disabled: false },
            { value: "DISABLED", labelKey: null, label: "停用", tone: "WARNING", disabled: false }
          ]
        },
        {
          code: "system.dictionary-tone",
          kind: "CODE_ENUM",
          labelKey: "dictionary.system.dictionary-tone",
          label: "语义色调",
          options: [
            { value: "DEFAULT", labelKey: null, label: "默认", tone: "DEFAULT", disabled: false },
            { value: "INFO", labelKey: null, label: "信息", tone: "INFO", disabled: false },
            { value: "SUCCESS", labelKey: null, label: "成功", tone: "SUCCESS", disabled: false },
            { value: "WARNING", labelKey: null, label: "警告", tone: "WARNING", disabled: false },
            { value: "DANGER", labelKey: null, label: "危险", tone: "DANGER", disabled: false }
          ]
        },
        {
          code: "iam.tenant-status",
          kind: "CODE_ENUM",
          labelKey: "dictionary.iam.tenant-status",
          label: "租户状态",
          options: [
            { value: "ACTIVE", labelKey: null, label: "启用", tone: "SUCCESS", disabled: false },
            { value: "DISABLED", labelKey: null, label: "停用", tone: "NEUTRAL", disabled: false }
          ]
        },
        {
          code: "iam.tenant-type",
          kind: "CODE_ENUM",
          labelKey: "dictionary.iam.tenant-type",
          label: "租户类型",
          options: [
            { value: "DEFAULT", labelKey: null, label: "默认租户", tone: "NEUTRAL", disabled: false }
          ]
        },
        {
          code: "iam.subject-status",
          kind: "CODE_ENUM",
          labelKey: "dictionary.iam.subject-status",
          label: "登录身份状态",
          options: [
            { value: "ACTIVE", labelKey: null, label: "可用", tone: "SUCCESS", disabled: false },
            { value: "DISABLED", labelKey: null, label: "停用", tone: "NEUTRAL", disabled: false }
          ]
        },
        {
          code: "iam.credential-status",
          kind: "CODE_ENUM",
          labelKey: "dictionary.iam.credential-status",
          label: "凭证状态",
          options: [
            { value: "ACTIVE", labelKey: null, label: "可用", tone: "SUCCESS", disabled: false },
            { value: "DISABLED", labelKey: null, label: "停用", tone: "NEUTRAL", disabled: false }
          ]
        },
        {
          code: "iam.principal-status",
          kind: "CODE_ENUM",
          labelKey: "dictionary.iam.principal-status",
          label: "执行主体状态",
          options: [
            { value: "ACTIVE", labelKey: null, label: "启用", tone: "SUCCESS", disabled: false },
            { value: "DISABLED", labelKey: null, label: "停用", tone: "NEUTRAL", disabled: false }
          ]
        },
        {
          code: "iam.role-status",
          kind: "CODE_ENUM",
          labelKey: "dictionary.iam.role-status",
          label: "角色状态",
          options: [
            { value: "ACTIVE", labelKey: null, label: "启用", tone: "SUCCESS", disabled: false },
            { value: "DISABLED", labelKey: null, label: "停用", tone: "NEUTRAL", disabled: false }
          ]
        },
        {
          code: "iam.credential-type",
          kind: "CODE_ENUM",
          labelKey: "dictionary.iam.credential-type",
          label: "凭证类型",
          options: [
            { value: "PASSWORD", labelKey: null, label: "账号密码", tone: "NEUTRAL", disabled: false }
          ]
        }
      ];
    } else if (path === "/mg/system/dictionaries/order" && method === "GET") {
      data = dictionaryTypeOrder;
    } else if (path === "/mg/system/dictionaries/order" && method === "PUT") {
      if (dictionaryOrderConflictPending) {
        dictionaryOrderConflictPending = false;
        dictionaryTypeOrder.forEach((item) => { item.version = String(Number(item.version) + 1); });
        dictionaryTypeOrder.push({
          id: "51003",
          label: "地区",
          description: "customer.region",
          version: "0"
        });
        await route.fulfill({
          status: 409,
          headers: { "X-Client-State-Refresh": "dictionaries" },
          contentType: "application/json",
          body: JSON.stringify({
            code: 1009,
            messageKey: "error.dictionary.changed",
            messageArgs: [],
            message: "字典列表已变化，请重新确认顺序",
            data: null,
            traceId: "dictionary-order-conflict",
            timestamp: String(Date.now())
          })
        });
        return;
      }
      const input = route.request().postDataJSON() as { items: Array<{ id: string }> };
      const byId = new Map(dictionaryTypeOrder.map((item) => [item.id, item]));
      dictionaryTypeOrder.splice(0, dictionaryTypeOrder.length, ...input.items.map((item) => byId.get(item.id)!));
    } else if (path === "/mg/system/dictionaries/51002/items/order" && method === "GET") {
      data = dictionaryItemOrder;
    } else if (path === "/mg/system/dictionaries/51002/items/order" && method === "PUT") {
      const input = route.request().postDataJSON() as { items: Array<{ id: string }> };
      const byId = new Map(dictionaryItemOrder.map((item) => [item.id, item]));
      dictionaryItemOrder.splice(0, dictionaryItemOrder.length, ...input.items.map((item) => byId.get(item.id)!));
    } else if (path === "/mg/system/dictionaries" && method === "GET") {
      data = {
        records: [
          {
            id: "51001",
            tenantId: null,
            code: "system.dictionary-status",
            kind: "CODE_ENUM",
            source: "CODE",
            labelKey: "dictionary.system.dictionary-status",
            labels: {},
            label: "字典状态",
            status: "ACTIVE",
            sortOrder: 10,
            version: "1",
            createdAt: "2026-07-10T08:00:00+08:00",
            updatedAt: "2026-07-14T08:00:00+08:00"
          },
          {
            id: "51002",
            tenantId: null,
            code: "customer.type",
            kind: "BUSINESS_CODE",
            source: "CODE",
            labelKey: "dictionary.customer.type",
            labels: { "zh-CN": "客户类型" },
            label: "客户类型",
            status: "ACTIVE",
            sortOrder: 20,
            version: "2",
            createdAt: "2026-07-10T08:00:00+08:00",
            updatedAt: "2026-07-14T08:00:00+08:00"
          }
        ],
        page: "1",
        size: "20",
        total: "2"
      };
    } else if (path === "/mg/system/dictionaries/51001" && method === "GET") {
      data = {
        id: "51001",
        tenantId: null,
        code: "system.dictionary-status",
        kind: "CODE_ENUM",
        source: "CODE",
        labelKey: "dictionary.system.dictionary-status",
        labels: {},
        label: "字典状态",
        status: "ACTIVE",
        sortOrder: 10,
        version: "1",
        createdAt: "2026-07-10T08:00:00+08:00",
        updatedAt: "2026-07-14T08:00:00+08:00"
      };
    } else if (path === "/mg/system/dictionaries/51002" && method === "GET") {
      data = {
        id: "51002",
        tenantId: null,
        code: "customer.type",
        kind: "BUSINESS_CODE",
        source: "CODE",
        labelKey: "dictionary.customer.type",
        labels: { "zh-CN": "客户类型" },
        label: "客户类型",
        status: "ACTIVE",
        sortOrder: 20,
        version: "2",
        createdAt: "2026-07-10T08:00:00+08:00",
        updatedAt: "2026-07-14T08:00:00+08:00"
      };
    } else if (path === "/mg/system/dictionaries/51002/items" && method === "GET") {
      data = {
        records: [
          {
            id: "52001",
            dictionaryId: "51002",
            value: "VIP",
            source: "CODE",
            labelKey: "dictionary.customer.type.vip",
            labels: { "zh-CN": "重要客户" },
            label: "重要客户",
            tone: "SUCCESS",
            selectable: true,
            status: "ACTIVE",
            sortOrder: 10,
            version: "3",
            createdAt: "2026-07-10T08:00:00+08:00",
            updatedAt: "2026-07-14T08:00:00+08:00"
          },
          {
            id: "52002",
            dictionaryId: "51002",
            value: "PARTNER",
            source: "CONFIGURATION",
            labelKey: null,
            labels: { "zh-CN": "合作伙伴" },
            label: "合作伙伴",
            tone: "INFO",
            selectable: true,
            status: "ACTIVE",
            sortOrder: 20,
            version: "1",
            createdAt: "2026-07-10T08:00:00+08:00",
            updatedAt: "2026-07-14T08:00:00+08:00"
          }
        ],
        page: "1",
        size: "20",
        total: "2"
      };
    } else if (path === "/mg/platform/tenants") {
      data = {
        records: [
          {
            tenantId: "20001",
            code: "acme",
            name: "示例租户",
            tenantType: "DEFAULT",
            status: "ACTIVE",
            createdAt: "2026-07-10T08:00:00+08:00",
            updatedAt: "2026-07-10T08:00:00+08:00",
            version: "1"
          }
        ],
        page: "1",
        size: "20",
        total: "1"
      };
    } else if (path === "/mg/platform/tenants/20001") {
      data = {
        tenantId: "20001",
        code: "acme",
        name: "示例租户",
        tenantType: "DEFAULT",
        status: "ACTIVE",
        createdAt: "2026-07-10T08:00:00+08:00",
        updatedAt: "2026-07-10T08:00:00+08:00",
        version: "1"
      };
    } else if (
      path === "/mg/platform/tenants/20001/members/assignable-roles" ||
      path === "/mg/tenant/tenants/20001/members/assignable-roles"
    ) {
      data = [{ roleId: "30001", code: "TENANT_ADMIN", name: "租户管理员" }];
    } else if (/^\/mg\/(platform|tenant)\/tenants\/20001\/members\/existing-accounts$/.test(path) && method === "POST") {
      const input = route.request().postDataJSON() as { accountId: string; displayName: string; roleIds: string[] };
      expect(input).toMatchObject({ accountId: "10002", displayName: "既有账号成员", roleIds: [] });
      data = "12002";
    } else if (/^\/mg\/(platform|tenant)\/tenants\/20001\/members$/.test(path) && method === "POST") {
      data = "12002";
    } else if (/^\/mg\/(platform|tenant)\/tenants\/20001\/members\/12001$/.test(path)) {
      data = {
        membershipId: "22001",
        principalId: "12001",
        subjectId: "10001",
        tenantId: "20001",
        displayName: "平台管理员",
        membershipStatus: "ACTIVE",
        membershipVersion: "1",
        principalStatus: "ACTIVE",
        principalVersion: "1",
        roles: [
          {
            principalId: "12001",
            roleId: "30001",
            roleCode: "TENANT_ADMIN",
            roleName: "租户管理员"
          }
        ]
      };
    } else if (
      path === "/mg/platform/tenants/20001/members" ||
      path === "/mg/tenant/tenants/20001/members"
    ) {
      data = {
        records: [
          {
            membershipId: "22001",
            principalId: "12001",
            subjectId: "10001",
            tenantId: "20001",
            displayName: "平台管理员",
            membershipStatus: "ACTIVE",
            membershipVersion: "1",
            principalStatus: "ACTIVE",
            principalVersion: "1",
            roles: [
              {
                principalId: "12001",
                roleId: "30001",
                roleCode: "TENANT_ADMIN",
                roleName: "租户管理员"
              }
            ]
          }
        ],
        page: "1",
        size: "20",
        total: "1"
      };
    } else if (path === "/mg/platform/schedule-task-types") {
      data = [{ taskType: "notification.sse", payloadVersion: 1, displayName: "站内实时通知" }];
    } else if (path === "/mg/platform/schedules" && method === "GET") {
      data = { records: schedules, page: "1", size: "20", total: String(schedules.length) };
    } else if (path === "/mg/platform/schedules" && method === "POST") {
      const input = route.request().postDataJSON() as Record<string, unknown>;
      const created = {
        ...schedules[0],
        ...input,
        scheduleId: "80002",
        status: "ACTIVE",
        version: "0",
        createdAt: "2026-07-14T12:00:00+08:00",
        updatedAt: "2026-07-14T12:00:00+08:00"
      };
      schedules.push(created as (typeof schedules)[number]);
      data = created;
    } else if (/^\/mg\/platform\/schedules\/[^/]+\/executions\/[^/]+$/.test(path)) {
      data = {
        executionId: path.split("/").at(-1),
        scheduleId: path.split("/").at(-3),
        triggerSource: "SCHEDULED",
        scheduledFireTime: "2026-07-14T09:00:00+08:00",
        actualFireTime: "2026-07-14T09:00:01+08:00",
        completedAt: "2026-07-14T09:00:02+08:00",
        status: "SUCCESS",
        taskType: "notification.sse",
        resultSummary: "1 recipient",
        safeErrorType: null,
        safeErrorMessage: null,
        executedInstanceId: "node-1",
        createdAt: "2026-07-14T09:00:00+08:00"
      };
    } else if (/^\/mg\/platform\/schedules\/[^/]+\/executions$/.test(path)) {
      data = method === "POST"
        ? {
            executionId: "81002",
            scheduleId: "80001",
            triggerSource: "MANUAL",
            status: "PENDING",
            taskType: "notification.sse",
            createdAt: "2026-07-14T12:00:00+08:00"
          }
        : {
            records: [
              {
                executionId: "81001",
                scheduleId: "80001",
                triggerSource: "SCHEDULED",
                scheduledFireTime: "2026-07-14T09:00:00+08:00",
                actualFireTime: "2026-07-14T09:00:01+08:00",
                completedAt: "2026-07-14T09:00:02+08:00",
                status: "SUCCESS",
                taskType: "notification.sse",
                resultSummary: "1 recipient",
                safeErrorType: null,
                safeErrorMessage: null,
                executedInstanceId: "node-1",
                createdAt: "2026-07-14T09:00:00+08:00"
              }
            ],
            page: "1",
            size: "20",
            total: "1"
          };
    } else if (/^\/mg\/platform\/schedules\/[^/]+$/.test(path)) {
      data = schedules.find((schedule) => schedule.scheduleId === path.split("/").at(-1)) ?? null;
    } else if (path === "/mg/system/mail-senders" && method === "GET") {
      data = mailSenders;
    } else if (path === "/mg/system/mail-senders" && method === "POST") {
      const input = route.request().postDataJSON() as Record<string, unknown>;
      const senderId = "61002";
      mailSenders.push({
        senderId,
        name: String(input.name),
        host: String(input.host),
        port: Number(input.port),
        security: input.security as MailSenderView["security"],
        username: input.username ? String(input.username) : null,
        passwordConfigured: Boolean(input.password),
        fromAddress: String(input.fromAddress),
        fromName: input.fromName ? String(input.fromName) : null,
        replyTo: input.replyTo ? String(input.replyTo) : null,
        status: "DISABLED",
        version: "0"
      });
      data = mailSenders.at(-1);
    } else if (/^\/mg\/system\/mail-senders\/[^/]+\/connection-tests$/.test(path)) {
      data = null;
    } else if (/^\/mg\/system\/mail-senders\/[^/]+$/.test(path) && method === "PATCH") {
      const senderId = path.split("/").at(-1);
      const sender = mailSenders.find((item) => item.senderId === senderId);
      const input = route.request().postDataJSON() as { status: MailSenderView["status"] };
      if (sender) {
        sender.status = input.status;
        sender.version = String(Number(sender.version) + 1);
      }
      data = sender;
    } else if (/^\/mg\/system\/mail-senders\/[^/]+$/.test(path) && method === "GET") {
      data = mailSenders.find((item) => item.senderId === path.split("/").at(-1)) ?? null;
    } else if (/^\/mg\/system\/mail-senders\/[^/]+$/.test(path) && method === "PUT") {
      const senderId = path.split("/").at(-1);
      const sender = mailSenders.find((item) => item.senderId === senderId);
      const input = route.request().postDataJSON() as Record<string, unknown>;
      if (sender) {
        Object.assign(sender, {
          name: String(input.name),
          host: String(input.host),
          port: Number(input.port),
          security: input.security as MailSenderView["security"],
          username: input.username ? String(input.username) : null,
          passwordConfigured: input.password ? true : input.clearPassword ? false : sender.passwordConfigured,
          fromAddress: String(input.fromAddress),
          fromName: input.fromName ? String(input.fromName) : null,
          replyTo: input.replyTo ? String(input.replyTo) : null,
          version: String(Number(sender.version) + 1)
        });
      }
      data = sender;
    } else if (/^\/mg\/system\/mail-senders\/[^/]+$/.test(path) && method === "DELETE") {
      const index = mailSenders.findIndex((item) => item.senderId === path.split("/").at(-1));
      if (index >= 0) mailSenders.splice(index, 1);
    } else if (path === "/mg/system/mail-template-previews" && method === "POST") {
      const input = route.request().postDataJSON() as Record<string, unknown>;
      const variables = input.variables as Record<string, string>;
      const render = (value: unknown) => String(value ?? "").replace(/{{\s*([a-z][a-zA-Z0-9_]*)\s*}}/g, (_, name: string) => variables[name] ?? "");
      data = {
        subject: render(input.subjectTemplate),
        text: render(input.textTemplate),
        html: render(input.htmlTemplate).replace(/<script[\s\S]*?<\/script>/gi, "") || null
      };
    } else if (path === "/mg/system/mail-templates" && method === "GET") {
      data = mailTemplates;
    } else if (path === "/mg/system/mail-templates" && method === "POST") {
      const input = route.request().postDataJSON() as Record<string, unknown>;
      const template: MailTemplateView = {
        templateId: "62002",
        family: String(input.family),
        locale: String(input.locale),
        subjectTemplate: String(input.subjectTemplate),
        textTemplate: String(input.textTemplate),
        htmlTemplate: input.htmlTemplate ? String(input.htmlTemplate) : null,
        variableNames: [],
        status: "DISABLED",
        version: "0"
      };
      mailTemplates.push(template);
      data = template;
    } else if (/^\/mg\/system\/mail-templates\/[^/]+\/test-messages$/.test(path)) {
      data = null;
    } else if (/^\/mg\/system\/mail-templates\/[^/]+$/.test(path) && method === "PATCH") {
      const template = mailTemplates.find((item) => item.templateId === path.split("/").at(-1));
      const input = route.request().postDataJSON() as { status: MailTemplateView["status"] };
      if (template) {
        template.status = input.status;
        template.version = String(Number(template.version) + 1);
      }
      data = template;
    } else if (/^\/mg\/system\/mail-templates\/[^/]+$/.test(path) && method === "GET") {
      data = mailTemplates.find((item) => item.templateId === path.split("/").at(-1)) ?? null;
    } else if (/^\/mg\/system\/mail-templates\/[^/]+$/.test(path) && method === "PUT") {
      const template = mailTemplates.find((item) => item.templateId === path.split("/").at(-1));
      const input = route.request().postDataJSON() as Record<string, unknown>;
      if (template) {
        Object.assign(template, {
          family: String(input.family),
          locale: String(input.locale),
          subjectTemplate: String(input.subjectTemplate),
          textTemplate: String(input.textTemplate),
          htmlTemplate: input.htmlTemplate ? String(input.htmlTemplate) : null,
          version: String(Number(template.version) + 1)
        });
      }
      data = template;
    } else if (/^\/mg\/system\/mail-templates\/[^/]+$/.test(path) && method === "DELETE") {
      const index = mailTemplates.findIndex((item) => item.templateId === path.split("/").at(-1));
      if (index >= 0) mailTemplates.splice(index, 1);
    } else if (path === "/mg/system/scheduled-jobs") {
      data = [
        {
          jobGroup: "system",
          jobName: "subject-cleanup",
          description: "Subject 关系清理",
          triggerState: "NORMAL",
          scheduleType: "INTERVAL",
          interval: "PT30S",
          cronExpression: null,
          previousFireTime: "2026-07-14T11:59:30+08:00",
          nextFireTime: "2026-07-14T12:00:30+08:00",
          lastActualFireTime: "2026-07-14T11:59:30+08:00",
          lastResult: "SUCCESS",
          lastDuration: "PT0.12S",
          safeErrorSummary: null,
          executedInstanceId: "node-1",
          manageable: false
        }
      ];
    } else if (path === "/mg/system/scheduled-jobs/system/subject-cleanup") {
      data = {
        jobGroup: "system",
        jobName: "subject-cleanup",
        description: "Subject 关系清理",
        triggerState: "NORMAL",
        scheduleType: "INTERVAL",
        interval: "PT30S",
        cronExpression: null,
        previousFireTime: "2026-07-14T11:59:30+08:00",
        nextFireTime: "2026-07-14T12:00:30+08:00",
        lastActualFireTime: "2026-07-14T11:59:30+08:00",
        lastResult: "SUCCESS",
        lastDuration: "PT0.12S",
        safeErrorSummary: null,
        executedInstanceId: "node-1",
        manageable: false
      };
    } else if (path === "/mg/system/menus/route-options") {
      data = menuRoutes;
    } else if (path === "/mg/system/menus" && method === "GET") {
      data = menus;
    } else if (path === "/mg/system/menus" && method === "POST") {
      const input = route.request().postDataJSON() as MenuWriteInput;
      const menuId = String(nextMenuId++);
      const siblings = menus.filter((item) => item.parentId === (input.parentId ?? null));
      menus.push({
        menuId,
        parentId: input.parentId ?? null,
        routeCode: input.routeCode ?? null,
        pathTemplate: input.routeCode
          ? menuRoutes.find((item) => item.code === input.routeCode)?.pathTemplate ?? null
          : null,
        permissionCode: input.routeCode
          ? menuRoutes.find((item) => item.code === input.routeCode)?.permissionCode ?? null
          : null,
        titles: input.titles,
        icon: input.icon,
        sortOrder: Math.max(0, ...siblings.map((item) => item.sortOrder)) + 10,
        visible: input.visible,
        version: "0",
        children: []
      });
      data = menuId;
    } else if (path === "/mg/system/menus/order" && method === "PUT") {
      const input = route.request().postDataJSON() as { items: Array<{ menuId: string }> };
      if (menuOrderConflictPending) {
        menuOrderConflictPending = false;
        for (const item of input.items) {
          const menu = menus.find((candidate) => candidate.menuId === item.menuId);
          if (menu) menu.version = String(Number(menu.version) + 1);
        }
        await route.fulfill({
          status: 409,
          headers: { "X-Client-State-Refresh": "menus" },
          contentType: "application/json",
          body: JSON.stringify({
            code: 1009,
            messageKey: "error.menu.changed",
            messageArgs: [],
            message: "同级菜单版本已变化，请重新确认顺序",
            data: null,
            traceId: "menu-order-conflict",
            timestamp: String(Date.now())
          })
        });
        return;
      }
      const roots = new Map(menus.filter((item) => item.parentId === null).map((item) => [item.menuId, item]));
      const orderedRoots = input.items.map((item, index) => {
        const menu = roots.get(item.menuId)!;
        menu.sortOrder = (index + 1) * 10;
        return menu;
      });
      menus.splice(0, menus.length, ...orderedRoots);
    } else if (/^\/mg\/system\/menus\/[^/]+$/.test(path) && method === "PUT") {
      const menuId = path.split("/").at(-1);
      const menu = menus.find((item) => item.menuId === menuId);
      const input = route.request().postDataJSON() as MenuWriteInput;
      if (menuConflictPending) {
        menuConflictPending = false;
        if (menu) menu.version = "1";
        await route.fulfill({
          status: 409,
          headers: { "X-Client-State-Refresh": "menus" },
          contentType: "application/json",
          body: JSON.stringify({
            code: 1009,
            messageKey: "error.menu.changed",
            messageArgs: ["menus"],
            message: "菜单已变化，请刷新后重试",
            data: null,
            traceId: "e2e-conflict",
            timestamp: String(Date.now())
          })
        });
        return;
      }
      if (menu) {
        const parentId = input.parentId ?? null;
        const parentChanged = menu.parentId !== parentId;
        const siblings = menus.filter((item) => item.menuId !== menu.menuId && item.parentId === parentId);
        Object.assign(menu, {
          parentId,
          routeCode: input.routeCode ?? null,
          pathTemplate: input.routeCode
            ? menuRoutes.find((item) => item.code === input.routeCode)?.pathTemplate ?? null
            : null,
          permissionCode: input.routeCode
            ? menuRoutes.find((item) => item.code === input.routeCode)?.permissionCode ?? null
            : null,
          titles: input.titles,
          icon: input.icon,
          sortOrder: parentChanged
            ? Math.max(0, ...siblings.map((item) => item.sortOrder)) + 10
            : menu.sortOrder,
          visible: input.visible,
          version: String(Number(menu.version) + 1)
        });
      }
    } else if (/^\/mg\/system\/menus\/[^/]+$/.test(path) && method === "DELETE") {
      const menuId = path.split("/").at(-1);
      const index = menus.findIndex((item) => item.menuId === menuId);
      if (index >= 0) menus.splice(index, 1);
    } else if (path.startsWith("/mg/system/operation-logs/")) {
      data = {
        id: "70001",
        subjectId: "10001",
        accountId: "10001",
        principalId: "11001",
        subjectDisplayName: "平台管理员",
        principalDisplayName: "平台管理员",
        moduleCode: "system",
        moduleName: "系统管理",
        actionCode: "update",
        actionName: "更新",
        result: "SUCCESS",
        requestUri: "/mg/system/demo",
        payloadType: "raw",
        summary: "更新系统配置",
        actorReference: null,
        targetReference: null,
        createdAt: "2026-07-10T08:00:00+08:00",
        actor: {},
        target: {},
        request: {},
        error: null,
        payload: { value: "demo" }
      };
    } else if (path === "/mg/system/operation-logs") {
      data = {
        records: [
          {
            id: "70001",
            subjectId: "10001",
            accountId: "10001",
            principalId: "11001",
            subjectDisplayName: "平台管理员",
            principalDisplayName: "平台管理员",
            moduleCode: "system",
            moduleName: "系统管理",
            actionCode: "update",
            actionName: "更新",
            result: "SUCCESS",
            requestUri: "/mg/system/demo",
            payloadType: "raw",
            summary: "更新系统配置",
            actorReference: null,
            targetReference: null,
            createdAt: "2026-07-10T08:00:00+08:00"
          }
        ],
        page: "1",
        size: "20",
        total: "1"
      };
    }
    await route.fulfill({
      status: 200,
      ...(path.startsWith("/mg/system/menus") && method !== "GET"
        ? { headers: { "X-Client-State-Refresh": "me" } }
        : {}),
      contentType: "application/json",
      body: JSON.stringify({
        code: 0,
        messageKey: null,
        messageArgs: [],
        message: null,
        data,
        traceId: "e2e-trace",
        timestamp: String(Date.now())
      })
    });
  });
  return {
    setAvatarReadUrl(url: string) {
      avatarReadUrl = url;
    },
    failSignatureReads() {
      signatureReadsFail = true;
    },
    failNextMeRefresh() {
      nextMeRefreshFails = true;
    }
  };
}

export async function openAvatarCropper(page: Page, scope: Locator): Promise<Locator> {
  const imageBytes = await page.evaluate(async () => {
    const canvas = document.createElement("canvas");
    canvas.width = 256;
    canvas.height = 256;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("Canvas 2D context is unavailable");
    context.fillStyle = "#2563eb";
    context.fillRect(0, 0, 256, 256);
    context.fillStyle = "#ffffff";
    context.fillRect(64, 64, 128, 128);
    const blob = await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((value) => (value ? resolve(value) : reject(new Error("PNG encoding failed"))), "image/png")
    );
    return [...new Uint8Array(await blob.arrayBuffer())];
  });
  await scope.locator("input.avatar-file-input").setInputFiles({
    name: "avatar.png",
    mimeType: "image/png",
    buffer: Buffer.from(imageBytes)
  });
  const modal = page.locator(".avatar-crop-modal:visible");
  await expect(modal).toBeVisible();
  await expect.poll(() =>
    modal.locator("img").first().evaluate((image) => {
      const candidate = image as HTMLImageElement;
      return candidate.complete && candidate.naturalWidth > 0;
    })
  ).toBe(true);
  return modal;
}

export async function contrastRatio(foreground: Locator, background: Locator): Promise<number> {
  const [foregroundColor, backgroundColor] = await Promise.all([
    foreground.evaluate((element) => getComputedStyle(element).color),
    background.evaluate((element) => getComputedStyle(element).backgroundColor)
  ]);
  const luminance = (value: string): number => {
    const channels = value.match(/[\d.]+/g)?.slice(0, 3).map(Number);
    if (!channels || channels.length !== 3) throw new Error(`Unsupported color: ${value}`);
    const [red, green, blue] = channels.map((channel) => {
      const normalized = channel! / 255;
      return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * red! + 0.7152 * green! + 0.0722 * blue!;
  };
  const foregroundLuminance = luminance(foregroundColor);
  const backgroundLuminance = luminance(backgroundColor);
  return (Math.max(foregroundLuminance, backgroundLuminance) + 0.05) /
    (Math.min(foregroundLuminance, backgroundLuminance) + 0.05);
}

export async function cropAndUploadAvatar(page: Page, scope: Locator): Promise<void> {
  const modal = await openAvatarCropper(page, scope);
  const stencil = modal.locator(".vue-circle-stencil");
  const cropperSurface = modal.locator(".avatar-cropper");
  const readStencilGeometry = async (): Promise<{ x: number; y: number; width: number; height: number }> => {
    const [stencilBox, cropperBox] = await Promise.all([stencil.boundingBox(), cropperSurface.boundingBox()]);
    expect(stencilBox).not.toBeNull();
    expect(cropperBox).not.toBeNull();
    return {
      x: stencilBox!.x - cropperBox!.x,
      y: stencilBox!.y - cropperBox!.y,
      width: stencilBox!.width,
      height: stencilBox!.height
    };
  };
  let previousGeometry: { x: number; y: number; width: number; height: number } | null = null;
  await expect.poll(async () => {
    const current = await readStencilGeometry();
    const stable = previousGeometry !== null &&
      Math.max(
        Math.abs(current.x - previousGeometry.x),
        Math.abs(current.y - previousGeometry.y),
        Math.abs(current.width - previousGeometry.width),
        Math.abs(current.height - previousGeometry.height)
      ) < 0.05;
    previousGeometry = current;
    return stable;
  }).toBe(true);
  const initialStencilGeometry = previousGeometry!;
  const cropperBox = await cropperSurface.boundingBox();
  expect(cropperBox).not.toBeNull();
  const expectedStencilSize = Math.round(Math.min(cropperBox!.width, cropperBox!.height) * 0.72);
  expect(initialStencilGeometry.width).toBeCloseTo(expectedStencilSize, 0);
  expect(initialStencilGeometry.height).toBeCloseTo(expectedStencilSize, 0);
  expect(initialStencilGeometry.x).toBeCloseTo((cropperBox!.width - expectedStencilSize) / 2, 0);
  expect(initialStencilGeometry.y).toBeCloseTo((cropperBox!.height - expectedStencilSize) / 2, 0);
  const expectFixedStencil = async (): Promise<void> => {
    const geometry = await readStencilGeometry();
    expect(geometry.x).toBeCloseTo(initialStencilGeometry.x, 1);
    expect(geometry.y).toBeCloseTo(initialStencilGeometry.y, 1);
    expect(geometry.width).toBeCloseTo(initialStencilGeometry.width, 1);
    expect(geometry.height).toBeCloseTo(initialStencilGeometry.height, 1);
  };
  const initialStencilBox = await stencil.boundingBox();
  expect(initialStencilBox).not.toBeNull();
  await page.mouse.move(
    initialStencilBox!.x + initialStencilBox!.width / 2,
    initialStencilBox!.y + initialStencilBox!.height / 2
  );
  await page.mouse.down();
  await page.mouse.move(
    initialStencilBox!.x + initialStencilBox!.width / 2 + 24,
    initialStencilBox!.y + initialStencilBox!.height / 2 + 16
  );
  await page.mouse.up();
  await expectFixedStencil();
  await expect(stencil.locator(".vue-simple-handler, .vue-simple-handler-wrapper")).toHaveCount(0);
  await expectFixedStencil();
  await expect(modal.getByRole("button", { name: "缩小" })).toBeVisible();
  await modal.getByRole("button", { name: "放大" }).click();
  await expectFixedStencil();
  await modal.getByRole("button", { name: "向右旋转" }).click();
  await expectFixedStencil();
  const controlSizes = await modal.locator(".avatar-control-button").evaluateAll((buttons) =>
    buttons.map((button) => {
      const box = button.getBoundingClientRect();
      return { width: box.width, height: box.height };
    })
  );
  expect(controlSizes.every((size) => size.width >= 44 && size.height >= 44)).toBe(true);
  const modalBox = await modal.locator(".ant-modal-content").boundingBox();
  const viewport = page.viewportSize();
  expect(modalBox).not.toBeNull();
  expect(viewport).not.toBeNull();
  expect(modalBox!.x).toBeGreaterThanOrEqual(0);
  expect(modalBox!.x + modalBox!.width).toBeLessThanOrEqual(viewport!.width);
  expect(modalBox!.y + modalBox!.height).toBeLessThanOrEqual(viewport!.height + 1);
  await modal.getByRole("button", { name: "上传并使用" }).click();
  await expect(modal).toBeHidden();
}
