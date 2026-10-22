<script setup lang="ts">
/** 资源名称、稳定标识和状态摘要。 */
defineProps<{
  /** 可复制的稳定资源标识。 */
  identifier?: string;
  /** 资源名称。 */
  title: string;
}>();
</script>
<template>
  <header class="flex min-w-0 flex-col gap-2">
    <h2 class="m-0 break-words text-base font-semibold">{{ title }}</h2>
    <a-typography-text
      v-if="identifier"
      copyable
      class="text-xs text-muted-foreground"
    >
      {{ identifier }}
    </a-typography-text>
    <div class="flex flex-wrap gap-2"><slot name="status"></slot></div>
    <slot name="meta"></slot>
  </header>
</template>
