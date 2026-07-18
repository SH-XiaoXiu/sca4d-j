import { expect, test } from "@playwright/test";
import { mockApi } from "./support/admin";

test("平台账号详情按资料、安全和设备分区，并以 STEP_UP 与 If-Match 重新生成恢复码", async ({ page }) => {
  await mockApi(page);
  await page.goto("/platform/accounts/10001");

  await expect(page.getByRole("heading", { name: "平台账号" })).toBeVisible();

  const drawer = page.locator(".ant-drawer-content:visible");
  await expect(drawer.getByText("基本资料", { exact: true })).toBeVisible();
  await drawer.getByText("登录与安全", { exact: true }).click();
  await expect(drawer.getByText("登录账号", { exact: true })).toBeVisible();
  await expect(drawer.getByText("平台角色", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "重新生成恢复码" }).click();

  const secondStep = page.locator(".ant-modal-content").filter({ hasText: "重新生成恢复码" });
  await secondStep.locator("input[type='password']").fill("current-secret");
  const requestPromise = page.waitForRequest((request) =>
    request.method() === "POST" && request.url().endsWith("/mg/platform/accounts/10001/recovery-codes")
  );
  await secondStep.getByRole("button", { name: "完成二步验证并继续" }).click();
  const request = await requestPromise;
  expect(request.headers()["if-match"]).toBe('"1"');
  expect(request.headers()["x-authentication-id"]).toMatch(/^authentication-step-up-\d+$/);
  await expect(page.locator(".ant-modal-content").filter({ hasText: "保存恢复码" }).locator("code")).toHaveCount(10);
  await expect(page.locator(".ant-modal-content").filter({ hasText: "保存恢复码" })).toBeVisible();
});

test("平台操作员只管理 PLATFORM Principal 的角色与状态", async ({ page }) => {
  await mockApi(page);
  await page.goto("/platform/operators");

  await expect(page.getByRole("heading", { name: "平台操作员" })).toBeVisible();
  const row = page.locator(".ant-table-row").filter({ hasText: "平台管理员" });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "平台角色" }).click();

  const roleDialog = page.locator(".ant-modal-content").filter({ hasText: "平台角色" });
  await expect(roleDialog.getByText("平台管理员", { exact: true })).toBeVisible();
  const requestPromise = page.waitForRequest((request) =>
    request.method() === "PUT" && request.url().endsWith("/mg/platform/operators/11001/roles")
  );
  await roleDialog.getByRole("button", { name: "确 定" }).click();
  expect((await requestPromise).postDataJSON()).toEqual({ roleIds: ["30001"], version: "1" });
  await expect(page.getByText("平台角色已更新", { exact: true })).toBeVisible();
  await expect(page.getByText("账号密码", { exact: true })).toHaveCount(0);
});

test("租户成员明确区分新建租户账号和使用既有账号", async ({ page }) => {
  await mockApi(page);
  await page.goto("/platform/tenants/20001/members");

  await expect(page.getByRole("heading", { name: "示例租户" })).toBeVisible();
  await page.getByRole("button", { name: "新建租户账号" }).click();
  const drawer = page.locator(".ant-drawer-content:visible");
  await expect(drawer.getByText("账号来源", { exact: true })).toBeVisible();
  await drawer.getByText("使用既有账号", { exact: true }).click();
  await drawer.locator(".ant-select").first().click();
  await page.getByText("租户管理员 · 10002", { exact: true }).click();
  await drawer.getByLabel("成员显示名称").fill("既有账号成员");

  const requestPromise = page.waitForRequest((request) =>
    request.method() === "POST" && request.url().endsWith("/mg/platform/tenants/20001/members/existing-accounts")
  );
  await drawer.getByRole("button", { name: "加入租户" }).click();
  expect((await requestPromise).postDataJSON()).toMatchObject({
    accountId: "10002",
    displayName: "既有账号成员",
    roleIds: []
  });
  await expect(page.getByText("既有账号已加入租户成员", { exact: true })).toBeVisible();
});
