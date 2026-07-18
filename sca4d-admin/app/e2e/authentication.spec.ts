import { expect, test } from "@playwright/test";
import { session } from "./support/admin";

test("登录页可完整操作", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "登录管理端" })).toBeVisible();
  await expect(page.locator(".login-panel form button[type='submit']")).toBeVisible();
  await expect(page.locator("body")).not.toHaveCSS("overflow-x", "scroll");
});

test("登录页使用一次性恢复码替换密码", async ({ page }) => {
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api/, "");
    let data: unknown;
    if (path === "/authentication-methods") {
      data = [
        { type: "PASSWORD", capabilities: ["LOGIN", "STEP_UP"] },
        { type: "RECOVERY_CODE", capabilities: ["RECOVERY"] }
      ];
    } else if (path === "/authentications") {
      expect(route.request().postDataJSON()).toEqual({ purpose: "RECOVERY", scopeType: "GLOBAL" });
      data = {
        authenticationId: "authentication-recovery",
        purpose: "RECOVERY",
        activityId: "primary-proof",
        subjectId: null,
        accountId: null,
        expiresAt: "2026-07-17T02:00:00Z"
      };
    } else if (path === "/authentications/authentication-recovery/recovery-code-proofs") {
      expect(route.request().postDataJSON()).toEqual({
        identifierType: "USERNAME",
        identifier: "admin",
        recoveryCode: "AAAAA-BBBBB-CCCCC-DDDDD"
      });
      data = {
        authenticationId: "authentication-recovery",
        purpose: "RECOVERY",
        activityId: "completion",
        subjectId: "10001",
        accountId: "10001",
        expiresAt: "2026-07-17T02:00:00Z"
      };
    } else if (path === "/authentications/authentication-recovery/password-replacements") {
      expect(route.request().postDataJSON()).toEqual({ password: "new-secret" });
      await route.fulfill({ status: 204, body: "" });
      return;
    } else {
      await route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
      return;
    }
    await route.fulfill({
      status: path === "/authentications" ? 201 : 200,
      contentType: "application/json",
      body: JSON.stringify({
        code: 0,
        messageKey: null,
        messageArgs: [],
        message: null,
        data,
        traceId: "e2e-recovery",
        timestamp: String(Date.now())
      })
    });
  });

  await page.goto("/login");
  await page.getByRole("button", { name: "使用恢复码" }).click();
  await expect(page.getByRole("heading", { name: "恢复登录账号" })).toBeVisible();
  await page.getByLabel("登录账号", { exact: true }).fill("admin");
  await page.getByLabel("恢复码", { exact: true }).fill("AAAAA-BBBBB-CCCCC-DDDDD");
  await page.getByRole("button", { name: "验证恢复码" }).click();

  await expect(page.getByRole("heading", { name: "设置新密码" })).toBeVisible();
  await page.getByLabel("新密码", { exact: true }).fill("new-secret");
  await page.getByLabel("确认新密码").fill("new-secret");
  await page.getByRole("button", { name: "替换密码" }).click();

  await expect(page.getByRole("heading", { name: "密码已替换" })).toBeVisible();
  expect(await page.locator("body").evaluate((body) => body.scrollWidth <= body.clientWidth)).toBe(true);
  const storedSecrets = await page.evaluate(() => {
    const values = (storage: Storage) => Array.from(
      { length: storage.length },
      (_, index) => storage.getItem(storage.key(index) ?? "") ?? ""
    );
    return [...values(localStorage), ...values(sessionStorage)].join("");
  });
  expect(storedSecrets).not.toContain("AAAAA-BBBBB-CCCCC-DDDDD");
  expect(storedSecrets).not.toContain("new-secret");
});

