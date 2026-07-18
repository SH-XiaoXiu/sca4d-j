import { expect, test } from "@playwright/test";
import { mockApi } from "./support/admin";

test("工作区标签记录页面并保持操作状态", async ({ page }) => {
  await mockApi(page);
  await page.goto("/dashboard");

  await page.locator(".page-container").evaluate((element) => {
    (element as HTMLElement).style.minHeight = "1600px";
    const routeRoot = element.firstElementChild as HTMLElement | null;
    if (!routeRoot) throw new Error("Current route root is missing");
    routeRoot.dataset.e2eLocaleInstance = "before";
    window.scrollTo(0, 240);
  });
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(240);
  const englishMeRequest = page.waitForRequest((request) =>
    new URL(request.url()).pathname === "/api/me"
      && request.headers()["accept-language"]?.startsWith("en") === true
  );
  await page.getByRole("button", { name: "语言" }).click();
  const englishLanguage = page.getByRole("menuitem", { name: "English" });
  await englishLanguage.focus();
  await page.keyboard.press("Enter");
  await englishMeRequest;
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.locator("[data-e2e-locale-instance='before']")).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(240);
  const chineseMeRequest = page.waitForRequest((request) =>
    new URL(request.url()).pathname === "/api/me"
      && request.headers()["accept-language"]?.startsWith("zh") === true
  );
  await page.getByRole("button", { name: "Language" }).click();
  const chineseLanguage = page.getByRole("menuitem", { name: "简体中文" });
  await chineseLanguage.focus();
  await page.keyboard.press("Enter");
  await chineseMeRequest;
  await expect(page.getByRole("heading", { name: "工作台" })).toBeVisible();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(240);

  await page.goto("/system/operation-logs?page=1");
  await expect(page.getByRole("heading", { name: "操作日志" })).toBeVisible();
  await page.goto("/system/operation-logs?page=3");
  await expect(page).toHaveURL(/\/system\/operation-logs\?page=3$/);
  await page.goBack();
  await expect(page).toHaveURL(/\/system\/operation-logs\?page=1$/);
  await expect(page.getByRole("heading", { name: "操作日志" })).toBeVisible();
  await page.goForward();
  await expect(page).toHaveURL(/\/system\/operation-logs\?page=3$/);
  await expect(page.getByRole("heading", { name: "操作日志" })).toBeVisible();

  const viewportWidth = page.viewportSize()?.width ?? 0;
  const tabStrip = page.getByRole("navigation", { name: "工作区标签" });
  if (viewportWidth <= 560) {
    await expect(tabStrip).toBeHidden();
    await expect(page.getByText("更新系统配置", { exact: true }).filter({ visible: true })).toBeVisible();
    await expect
      .poll(() =>
        page.evaluate(() => {
          const raw = localStorage.getItem("sca4d-admin.workspace.v1.10001");
          return raw ? (JSON.parse(raw) as { tabs: unknown[] }).tabs.length : 0;
        })
      )
      .toBe(2);
    return;
  }

  await expect(tabStrip).toBeVisible();
  await expect(page.getByRole("tab", { name: "工作台" })).toBeVisible();

  await expect(page.getByRole("tab", { name: "操作日志" })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("tab", { name: "操作日志" })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("tab", { name: "操作日志" })).toHaveCount(1);

  const openFilters = async (): Promise<void> => {
    await page
      .getByRole("button", { name: viewportWidth <= 900 ? "筛选" : /高级筛选/ })
      .click();
  };
  await openFilters();
  const filterDrawer = page.locator(".ant-drawer-content").filter({ hasText: "高级筛选" });
  const traceInput = filterDrawer
    .locator(".ant-form-item")
    .filter({ hasText: "追踪编号" })
    .locator("input");
  await traceInput.fill("trace-workspace");
  await page.keyboard.press("Escape");
  await page.getByRole("tab", { name: "工作台" }).click();
  await page.getByRole("tab", { name: "操作日志" }).click();
  await openFilters();
  await expect(traceInput).toHaveValue("trace-workspace");
  await page.keyboard.press("Escape");

  await page.goto("/profile");
  await expect(page.getByRole("heading", { name: "账号信息" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "账号信息" })).toHaveAttribute("aria-selected", "true");
  await page.goto("/");
  await expect(page).toHaveURL(/\/profile$/);
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.getByRole("tab", { name: "账号信息" }).click();

  const profileTabItem = page.locator(".workspace-tab[data-tab-key='route:profile']");
  const operationTabItem = page.locator(".workspace-tab[data-tab-key='route:operation-logs']");
  await profileTabItem.dragTo(operationTabItem, { targetPosition: { x: 2, y: 16 } });
  await expect
    .poll(() =>
      page.getByRole("tab").evaluateAll((tabs) => tabs.map((tab) => tab.textContent?.trim()))
    )
    .toEqual(["工作台", "账号信息", "操作日志"]);
  await page.getByRole("tab", { name: "账号信息" }).focus();
  await page.keyboard.press("Alt+ArrowRight");
  await expect
    .poll(() =>
      page.getByRole("tab").evaluateAll((tabs) => tabs.map((tab) => tab.textContent?.trim()))
    )
    .toEqual(["工作台", "操作日志", "账号信息"]);

  const markedDirty = await page.evaluate(() => {
    const app = document.querySelector("#app") as HTMLElement & {
      __vue_app__?: {
        config: {
          globalProperties: {
            $pinia?: { _s: Map<string, { markCurrentDirty: (dirty: boolean) => void }> };
          };
        };
      };
    };
    const workspace = app.__vue_app__?.config.globalProperties.$pinia?._s.get("workspace");
    workspace?.markCurrentDirty(true);
    return Boolean(workspace);
  });
  expect(markedDirty).toBe(true);

  await page.getByRole("button", { name: "语言" }).click();
  await page.getByRole("menuitem", { name: "English" }).click();
  await expect(page.getByText("切换语言并放弃未保存的更改？")).toBeVisible();
  await page.getByRole("button", { name: /取\s*消/ }).click();
  await expect(page.getByRole("heading", { name: "账号信息" })).toBeVisible();

  await page.getByRole("button", { name: "关闭账号信息" }).click();
  await expect(page.getByText("放弃未保存的更改？")).toBeVisible();
  await page.getByRole("button", { name: /取\s*消/ }).click();
  await expect(page.getByRole("tab", { name: /账号信息/ })).toBeVisible();
  await page.getByRole("button", { name: "关闭账号信息" }).click();
  await page.getByRole("button", { name: "仍然关闭" }).click();
  await expect(page.getByRole("tab", { name: /账号信息/ })).toHaveCount(0);

  const dashboardTab = page.getByRole("tab", { name: "工作台" });
  await dashboardTab.focus();
  await page.keyboard.press("ArrowRight");
  await expect(page.getByRole("tab", { name: "操作日志" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/system\/operation-logs\?page=3$/);

  await page.goto("/profile");
  await expect(page.getByRole("tab", { name: "账号信息" })).toHaveAttribute("aria-selected", "true");
  await page.getByRole("tab", { name: "操作日志" }).click({ button: "right" });
  await page.getByRole("menuitem", { name: "关闭其他标签" }).click();
  await expect(page.getByRole("tab")).toHaveCount(2);
  await page.getByRole("tab", { name: "操作日志" }).click({ button: "right" });
  await page.getByRole("menuitem", { name: "关闭全部标签" }).click();
  await expect(page.getByRole("tab")).toHaveCount(1);
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.evaluate(() => {
    const app = document.querySelector("#app") as HTMLElement & {
      __vue_app__?: {
        config: {
          globalProperties: {
            $pinia?: {
              _s: Map<
                string,
                {
                  visitRoute: (route: unknown) => void;
                  activate: (key: string) => void;
                }
              >;
            };
          };
        };
      };
    };
    const workspace = app.__vue_app__?.config.globalProperties.$pinia?._s.get("workspace");
    for (let index = 1; index <= 18; index += 1) {
      workspace?.visitRoute({
        path: `/test-${index}`,
        fullPath: `/test-${index}`,
        name: `test-${index}`,
        meta: { title: `测试页面 ${index}`, workspace: true }
      });
    }
    workspace?.activate("route:dashboard");
  });
  const scroller = page.locator(".workspace-tabs__scroller");
  await expect.poll(() => scroller.evaluate((element) => element.scrollWidth > element.clientWidth)).toBe(true);
  const before = await scroller.evaluate((element) => element.scrollLeft);
  await scroller.dispatchEvent("wheel", { deltaY: 280 });
  await expect.poll(() => scroller.evaluate((element) => element.scrollLeft)).toBeGreaterThan(before);
});
