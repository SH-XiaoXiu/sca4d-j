import { expect, test, type Page, type APIRequestContext } from "@playwright/test";

const enabled = process.env.SCA4D_REAL_PASSKEY === "true";
const username = process.env.SCA4DJ_BOOTSTRAP_PLATFORM_ADMINISTRATOR_IDENTIFIER;
const password = process.env.SCA4DJ_BOOTSTRAP_PLATFORM_ADMINISTRATOR_PASSWORD;
const backend = process.env.SCA4D_REAL_API_URL ?? "http://localhost:8091";
const mailpit = process.env.SCA4D_MAILPIT_URL ?? "http://127.0.0.1:8025";

interface MailpitList {
  messages: Array<{ ID: string }>;
}

interface MailpitMessage {
  Subject: string;
  Text: string;
}

interface SessionStorageValue {
  session: { tokenName: string; tokenValue: string };
}

/** 将管理端同源 `/api` 请求转发到当前真实后端，同时保留浏览器 Origin 和 Cookie 行为。 */
async function routeBackend(page: Page): Promise<void> {
  await page.route("**/api/**", async (route) => {
    const source = new URL(route.request().url());
    if (!source.pathname.startsWith("/api/")) {
      await route.continue();
      return;
    }
    const path = source.pathname.replace(/^\/api/, "");
    await route.continue({ url: `${backend}${path}${source.search}` });
  });
}

/** @return Mailpit 当前最新消息主键；邮箱为空时返回 {@code null} */
async function latestMessageId(request: APIRequestContext): Promise<string | null> {
  const response = await request.get(`${mailpit}/api/v1/messages`);
  expect(response.ok()).toBe(true);
  const list = await response.json() as MailpitList;
  return list.messages[0]?.ID ?? null;
}

/** 等待新认证邮件并只返回验证码，不把邮件正文写入测试输出。 */
async function waitForEmailCode(
  request: APIRequestContext,
  previousMessageId: string | null
): Promise<string> {
  let messageId: string | null = null;
  await expect.poll(async () => {
    messageId = await latestMessageId(request);
    return messageId !== null && messageId !== previousMessageId;
  }, { timeout: 10_000 }).toBe(true);
  const response = await request.get(`${mailpit}/api/v1/message/${encodeURIComponent(messageId!)}`);
  expect(response.ok()).toBe(true);
  const message = await response.json() as MailpitMessage;
  const code = `${message.Subject}\n${message.Text}`.match(/\b\d{6}\b/)?.[0];
  expect(code).toBeTruthy();
  return code!;
}

