<script setup lang="ts">
defineOptions({ name: 'SjConfirmSwitch', inheritAttrs: false });

const props = defineProps<{
  /** 开关的可访问名称。 */
  ariaLabel?: string;
  /** 取消按钮文案。 */
  cancelText: string;
  /** 当前状态；确认后才由父级更新。 */
  checked: boolean;
  /** 开启状态文案。 */
  checkedChildren?: string;
  /** 确认说明。 */
  confirmTitle: string;
  /** 是否禁止切换。 */
  disabled?: boolean;
  /** 是否正在提交。 */
  loading?: boolean;
  /** 确认按钮文案。 */
  okText: string;
  /** 关闭状态文案。 */
  unCheckedChildren?: string;
}>();

const emit = defineEmits<{
  /** 用户确认切换后的目标状态。 */
  confirm: [checked: boolean];
}>();

/** 使用键盘请求确认切换，状态仍由调用方保存。 */
function activateByKeyboard(event: KeyboardEvent): void {
  if (props.disabled || props.loading) return;
  event.preventDefault();
  (event.currentTarget as HTMLElement).click();
}
</script>

<template>
  <a-popconfirm
    :cancsj-text="cancelText"
    :disabled="disabled || loading"
    :ok-button-props="{ danger: checked }"
    :ok-text="okText"
    :title="confirmTitle"
    @confirm="emit('confirm', !checked)"
  >
    <span
      class="sj-confirm-switch__trigger"
      role="switch"
      :aria-checked="checked"
      :aria-disabled="disabled || loading || undefined"
      :aria-label="ariaLabel"
      :tabindex="disabled || loading ? -1 : 0"
      @keydown.enter="activateByKeyboard"
      @keydown.space="activateByKeyboard"
    >
      <a-switch
        aria-hidden="true"
        :checked="checked"
        :checked-children="checkedChildren"
        class="pointer-events-none"
        :disabled="disabled || loading"
        :loading="loading"
        tabindex="-1"
        :un-checked-children="unCheckedChildren"
      />
    </span>
  </a-popconfirm>
</template>

<style scoped>
.sj-confirm-switch__trigger {
  display: inline-flex;
  cursor: pointer;
}

.sj-confirm-switch__trigger[aria-disabled='true'] {
  cursor: not-allowed;
}

.sj-confirm-switch__trigger:focus-visible {
  outline: 2px solid hsl(var(--ring));
  outline-offset: 2px;
  border-radius: 999px;
}
</style>