test("Account 登录时显式选择身份验证器或邮件验证码完成二步验证", async ({ page }) => {
  // 只模拟服务端资源状态；页面继续通过正式 Authentication API 推进登录。
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api/, "");
    let data: unknown;
    if (path === "/authentication-methods") {
      data = [
        { type: "PASSWORD", capabilities: ["LOGIN", "STEP_UP"] },
        { type: "TOTP", capabilities: ["STEP_UP"] },
        { type: "EMAIL_CODE", capabilities: ["STEP_UP"] }
      ];
    } else if (path === "/authentications") {
      data = {
        authenticationId: "authentication-2fa",
        purpose: "LOGIN",
        activityId: "primary-proof",
        subjectId: null,
        accountId: null,
        expiresAt: "2026-07-16T16:00:00Z"
      };
    } else if (path === "/authentications/authentication-2fa/password-proofs") {
      data = {
        authenticationId: "authentication-2fa",
        purpose: "LOGIN",
        activityId: "second-factor-proof",
        subjectId: "10001",
        accountId: "10001",
        expiresAt: "2026-07-16T16:00:00Z"
      };
    } else if (path === "/authentications/authentication-2fa/email-code-challenges") {
      data = {
        challengeId: "challenge-2fa",
        maskedRecipient: "ad***@example.test",
        expiresAt: "2026-07-16T15:20:00Z",
        resendAt: "2099-07-16T15:16:00Z"
      };
    } else if (path === "/authentications/authentication-2fa/email-code-proofs") {
      expect(route.request().postDataJSON()).toEqual({
        challengeId: "challenge-2fa",
        code: "123456"
      });
      data = {
        authenticationId: "authentication-2fa",
        purpose: "LOGIN",
        activityId: "completion",
        subjectId: "10001",
        accountId: "10001",
        expiresAt: "2026-07-16T16:00:00Z"
      };
    } else if (path === "/authentications/authentication-2fa/totp-proofs") {
      expect(route.request().postDataJSON()).toEqual({ code: "123456" });
      data = {
        authenticationId: "authentication-2fa",
        purpose: "LOGIN",
        activityId: "completion",
        subjectId: "10001",
        accountId: "10001",
        expiresAt: "2026-07-16T16:00:00Z"
      };
    } else if (path === "/sessions" && route.request().method() === "POST") {
      expect(route.request().postDataJSON()).toEqual({ authenticationId: "authentication-2fa" });
      data = session;
    } else if (path === "/me") {
      data = {
        subject: { subjectId: "10001", displayName: "平台管理员" },
        account: { accountId: "10001", scopeType: "GLOBAL", scopeId: null },
        avatar: null,
        profileVersion: "1",
        principal: {
          principalId: "10001",
          principalType: "DEFAULT",
          tenantId: null,
          displayName: "平台管理员"
        },
        session: { ...session, tokenValue: undefined },
        roles: [],
        permissions: [],
        availablePrincipals: [
          {
            principalId: "10001",
            principalType: "DEFAULT",
            tenantId: null,
            displayName: "平台管理员",
            status: "ACTIVE"
          }
        ],
        tenants: [],
        navigation: []
      };
    } else {
      await route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
      return;
    }
    await route.fulfill({
      status:
        path === "/sessions"
        || path.endsWith("/email-code-challenges")
        || path === "/authentications"
          ? 201
          : 200,
      contentType: "application/json",
      body: JSON.stringify({
        code: 0,
        messageKey: null,
        messageArgs: [],
        message: null,
        data,
        traceId: "e2e-2fa",
        timestamp: String(Date.now())
      })
    });
  });

  await page.goto("/login");
  await page.getByLabel("登录账号").fill("admin");
  await page.getByLabel("密码").fill("secret");
  await page.locator(".login-panel form button[type='submit']").click();

  await expect(page.getByRole("heading", { name: "完成二步验证" })).toBeVisible();
  await expect(page.getByText("身份验证器", { exact: true }).first()).toBeVisible();
  expect(await page.locator("body").evaluate((body) => body.scrollWidth <= body.clientWidth)).toBe(true);

  await page.getByRole("textbox", { name: /验证码/ }).fill("123456");
  await page.getByRole("button", { name: "完成二步验证并登录" }).click();
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem("sca4d-admin.session.tab")))
    .toContain("e2e-token");
  await expect(page).toHaveURL(/\/dashboard$/);
});