/** 使用密码和邮件第二因素建立当前管理员 Session。 */
async function passwordAndEmailLogin(page: Page): Promise<void> {
  const before = await latestMessageId(page.request);
  await page.getByLabel("登录账号").fill(username!);
  await page.getByLabel("密码").fill(password!);
  await page.locator(".login-panel form button[type='submit']").click();
  await expect(page.getByRole("heading", { name: "验证登录" })).toBeVisible();
  await page.getByText("邮件验证码", { exact: true }).click();
  await page.getByRole("button", { name: "发送验证码" }).click();
  const code = await waitForEmailCode(page.request, before);
  await page.getByRole("textbox", { name: /验证码/ }).fill(code);
  await page.getByRole("button", { name: "验证并登录" }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

test.describe("真实 Passkey", () => {
  test.skip(!enabled, "仅在显式启用真实 Passkey 验收时运行");
  test.skip(!username || !password, "真实验收需要 Bootstrap 管理员环境变量");

  test("注册、无用户名登录、重放拒绝、计数推进、UV 失败和删除", async ({ page, context }) => {
    test.setTimeout(90_000);
    const pageErrors: string[] = [];
    page.on("pageerror", (error) => pageErrors.push(error.stack ?? error.message));
    await routeBackend(page);
    const cdp = await context.newCDPSession(page);
    await cdp.send("WebAuthn.enable");
    const { authenticatorId } = await cdp.send("WebAuthn.addVirtualAuthenticator", {
      options: {
        protocol: "ctap2",
        transport: "internal",
        hasResidentKey: true,
        hasUserVerification: true,
        isUserVerified: true,
        automaticPresenceSimulation: true
      }
    });
    const displayName = `Playwright ${Date.now()}`;

    await page.goto("/login");
    await passwordAndEmailLogin(page);
    await page.goto("/account-security");
    await expect(page.getByRole("heading", { name: "账号与安全" })).toBeVisible();

    await page.getByRole("button", { name: "添加 Passkey" }).click();
    await page.getByLabel("Passkey 名称").fill(displayName);
    await page.getByRole("dialog", { name: "添加 Passkey" }).locator("button[type='submit']").click();
    const registrationDialog = page.getByRole("dialog", { name: "验证并添加 Passkey" });
    await registrationDialog.locator("input[type='password']").fill(password!);
    const activationResponse = page.waitForResponse(
      (response) => response.url().endsWith("/passkey/credentials") && response.request().method() === "POST"
    );
    await registrationDialog.getByRole("button", { name: "验证并继续" }).click();
    const activation = await activationResponse;
    expect(activation.status(), await activation.text()).toBe(201);
    await page.waitForTimeout(250);
    expect(pageErrors).toEqual([]);
    const registrationNotice = page.locator(".ant-message-notice-content").last();
    await expect(registrationNotice).toBeVisible();
    expect(await registrationNotice.textContent()).toBe("Passkey 已添加");
    await expect(page.getByRole("heading", { name: "登录管理端" })).toBeVisible();

    let capturedProof: { flowId: string; body: unknown } | null = null;
    page.on("request", (request) => {
      const matched = request.url().match(/\/auth\/flows\/([^/]+)\/passkey\/proofs$/);
      if (matched && request.method() === "POST") {
        capturedProof = { flowId: decodeURIComponent(matched[1]!), body: request.postDataJSON() };
      }
    });
    await page.getByRole("button", { name: "使用 Passkey 登录" }).click();
    await expect(page.getByRole("heading", { name: "验证登录" })).toBeVisible();
    expect(capturedProof).not.toBeNull();

    const replay = await page.request.post(
      `${backend}/auth/flows/${encodeURIComponent(capturedProof!.flowId)}/passkey/proofs`,
      { data: capturedProof!.body, headers: { Origin: "http://localhost:5173" } }
    );
    expect(replay.ok()).toBe(false);

    const beforeEmail = await latestMessageId(page.request);
    await page.getByText("邮件验证码", { exact: true }).click();
    await page.getByRole("button", { name: "发送验证码" }).click();
    const code = await waitForEmailCode(page.request, beforeEmail);
    await page.getByRole("textbox", { name: /验证码/ }).fill(code);
    await page.getByRole("button", { name: "验证并登录" }).click();
    await expect(page).not.toHaveURL(/\/login/);

    const stored = await page.evaluate(() => {
      const raw = sessionStorage.getItem("sca4d-admin.session.tab");
      return raw ? JSON.parse(raw) as SessionStorageValue : null;
    });
    expect(stored).not.toBeNull();
    const authHeaders = { [stored!.session.tokenName]: stored!.session.tokenValue };
    const accountsAfterLogin = await page.request.get(`${backend}/me/accounts`, { headers: authHeaders });
    expect(accountsAfterLogin.ok()).toBe(true);
    const accountPayload = await accountsAfterLogin.json();
    const registered = accountPayload.data
      .flatMap((account: { accountId: string; passkeyCredentials: Array<{ credentialId: string; displayName: string; version: string }> }) =>
        account.passkeyCredentials.map((credential) => ({ ...credential, accountId: account.accountId }))
      )
      .find((credential: { displayName: string }) => credential.displayName === displayName);
    expect(registered).toBeTruthy();
    const versionAfterLogin = BigInt(registered.version);
    expect(versionAfterLogin).toBeGreaterThan(0n);

    await page.goto("/account-security");
    const row = page.getByText(displayName, { exact: true }).locator("xpath=ancestor::*[contains(@class,'fact-row')]");
    await row.getByRole("button", { name: "删除 Passkey" }).click();
    const deleteDialog = page.getByRole("dialog", { name: "删除 Passkey" });
    await deleteDialog.getByText("Passkey", { exact: true }).click();
    // 强制 assertion 清除 UV 标志，验证后端拒绝未完成用户验证的响应且页面保留当前 Session。
    await cdp.send("WebAuthn.setResponseOverrideBits", { authenticatorId, isBadUV: true });
    const rejectedProofResponse = page.waitForResponse(
      (response) => response.request().method() === "POST" && response.url().endsWith("/passkey/proofs")
    );
    await deleteDialog.getByRole("button", { name: "验证并删除" }).click();
    expect((await rejectedProofResponse).status()).toBe(401);
    await expect(page).toHaveURL(/\/account-security/);
    await expect(deleteDialog).toBeVisible();
    await expect(page.getByText(displayName, { exact: true })).toBeVisible();
    await expect(deleteDialog.getByRole("button", { name: "验证并删除" })).not.toHaveClass(/ant-btn-loading/);
    expect(pageErrors).toEqual([]);

    await cdp.send("WebAuthn.setResponseOverrideBits", { authenticatorId, isBadUV: false });
    let versionAfterStepUp: bigint | null = null;
    page.on("response", async (response) => {
      if (response.request().method() !== "GET" || !response.url().endsWith("/me/accounts")) return;
      const payload = await response.json().catch(() => null);
      const current = payload?.data
        ?.flatMap((account: { passkeyCredentials: Array<{ displayName: string; version: string }> }) =>
          account.passkeyCredentials)
        ?.find((credential: { displayName: string }) => credential.displayName === displayName);
      if (current) versionAfterStepUp = BigInt(current.version);
    });
    const confirmDelete = deleteDialog.getByRole("button", { name: "验证并删除" });
    const deletionResponse = page.waitForResponse(
      (response) => response.request().method() === "DELETE" && response.url().includes("/passkey/credentials/")
    );
    await confirmDelete.focus();
    await page.keyboard.press("Enter");
    expect((await deletionResponse).status()).toBe(204);
    await expect(page).toHaveURL(/\/login/);
    expect(versionAfterStepUp).not.toBeNull();
    expect(versionAfterStepUp!).toBeGreaterThan(versionAfterLogin);

    const oldSession = await page.request.get(`${backend}/me/accounts`, { headers: authHeaders });
    expect(oldSession.status()).toBe(401);
    expect(pageErrors).toEqual([]);
  });

});
