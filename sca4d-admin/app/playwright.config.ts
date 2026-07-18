import { defineConfig, devices } from "@playwright/test";
import { fileURLToPath, URL } from "node:url";
import { loadEnv } from "vite";

const environment = loadEnv("development", fileURLToPath(new URL("../../", import.meta.url)), "");
const executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH ?? environment.PLAYWRIGHT_EXECUTABLE_PATH;
const screenshot = process.env.PLAYWRIGHT_SCREENSHOT ?? environment.PLAYWRIGHT_SCREENSHOT;
const externalBaseUrl = process.env.SCA4D_E2E_BASE_URL;
const localBrowser = executablePath ? { browserName: "chromium" as const } : {};

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  reporter: "html",
  use: {
    baseURL: externalBaseUrl ?? "http://127.0.0.1:4173",
    locale: "zh-CN",
    trace: "on-first-retry",
    screenshot: screenshot === "on" ? "on" : "only-on-failure",
    ...(executablePath ? { launchOptions: { executablePath } } : {})
  },
  ...(externalBaseUrl
    ? {}
    : { webServer: {
        command: "pnpm build && pnpm exec vite preview --host 127.0.0.1 --port 4173",
        port: 4173,
        reuseExistingServer: true
      } }),
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } } },
    {
      name: "compact-desktop",
      use: { ...devices["Desktop Chrome"], viewport: { width: 1024, height: 768 } }
    },
    {
      name: "tablet",
      use: { ...devices["iPad (gen 7)"], viewport: { width: 768, height: 1024 }, ...localBrowser }
    },
    { name: "mobile", use: { ...devices["iPhone 13"], ...localBrowser } }
  ]
});
