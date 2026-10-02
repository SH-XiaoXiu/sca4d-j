import type { ModalApiOptions } from '@vben/common-ui';

import { useVbenModal } from '@vben/common-ui';

/** Sj 创建工作区支持的标准尺寸。 */
export type SjCreateModalSize = 'lg' | 'md' | 'xl';

/** Sj 创建工作区配置；其余选项直接使用 Vben Modal。 */
export interface SjCreateModalOptions extends ModalApiOptions {
  /** 创建表单复杂度对应的标准尺寸。 */
  size?: SjCreateModalSize;
}

const MODAL_WIDTHS: Record<SjCreateModalSize, string> = {
  lg: 'w-[min(920px,calc(100vw-32px))]!',
  md: 'w-[min(760px,calc(100vw-32px))]!',
  xl: 'w-[min(1120px,calc(100vw-32px))]!',
};

const MODAL_HEIGHTS: Record<SjCreateModalSize, string> = {
  lg: 'h-[min(680px,calc(100vh-32px))]',
  md: 'h-[min(560px,calc(100vh-32px))]',
  xl: 'h-[min(760px,calc(100vh-32px))]',
};

/**
 * 使用 Vben Modal 创建 Sj 居中创建工作区，并统一尺寸、滚动和底部留白。
 */
export function useSjCreateModal(options: SjCreateModalOptions = {}) {
  const {
    class: modalClass,
    contentClass,
    footerClass,
    size = 'lg',
    ...modalOptions
  } = options;
  return useVbenModal({
    ...modalOptions,
    centered: true,
    class: [
      MODAL_WIDTHS[size],
      MODAL_HEIGHTS[size],
      'max-h-[calc(100vh-32px)]',
      modalClass,
    ],
    contentClass: ['min-h-0 flex-1 overflow-y-auto p-5 sm:p-6', contentClass],
    destroyOnClose: modalOptions.destroyOnClose ?? true,
    footerClass: ['px-5 py-3 sm:px-6', footerClass],
    fullscreenButton: false,
  });
}
