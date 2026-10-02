<script setup lang="ts">
import { computed, watch } from 'vue';

import { useSjDraftGuard } from '#/features/sj-draft-guard';
import { $t } from '#/locales';

import { useSjCreateModal } from './use-sj-create-modal';
import { useSjDrawer } from './use-sj-drawer';
/** 一个资源表单的创建或编辑工作区；调用方保存草稿并执行写入。 */
const props = withDefaults(
  defineProps<{
    /** 创建资源使用居中工作区，编辑使用抽屉。 */
    create?: boolean;
    /** 是否有未保存草稿。 */
    dirty?: boolean;
    /** 是否正在读取表单初始值。 */ loading?: boolean;
    /** 是否仅展示内容。 */ readOnly?: boolean;
    /** 是否正在保存。 */ saving?: boolean;
    /** 表单复杂度对应的标准尺寸。 */ size?: 'lg' | 'md' | 'sm' | 'xl';
    /** 无权限或初始值未就绪时停止提交。 */ submitDisabled?: boolean;
    /** 工作区标题。 */ title: string;
  }>(),
  {
    dirty: false,
    create: false,
    loading: false,
    readOnly: false,
    saving: false,
    size: undefined,
    submitDisabled: false,
  },
);
const emit = defineEmits<{
  /** 工作区关闭后释放秘密、本地预览和深链接参数。 */
  closed: [];
  /** 校验并提交草稿；成功关闭由调用方决定。 */
  submit: [];
}>();
const open = defineModel<boolean>('open', { required: true });
/** 保存期间保护正在写入的对象，其他时候允许取消。 */
async function beforeClose() {
  const allowed = await confirmDiscard(props.dirty);
  if (!allowed) open.value = true;
  return allowed;
}
/** 同步容器关闭结果，保持打开状态只有一个所有者。 */
function changed(value: boolean) {
  open.value = value;
}
const { confirmDiscard } = useSjDraftGuard(
  () => open.value && props.dirty,
  () => open.value && props.saving,
);
const [CreateModal, createApi] = useSjCreateModal({
  onBeforeClose: beforeClose,
  onOpenChange: changed,
  onClosed: () => emit('closed'),
  onConfirm: () => emit('submit'),
});
const [Drawer, drawerApi] = useSjDrawer({
  onBeforeClose: beforeClose,
  onOpenChange: changed,
  onClosed: () => emit('closed'),
  onConfirm: () => emit('submit'),
});
const container = computed(() => (props.create ? CreateModal : Drawer));
watch(
  () =>
    [
      open.value,
      props.create,
      props.title,
      props.saving,
      props.loading,
      props.readOnly,
      props.submitDisabled,
      props.size,
    ] as const,
  () => {
    const api = props.create ? createApi : drawerApi;
    const size = props.size ?? (props.create ? 'lg' : 'md');
    const widths = props.create
      ? { sm: 'w-[760px]', md: 'w-[760px]', lg: 'w-[920px]', xl: 'w-[1120px]' }
      : { sm: 'w-[480px]', md: 'w-[560px]', lg: 'w-[760px]', xl: 'w-[960px]' };
    api.setState({
      title: props.title,
      loading: props.loading,
      confirmLoading: props.saving,
      confirmDisabled:
        props.submitDisabled || props.loading || props.readOnly || props.saving,
      showConfirmButton: !props.readOnly,
      cancelText: $t('sj.cancel'),
      confirmText: $t('sj.save'),
      class: props.create
        ? [
            widths[size],
            'max-w-[calc(100vw-32px)]',
            'max-h-[calc(100dvh-32px)]',
            'h-[min(680px,calc(100dvh-32px))]',
          ]
        : [widths[size], 'max-w-full'],
      ...(props.create ? { centered: true } : {}),
    });
    if (open.value) api.open();
    else if (api.store.state.isOpen) api.close();
  },
  { immediate: true },
);
</script>
<template>
  <component :is="container">
    <template v-if="$slots.extra" #extra><slot name="extra"></slot></template>
    <slot></slot>
    <template v-if="$slots.footer" #footer>
      <slot name="footer"></slot>
    </template>
  </component>
</template>
