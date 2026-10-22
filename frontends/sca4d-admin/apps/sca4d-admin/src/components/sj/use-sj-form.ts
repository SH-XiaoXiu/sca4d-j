import { useVbenForm } from '#/adapter/form';
/** 普通编辑表单统一标签、控件宽度和响应式字段间距；业务校验由 schema 声明。 */
export function useSjForm(options: Parameters<typeof useVbenForm>[0]) {
  return useVbenForm({
    layout: 'vertical',
    showDefaultActions: false,
    wrapperClass: 'grid-cols-1 gap-x-4 sm:grid-cols-2',
    ...options,
    commonConfig: {
      componentProps: { class: 'w-full' },
      ...options.commonConfig,
    },
  });
}
