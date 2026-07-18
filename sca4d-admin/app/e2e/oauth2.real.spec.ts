import { expect, test } from "@playwright/test";

test.skip(process.env.SCA4DJ_OAUTH2_REAL_E2E !== "true", "只在真实后端与标准模拟 OAuth2 服务同时运行时执行");

test("OAuth2 Authorization Code callback 创建 Account Session", async ({ page }) => {
  const requests: string[] = [];
  page.on("request", (request) => requests.push(request.url()));

  await page.goto("/login");
  const provider = page.getByRole("button", { name: /OAuth2 E2E/ });
  await expect(provider).toBeVisible();
  await provider.click();
  await page.waitForURL((url) => url.origin === "http://127.0.0.1:5175" && url.pathname === "/dashboard", {
    timeout: 20_000
  });

  expect(page.url()).not.toContain("oauth2FlowId");
  expect(page.url()).not.toContain("oauth2FlowState");
  expect(page.url()).not.toContain("code=");
  expect(page.url()).not.toContain("token");
  expect(requests).toContainEqual(expect.stringMatching(/^http:\/\/127\.0\.0\.1:19091\/authorize\?/));
  expect(requests).toContainEqual(expect.stringMatching(/^http:\/\/127\.0\.0\.1:8087\/auth\/oauth2\/callback\?/));
  expect(requests).toContainEqual(expect.stringMatching(/\/api\/auth\/flows\/[^/]+\/sessions$/));
  expect(await page.evaluate(() => sessionStorage.getItem("sca4d-admin.oauth2-login"))).toBeNull();
  expect(await page.locator("body").evaluate((body) => body.scrollWidth <= body.clientWidth)).toBe(true);
});

test("OAuth2 上游拒绝授权后返回登录页并清理当前状态", async ({ page }) => {
  await page.route(/^http:\/\/127\.0\.0\.1:19091\/authorize\?/, async (route) => {
    const authorization = new URL(route.request().url());
    const callback = new URL(authorization.searchParams.get("redirect_uri") ?? "");
    callback.searchParams.set("error", "access_denied");
    callback.searchParams.set("state", authorization.searchParams.get("state") ?? "");
    await route.fulfill({
      status: 302,
      headers: { Location: callback.toString(), "Cache-Control": "no-store" }
    });
  });
  await page.goto("/login");

  await page.getByRole("button", { name: /OAuth2 E2E/ }).click();

  await expect(page).toHaveURL((url) => url.pathname === "/login" && !url.searchParams.has("oauth2Failed"));
  await expect(page.getByText(/外部身份认证未完成|External identity authentication was not completed/)).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("sca4d-admin.oauth2-login"))).toBeNull();
  expect(await page.evaluate(() => sessionStorage.getItem("sca4d-admin.session.tab"))).toBeNull();
});
