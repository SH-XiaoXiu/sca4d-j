import { existsSync } from 'node:fs';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { defineConfig } from '@vben/vite-config';

const frontendEnvironmentFile = fileURLToPath(
  new URL('../../../../.env/frontend.env', import.meta.url),
);
if (existsSync(frontendEnvironmentFile)) {
  process.loadEnvFile(frontendEnvironmentFile);
}

export default defineConfig(async () => {
  return {
    application: {
      nitroMock: false,
    },
    vite: {
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            target: process.env.SCA4DJ_API_TARGET || 'http://localhost:8088',
          },
        },
      },
    },
  };
});
