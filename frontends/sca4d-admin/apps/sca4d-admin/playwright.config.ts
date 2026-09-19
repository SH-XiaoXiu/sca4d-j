import { defineConfig, devices } from 'playwright/test';

/** Sca4d-J 默认前端示例的浏览器验收配置。 */
export default defineConfig({
  expect: { timeout: 10_000 },
  fullyParallel: true,
  reporter: 'list',
  testDir: './e2e',
  use: {
    baseURL: 'http://127.0.0.1:5666',
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH }
      : undefined,
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'pnpm dev -- --host 127.0.0.1',
    cwd: '../..',
    reuseExistingServer: true,
    timeout: 120_000,
    url: 'http://127.0.0.1:5666',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-chromium', use: { ...devices['Pixel 7'] } },
  ],
});
