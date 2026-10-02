<script setup lang="ts" generic="Row extends object">
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { watch } from 'vue';

import { useSjVxeGrid } from './use-sj-vxe-grid';
/** 展示 Controller 已读取的数据；服务端分页通过 page-change 请求调用方读取。 */
const props = withDefaults(
  defineProps<{
    /** 字段、标题和宽度；cell 插槽补充复杂业务视图。 */ columns: NonNullable<
      VxeTableGridOptions<Row>['columns']
    >;
    /** 主列表占满剩余高度，章节表格使用自然高度。 */ fillHeight?: boolean;
    /** 当前查询结果，不由组件修改。 */ items: readonly Row[];
    /** 稳定行标识字段。 */ keyField: string;
    /** 服务端页码、大小和总数；未提供时关闭分页。 */ pagination?: {
      currentPage: number;
      pageSize: number;
      total: number;
    };
  }>(),
  { pagination: undefined, fillHeight: false },
);
const emit = defineEmits<{
  /** 服务端页码或页大小改变。 */ pageChange: [page: number, size: number];
}>();
const [Grid, api] = useSjVxeGrid<Row>({
  gridOptions: {
    proxyConfig: { enabled: false },
    toolbarConfig: { enabled: false },
    height: props.fillHeight ? 'auto' : undefined,
    rowConfig: { keyField: props.keyField },
  },
  gridEvents: {
    pageChange: ({
      currentPage,
      pageSize,
    }: {
      currentPage: number;
      pageSize: number;
    }) => emit('pageChange', currentPage, pageSize),
  },
});
watch(
  () => [props.items, props.columns, props.pagination] as const,
  () => {
    api.setState({ tableData: [...props.items] });
    api.setGridOptions({
      columns: props.columns.map((column) => ({
        ...column,
        ...(column.field === 'actions'
          ? {
              fixed: 'right',
              align: 'center',
              headerAlign: 'center',
              showOverflow: false,
            }
          : {}),
        slots: { default: 'cell' },
      })),
      pagerConfig: {
        enabled: Boolean(props.pagination),
        ...props.pagination,
        pageSizes: [10, 20, 50, 100],
      },
    });
  },
  { immediate: true },
);
/** 只展示标量字段，复杂资源字段由明确的业务插槽展示。 */
function cellValue(row: Row, field: string) {
  const value = Reflect.get(row, field);
  return typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean'
    ? String(value)
    : '';
}
</script>
<template>
  <Grid>
    <template #cell="{ column, row }">
      <slot
        name="cell"
        :column="column"
        :row="row"
        :value="column.field ? cellValue(row, column.field) : ''"
      >
        {{ column.field ? cellValue(row, column.field) : '' }}
      </slot>
    </template>
  </Grid>
</template>
