import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";

export default defineConfig(({ mode }) => {
  const environmentDirectory = fileURLToPath(new URL("../../", import.meta.url));
  const environment = loadEnv(mode, environmentDirectory, "");
  const apiTarget = process.env.SCA4D_API_TARGET ?? environment.SCA4D_API_TARGET ?? "http://localhost:8080";

  return {
    envDir: environmentDirectory,
    plugins: [vue()],
    resolve: {
      alias: [
        {
          find: "@sca4d/admin-common/openapi",
          replacement: fileURLToPath(new URL("../common/src/openapi.ts", import.meta.url))
        },
        {
          find: "@sca4d/admin-common",
          replacement: fileURLToPath(new URL("../common/src/index.ts", import.meta.url))
        },
        {
          find: "@",
          replacement: fileURLToPath(new URL("./src", import.meta.url))
        }
      ]
    },
    server: {
      port: 5173,
      proxy: {
        "/api": {
          target: apiTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, "")
        }
      }
    },
    build: {
      sourcemap: true
    },
    test: {
      environment: "jsdom",
      environmentOptions: {
        jsdom: { url: "http://localhost/" }
      },
      exclude: ["e2e/**", "node_modules/**", "dist/**"],
      setupFiles: ["./test/setup.ts"],
      restoreMocks: true
    }
  };
});
