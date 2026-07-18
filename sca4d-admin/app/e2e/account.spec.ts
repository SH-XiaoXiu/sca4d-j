import { expect, test } from "@playwright/test";
import { cropAndUploadAvatar, mockApi } from "./support/admin";

test("生产界面只保留真实基础能力", async ({ page }, testInfo) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await mockApi(page);
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "工作台" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "操作日志" })).toBeVisible();
  const viewportWidth = page.viewportSize()?.width ?? 0;
  if (viewportWidth <= 1100) await expect(page.locator(".user-menu-name")).toBeHidden();
  else await expect(page.locator(".user-menu-name")).toBeVisible();
  const headerFits = await page.locator(".admin-header").evaluate((element) =>
    element.scrollWidth <= element.clientWidth
  );
  expect(headerFits).toBe(true);
  await page.locator(".user-menu-button").click();
  const userDropdown = page.locator(".ant-dropdown:visible");
  const dropdownBox = await userDropdown.boundingBox();
  expect(dropdownBox).not.toBeNull();
  expect(dropdownBox!.x).toBeGreaterThanOrEqual(0);
  expect(dropdownBox!.x + dropdownBox!.width).toBeLessThanOrEqual(viewportWidth + 1);
  await page.keyboard.press("Escape");
  const reducedMotion = await page.locator(".workspace-tab").first().evaluate((element) => {
    const durations = getComputedStyle(element).transitionDuration
      .split(",")
      .map((value) => Number.parseFloat(value) * (value.trim().endsWith("ms") ? 0.001 : 1));
    return {
      requested: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
      maximumTransitionSeconds: Math.max(...durations)
    };
  });
  expect(reducedMotion.requested).toBe(true);
  expect(reducedMotion.maximumTransitionSeconds).toBeLessThanOrEqual(0.00001);
  const operationLogTarget = page.getByRole("button", { name: /操作日志/ });
  await operationLogTarget.focus();
  await operationLogTarget.press("Enter");
  await expect(page.getByRole("heading", { name: "操作日志" })).toBeVisible();
  await page.goto("/dashboard");
  if ((page.viewportSize()?.width ?? 0) > 900) {
    await page.locator(".ant-menu-submenu-title").filter({ hasText: "系统管理" }).click();
    await expect(page.getByRole("menuitem", { name: "操作日志" })).toBeVisible();
  }
  await expect(page.getByText("权限目录", { exact: true })).toHaveCount(0);
  await expect(page.getByText("错误码目录", { exact: true })).toHaveCount(0);
  await expect(page.locator(".ant-layout-sider-trigger")).toHaveCount(0);

  await page.goto("/profile");
  await expect(page.getByRole("heading", { name: "账号信息" })).toBeVisible();
  await expect(page.getByText("Identity", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Identity ID", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Principal", { exact: true })).toHaveCount(0);
  if (process.env.PLAYWRIGHT_SCREENSHOT === "on") {
    await page.screenshot({ path: testInfo.outputPath("account.png"), fullPage: true });
  }

  await page.goto("/system/operation-logs");
  await expect(page.getByText("更新系统配置", { exact: true }).filter({ visible: true })).toBeVisible();
  if (process.env.PLAYWRIGHT_SCREENSHOT === "on") {
    await page.screenshot({ path: testInfo.outputPath("operation-log.png"), fullPage: true });
  }

  await page.goto("/system/identities");
  await expect(page.getByRole("heading", { name: "页面不存在" })).toBeVisible();
  await page.goto("/__dev");
  await expect(page.getByRole("heading", { name: "页面不存在" })).toBeVisible();
  await expect(page.locator("body")).not.toHaveCSS("overflow-x", "scroll");
});

