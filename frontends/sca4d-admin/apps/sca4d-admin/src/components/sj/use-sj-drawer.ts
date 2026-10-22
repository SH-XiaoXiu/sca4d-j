import type { DrawerApiOptions } from '@vben/common-ui';

import { useVbenDrawer } from '@vben/common-ui';

/** Sj 编辑抽屉支持的标准宽度。 */
export type SjDrawerSize = 'lg' | 'md' | 'sm' | 'xl';

/** Sj 编辑抽屉配置；其余选项直接使用 Vben Drawer。 */
export interface SjDrawerOptions extends DrawerApiOptions {
  /** 表单复杂度对应的标准宽度。 */
  size?: SjDrawerSize;
}

const DRAWER_WIDTHS: Record<SjDrawerSize, string> = {
  lg: 'w-[760px]',
  md: 'w-[560px]',
  sm: 'w-[480px]',
  xl: 'w-[960px]',
};

/**
 * 使用 Vben Drawer 创建 Sj 编辑抽屉，并统一宽度、正文和底部留白。
 */
export function useSjDrawer(options: SjDrawerOptions = {}) {
  const {
    class: drawerClass,
    contentClass,
    footerClass,
    size = 'md',
    ...drawerOptions
  } = options;
  return useVbenDrawer({
    ...drawerOptions,
    class: [DRAWER_WIDTHS[size], 'max-w-full', drawerClass],
    contentClass: ['p-4 sm:p-5', contentClass].filter(Boolean).join(' '),
    footerClass: ['px-4 py-3 sm:px-5', footerClass],
  });
}
