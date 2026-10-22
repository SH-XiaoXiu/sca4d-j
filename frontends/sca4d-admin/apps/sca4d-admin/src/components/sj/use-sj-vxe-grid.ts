import { computed, watch } from 'vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';

/** 管理端 Grid 的业务字段、查询与事件声明。 */
export type SjVxeGridOptions<T extends object> = Parameters<
  typeof useVbenVxeGrid<T>
>[0];

/** Sj 列表页筛选区统一使用的响应式栅格。 */
export const SJ_FILTER_WRAPPER_CLASS =
  'grid-cols-1 sm:grid-cols-2 xl:grid-cols-4';

/**
 * 在 Vben VXE Grid 之上应用 Sj 列表的筛选、密度、工具栏和对齐规则。
 */
export function useSjVxeGrid<T extends object>(
  source: (() => SjVxeGridOptions<T>) | SjVxeGridOptions<T>,
) {
  const options = computed<SjVxeGridOptions<T>>(() => {
    const options = typeof source === 'function' ? source() : source;
    const schema = options.formOptions?.schema;
    const formOptions = options.formOptions
      ? {
          collapsedRows: 1,
          commonConfig: { componentProps: { class: 'w-full' } },
          compact: true,
          showCollapseButton: Array.isArray(schema) && schema.length > 4,
          submitOnEnter: true,
          wrapperClass: SJ_FILTER_WRAPPER_CLASS,
          ...options.formOptions,
        }
      : undefined;

    const columns = Array.isArray(options.gridOptions?.columns)
      ? options.gridOptions.columns.map((column) =>
          column?.field === 'actions'
            ? {
                ...column,
                align: 'center' as const,
                fixed: 'right' as const,
                headerAlign: 'center' as const,
                showOverflow: false,
                width: 136,
              }
            : column,
        )
      : options.gridOptions?.columns;

    return {
      ...options,
      formOptions,
      gridClass: ['sj-vxe-grid', options.gridClass],
      gridOptions: {
        align: 'left',
        border: false,
        cellConfig: { height: 56, verticalAlign: 'center' },
        columnConfig: { resizable: true },
        height: 'auto',
        minHeight: 180,
        round: true,
        showOverflow: true,
        size: 'small',
        toolbarConfig: {
          custom: true,
          export: false,
          refresh: true,
          search: Boolean(formOptions),
          zoom: true,
        },
        ...options.gridOptions,
        columns,
      },
    };
  });
  const [Grid, api] = useVbenVxeGrid<T>(options.value);
  if (typeof source === 'function')
    watch(options, (next) => api.setState(next));
  return [Grid, api] as const;
}