test("账号安全页按 Account 管理会话、三种二步验证方式和 TOTP 凭证", async ({ page }) => {
  await mockApi(page);
  await page.goto("/account-security");

  await expect(page.getByRole("heading", { name: "账号与安全" })).toBeVisible();
  await expect(page.getByRole("button", { name: /全局账号/ })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByText("当前登录账号")).toBeVisible();
  await page.getByRole("button", { name: "切换暗色主题" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.getByText("登录设备", { exact: true }).click();
  await expect(page.getByText("当前会话")).toBeVisible();
  expect(await page.locator("body").evaluate((body) => body.scrollWidth <= body.clientWidth)).toBe(true);

  const accountTargets = await page.locator(".account-option").evaluateAll((items) =>
    items.map((item) => {
      const box = item.getBoundingClientRect();
      return { width: box.width, height: box.height };
    })
  );
  expect(accountTargets.every((target) => target.width >= 40 && target.height >= 44)).toBe(true);

  await page.getByRole("button", { name: /租户账号 20001/ }).click();
  await expect(page.getByText("Safari on iPhone")).toBeVisible();
  await expect(page.getByText("Firefox on Linux")).toHaveCount(0);
  await page.getByText("二步验证", { exact: true }).first().click();
  await page.getByRole("button", { name: "启用二步验证" }).click();
  const secondFactorModal = page.locator(".ant-modal-content").filter({ hasText: "启用该账号的登录二步验证" });
  await expect(secondFactorModal.locator(".sensitive-action-warning")).toHaveCount(0);
  await expect(secondFactorModal.getByRole("button", { name: "完成二步验证并启用" }))
    .not.toHaveClass(/ant-btn-dangerous/);
  await secondFactorModal.locator("input[type='password']").fill("current-secret");
  const secondFactorRequest = page.waitForRequest(
    (request) =>
      request.method() === "PUT" && request.url().endsWith("/10002/second-factor/requirement")
  );
  await secondFactorModal.getByRole("button", { name: "完成二步验证并启用" }).click();
  expect((await secondFactorRequest).headers()["x-authentication-id"]).toMatch(
    /^authentication-step-up-\d+$/
  );
  const recoveryCodesModal = page.locator(".ant-modal-content").filter({ hasText: "保存恢复码" });
  await expect(recoveryCodesModal.locator("code")).toHaveCount(10);
  await recoveryCodesModal.getByRole("button", { name: /完\s*成/ }).click();
  await expect(page.getByRole("button", { name: "重新生成恢复码" })).toBeVisible();

  await page.getByText("验证方式", { exact: true }).click();
  await page.getByRole("button", { name: "设置身份验证器" }).click();
  const totpEnrollmentStepUp = page.locator(".ant-modal-content").filter({ hasText: "创建一次身份验证器设置" });
  await totpEnrollmentStepUp.getByText("身份验证器", { exact: true }).click();
  await totpEnrollmentStepUp.locator("input[inputmode='numeric']").fill("654321");
  const enrollmentRequest = page.waitForRequest(
    (request) => request.method() === "POST" && request.url().endsWith("/10002/totp/enrollments")
  );
  await totpEnrollmentStepUp.getByRole("button", { name: "完成二步验证并继续" }).click();
  expect((await enrollmentRequest).headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);

  const totpSetupModal = page.locator(".ant-modal-content").filter({ hasText: "设置身份验证器" });
  const qrCode = totpSetupModal.getByRole("img", { name: "身份验证器设置二维码" });
  await expect(qrCode).toBeVisible();
  expect(await qrCode.evaluate((image: HTMLImageElement) => image.naturalWidth > 0 && image.naturalHeight > 0)).toBe(true);
  const totpModalBox = await totpSetupModal.boundingBox();
  const totpViewport = page.viewportSize();
  expect(totpModalBox).not.toBeNull();
  expect(totpViewport).not.toBeNull();
  expect(totpModalBox!.x).toBeGreaterThanOrEqual(0);
  expect(totpModalBox!.x + totpModalBox!.width).toBeLessThanOrEqual(totpViewport!.width + 1);
  const activationCode = totpSetupModal.locator("input[inputmode='numeric']");
  await activationCode.fill("111111");
  await expect(activationCode).toHaveValue("111111");
  const activationRequest = page.waitForRequest(
    (request) => request.method() === "POST" && request.url().endsWith("/10002/totp/credentials")
  );
  await totpSetupModal.getByRole("button", { name: "启用身份验证器" }).click();
  expect((await activationRequest).postDataJSON()).toMatchObject({ code: "111111" });
  await expect(page.getByRole("button", { name: "更换身份验证器" })).toBeVisible();

  await page.getByRole("button", { name: "更换身份验证器" }).click();
  const totpReplaceStepUp = page.locator(".ant-modal-content").filter({ hasText: "新的身份验证器替换现有凭证" });
  await totpReplaceStepUp.getByText("身份验证器", { exact: true }).click();
  await totpReplaceStepUp.locator("input[inputmode='numeric']").fill("654321");
  await totpReplaceStepUp.getByRole("button", { name: "完成二步验证并继续" }).click();
  const totpReplaceModal = page.locator(".ant-modal-content").filter({ hasText: "更换身份验证器" });
  await expect(totpReplaceModal.getByRole("img", { name: "身份验证器设置二维码" })).toBeVisible();
  await totpReplaceModal.locator("input[inputmode='numeric']").fill("222222");
  const replaceRequest = page.waitForRequest(
    (request) => request.method() === "PUT" && request.url().endsWith("/10002/totp/credentials/40003")
  );
  await totpReplaceModal.getByRole("button", { name: "启用身份验证器" }).click();
  expect((await replaceRequest).postDataJSON()).toMatchObject({ code: "222222" });
  expect((await replaceRequest).headers()["if-match"]).toBe('"0"');

  await page.getByRole("button", { name: "删除身份验证器" }).click();
  const totpDeleteModal = page.locator(".ant-modal-content").filter({ hasText: "删除身份验证器" });
  await expect(totpDeleteModal.locator(".sensitive-action-warning")).toContainText("敏感操作确认");
  await expect(totpDeleteModal.getByRole("button", { name: "完成二步验证并删除" }))
    .toHaveClass(/ant-btn-dangerous/);
  await totpDeleteModal.getByText("身份验证器", { exact: true }).click();
  await totpDeleteModal.locator("input[inputmode='numeric']").fill("654321");
  const deleteTotpRequest = page.waitForRequest(
    (request) => request.method() === "DELETE" && request.url().includes("/10002/totp/credentials/40003")
  );
  await totpDeleteModal.getByRole("button", { name: "完成二步验证并删除" }).click();
  expect((await deleteTotpRequest).headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
  expect((await deleteTotpRequest).headers()["if-match"]).toBe('"1"');
  await expect(page.getByRole("button", { name: "设置身份验证器" })).toBeVisible();
  expect(await page.locator("body").evaluate((body) => body.scrollWidth <= body.clientWidth)).toBe(true);

  await page.getByText("登录设备", { exact: true }).click();
  await page.getByRole("button", { name: /全局账号/ }).click();
  await expect(page.getByText("Firefox on Linux")).toBeVisible();

  const passwordSession = page.locator(".session-row").filter({ hasText: "Firefox on Linux" });
  await passwordSession.getByRole("button", { name: "结束会话" }).click();
  const passwordModal = page.locator(".ant-modal-content").filter({ hasText: "Firefox on Linux" });
  await expect(passwordModal).toBeVisible();
  await expect(passwordModal.locator(".sensitive-action-warning")).toContainText("敏感操作确认");
  await expect(passwordModal.getByRole("button", { name: "完成二步验证并结束会话" }))
    .toHaveClass(/ant-btn-dangerous/);
  await passwordModal.locator("input[type='password']").fill("current-secret");
  const passwordDelete = page.waitForRequest(
    (request) => request.method() === "DELETE" && request.url().endsWith("/sessions/session-password")
  );
  await passwordModal.getByRole("button", { name: "完成二步验证并结束会话" }).click();
  expect((await passwordDelete).headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
  await expect(page.getByText("Firefox on Linux")).toHaveCount(0);

  const emailSession = page.locator(".session-row").filter({ hasText: "Edge on Windows" });
  await emailSession.getByRole("button", { name: "结束会话" }).click();
  const emailModal = page.locator(".ant-modal-content").filter({ hasText: "Edge on Windows" });
  await emailModal.getByText("邮件验证码", { exact: true }).click();
  await emailModal.getByRole("button", { name: "发送邮件验证码" }).click();
  await expect(emailModal.getByText("邮件验证码已发送至 ad***@example.test")).toBeVisible();
  const modalBox = await emailModal.boundingBox();
  const viewport = page.viewportSize();
  expect(modalBox).not.toBeNull();
  expect(viewport).not.toBeNull();
  expect(modalBox!.x).toBeGreaterThanOrEqual(0);
  expect(modalBox!.x + modalBox!.width).toBeLessThanOrEqual(viewport!.width + 1);
  await emailModal.locator("input[inputmode='numeric']").fill("123456");
  const emailDelete = page.waitForRequest(
    (request) => request.method() === "DELETE" && request.url().endsWith("/sessions/session-email")
  );
  await emailModal.getByRole("button", { name: "完成二步验证并结束会话" }).click();
  expect((await emailDelete).headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
  await expect(page.getByText("Edge on Windows")).toHaveCount(0);
  expect(await page.locator("body").evaluate((body) => body.scrollWidth <= body.clientWidth)).toBe(true);
});

test("当前账号默认头像覆盖裁剪替换、顶部刷新、删除回退和 409 恢复", async ({ page }) => {
  await mockApi(page);
  await page.goto("/profile");
  const profile = page.locator(".profile-avatar-editor");
  const avatarTrigger = profile.locator(".avatar-upload__trigger");
  const avatarHint = avatarTrigger.locator(".avatar-upload__hint");
  await expect(avatarHint).toHaveCSS("opacity", "0");
  await avatarTrigger.hover();
  await expect(avatarHint).toHaveCSS("opacity", "1");
  await avatarTrigger.focus();
  await expect(avatarHint).toHaveCSS("opacity", "1");

  await cropAndUploadAvatar(page, profile);
  await expect(page.getByText("头像已更新", { exact: true }).last()).toBeVisible();
  await expect(profile.locator(".default-profile-avatar-image img")).toBeVisible();
  await expect(page.locator(".user-menu-button .default-profile-avatar-image img")).toBeVisible();

  await profile.getByRole("button", { name: "删除头像" }).click();
  const deleteConfirm = page.locator(".ant-modal-confirm:visible");
  await expect(deleteConfirm.getByText("删除后将显示名称首字母，原图片会进入后台清理。")).toBeVisible();
  await deleteConfirm.getByRole("button", { name: "删除头像" }).click();
  await expect(page.getByText("头像已删除", { exact: true })).toBeVisible();
  await expect(profile.locator(".default-profile-avatar-image img")).toHaveCount(0);
  await expect(page.locator(".user-menu-button .default-profile-avatar-image img")).toHaveCount(0);

  let conflictPending = true;
  const conflictFileIds: string[] = [];
  await page.route("**/api/me/profile/avatar", async (route) => {
    if (route.request().method() !== "PUT" || !conflictPending) {
      if (route.request().method() === "PUT") {
        conflictFileIds.push((route.request().postDataJSON() as { fileId: string }).fileId);
      }
      await route.fallback();
      return;
    }
    conflictFileIds.push((route.request().postDataJSON() as { fileId: string }).fileId);
    conflictPending = false;
    await route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: 1007,
        messageKey: "error.iam.default-profile-changed",
        messageArgs: [],
        message: "默认资料已变化",
        data: null,
        traceId: "avatar-conflict",
        timestamp: String(Date.now())
      })
    });
  });
  await cropAndUploadAvatar(page, profile);
  await expect(page.getByText("默认资料已被其他操作修改，已加载最新头像；请重新确认操作", { exact: true }).first()).toBeVisible();
  await expect(profile.getByRole("button", { name: "重试使用" })).toBeVisible();
  await expect(profile.getByRole("button", { name: "放弃上传" })).toBeVisible();
  await expect(profile.locator(".default-profile-avatar-image img")).toBeVisible();
  await profile.getByRole("button", { name: "重试使用" }).click();
  await expect(page.getByText("头像已更新", { exact: true }).last()).toBeVisible();
  expect(conflictFileIds).toHaveLength(2);
  expect(conflictFileIds[1]).toBe(conflictFileIds[0]);
  await expect(profile.getByRole("button", { name: "重试使用" })).toHaveCount(0);

  let abandonConflict = true;
  await page.route("**/api/me/profile/avatar", async (route) => {
    if (route.request().method() !== "PUT" || !abandonConflict) {
      await route.fallback();
      return;
    }
    abandonConflict = false;
    await route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: 1007,
        messageKey: "error.iam.default-profile-changed",
        messageArgs: [],
        message: "默认资料已变化",
        data: null,
        traceId: "avatar-abandon-conflict",
        timestamp: String(Date.now())
      })
    });
  });
  await cropAndUploadAvatar(page, profile);
  await expect(profile.getByRole("button", { name: "放弃上传" })).toBeVisible();
  await profile.getByRole("button", { name: "放弃上传" }).click();
  await expect(profile.getByRole("button", { name: "重试使用" })).toHaveCount(0);
  await expect(profile.locator(".default-profile-avatar-image img")).toBeVisible();
});

