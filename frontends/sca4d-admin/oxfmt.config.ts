import { defineConfig, oxfmtConfig } from '@vben/oxfmt-config';

export default defineConfig({
  // 管理端 Vue 的标签换行与 ESLint 保持一致；其余 workspace 沿用 Vben 默认值。
  overrides: [
    ...(oxfmtConfig.overrides ?? []),
    {
      files: ['apps/sca4d-admin/**/*.vue'],
      options: { htmlWhitespaceSensitivity: 'ignore' },
    },
  ],
  ignorePatterns: [
    'dist',
    'dev-dist',
    '.local',
    '.claude',
    '.agent',
    '.agents',
    '.codex',
    '.output.js',
    'node_modules',
    '.nvmrc',
    'coverage',
    'CODEOWNERS',
    '.nitro',
    '.output',
    '**/*.svg',
    '**/*.sh',
    'public',
    '.npmrc',
    '*-lock.yaml',
    'skills-lock.json',
  ],
});
