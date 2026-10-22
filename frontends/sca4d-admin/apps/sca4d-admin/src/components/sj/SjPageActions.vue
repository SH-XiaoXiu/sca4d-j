<script setup lang="ts">
import { useSlots } from 'vue';

defineOptions({ name: 'SjPageActions', inheritAttrs: false });

defineProps<{
  /** 页面对象命令栏的可访问名称。 */
  label: string;
}>();

const slots = useSlots();
</script>

<template>
  <div
    v-bind="$attrs"
    role="toolbar"
    :aria-label="label"
    class="inline-flex max-w-full items-center gap-2"
  >
    <div v-if="slots.primary" class="flex min-w-0 items-center gap-2">
      <slot name="primary"></slot>
    </div>
    <div v-if="slots.secondary" class="flex min-w-0 items-center gap-2">
      <slot name="secondary"></slot>
    </div>
    <div v-if="slots.more" class="flex shrink-0 items-center">
      <slot name="more"></slot>
    </div>
  </div>
</template>
