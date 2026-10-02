import { defineConfig } from '@vben/eslint-config';

export default defineConfig([
  {
    files: ['apps/sca4d-admin/src/**/*.{ts,vue}'],
    rules: {
      // 应用可以使用自己的 HTTP 和 Locale 入口，组件创建规则只限制业务视图。
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['#/layouts/*', '#/stores/*'],
              message: '使用现有布局与 Store 的公开入口。',
            },
          ],
        },
      ],
    },
  },
  {
    files: [
      'apps/sca4d-admin/src/pages/**/*.vue',
      'apps/sca4d-admin/src/features/**/*.vue',
      'apps/sca4d-admin/src/views/_core/authentication/**/*.vue',
    ],
    rules: {
      'vue/no-restricted-html-elements': [
        'error',
        {
          element: ['a-table', 'a-modal', 'a-drawer'],
          message:
            '使用 SjDataGrid、SjResourceEditor 或 SjDialog，保持分页与工作区行为一致。',
        },
      ],
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: '@vben/common-ui',
              importNames: ['Page', 'useVbenModal', 'useVbenDrawer'],
              message: '业务视图使用 Sj 页面与工作区入口。',
            },
            {
              name: '#/adapter/vxe-table',
              importNames: ['useVbenVxeGrid'],
              message: '业务列表使用 useSjVxeGrid。',
            },
          ],
          patterns: [
            {
              group: ['#/layouts/*', '#/stores/*'],
              message: '使用现有布局与 Store 的公开入口。',
            },
          ],
        },
      ],
    },
  },
]);