test("当前账号默认头像写入成功后刷新失败只允许重新读取", async ({ page }) => {
  const api = await mockApi(page);
  let replaceRequests = 0;
  let deleteRequests = 0;
  page.on("request", (request) => {
    if (new URL(request.url()).pathname !== "/api/me/profile/avatar") return;
    if (request.method() === "PUT") replaceRequests += 1;
    if (request.method() === "DELETE") deleteRequests += 1;
  });
  await page.goto("/profile");
  const profile = page.locator(".profile-avatar-editor");
  await expect(profile).toBeVisible();

  api.failNextMeRefresh();
  await cropAndUploadAvatar(page, profile);
  await expect(page.getByText("头像已更新", { exact: true })).toBeVisible();
  await expect(page.getByText("头像数据刷新失败，请重新刷新", { exact: true })).toBeVisible();
  await expect(profile.getByRole("button", { name: "重试使用" })).toHaveCount(0);
  await expect(profile.getByRole("button", { name: "刷新头像" })).toBeVisible();
  await expect(profile.locator(".default-profile-avatar-image img")).toHaveAttribute("src", /^blob:/);
  await expect(page.locator(".user-menu-button .default-profile-avatar-image img")).toHaveAttribute("src", /^blob:/);
  expect(replaceRequests).toBe(1);
  await profile.getByRole("button", { name: "刷新头像" }).click();
  await expect(page.getByText("头像数据已刷新", { exact: true })).toBeVisible();
  await expect(profile.locator(".default-profile-avatar-image img")).toBeVisible();
  expect(replaceRequests).toBe(1);

  api.failNextMeRefresh();
  await profile.getByRole("button", { name: "删除头像" }).click();
  const deleteConfirm = page.locator(".ant-modal-confirm:visible");
  await deleteConfirm.getByRole("button", { name: "删除头像" }).click();
  await expect(page.getByText("头像已删除", { exact: true })).toBeVisible();
  await expect(page.getByText("头像数据刷新失败，请重新刷新", { exact: true }).first()).toBeVisible();
  await expect(deleteConfirm).toHaveCount(0);
  await expect(profile.getByRole("button", { name: "刷新头像" })).toBeVisible();
  await expect(profile.locator(".default-profile-avatar-image img")).toHaveCount(0);
  await expect(page.locator(".user-menu-button .default-profile-avatar-image img")).toHaveCount(0);
  await expect(profile.getByRole("button", { name: "删除头像" })).toHaveCount(0);
  expect(deleteRequests).toBe(1);
  await profile.getByRole("button", { name: "刷新头像" }).click();
  await expect(profile.locator(".default-profile-avatar-image img")).toHaveCount(0);
  await expect(profile.getByRole("button", { name: "删除头像" })).toHaveCount(0);
  expect(deleteRequests).toBe(1);
});
