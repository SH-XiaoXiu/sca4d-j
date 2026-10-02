<script setup lang="ts">
import { watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { $t } from '#/locales';
/** 删除确认或动作认证的小尺寸对话框，不用于资源编辑。 */
const props = withDefaults(
  defineProps<{
    /** 确认动作的明确名称。 */
    confirmText?: string;
    /** 是否造成对象或访问失效。 */
    danger?: boolean;
    /** 是否正在提交。 */
    saving?: boolean;
    /** 输入尚未满足动作要求时停止提交。 */
    submitDisabled?: boolean;
    /** 对话框标题。 */
    title: string;
  }>(),
  {
    danger: false,
    saving: false,
    submitDisabled: false,
    confirmText: undefined,
  },
);
const emit = defineEmits<{
  /** 用户取消动作。 */
  cancel: [];
  /** 清理当前操作秘密。 */
  closed: [];
  /** 请求执行确认动作。 */
  submit: [];
}>();
const open = defineModel<boolean>('open', { required: true });
const [Modal, api] = useVbenModal({
  centered: true,
  class: 'w-[480px] max-w-[calc(100vw-32px)]',
  fullscreenButton: false,
  onBeforeClose: () => !props.saving,
  onOpenChange: (value) => {
    open.value = value;
  },
  onCancel: () => {
    emit('cancel');
  },
  onClosed: () => emit('closed'),
});
watch(
  () => [open.value, props.title, props.saving] as const,
  () => {
    api.setState({ title: props.title });
    if (open.value) api.open();
    else if (api.store.state.isOpen) api.close();
  },
  { immediate: true },
);
</script>
<template>
  <Modal>
    <slot></slot>
    <template #footer>
      <div class="flex justify-end gap-2">
        <a-button
          :disabled="saving"
          @click="
            emit('cancel');
            open = false;
          "
        >
          {{ $t('sj.cancel') }}
        </a-button>
        <a-button
          type="primary"
          :danger="danger"
          :loading="saving"
          :disabled="submitDisabled || saving"
          @click="emit('submit')"
        >
          {{ confirmText ?? $t('sj.save') }}
        </a-button>
      </div>
    </template>
  </Modal>
</template>
