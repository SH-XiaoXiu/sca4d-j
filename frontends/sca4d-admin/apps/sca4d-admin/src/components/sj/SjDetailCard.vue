<script setup lang="ts">
/** 独立对象摘要使用的有边界分组；普通章节使用 SjSection。 */
defineProps<{
  /** 摘要说明。 */
  description?: string;
  /** 摘要标题。 */
  title: string;
}>();
</script>
<template>
  <section class="min-w-0 rounded-md border border-border bg-card p-4">
    <header class="mb-4 flex items-start justify-between gap-4">
      <div>
        <h2 class="m-0 text-sm font-semibold">{{ title }}</h2>
        <p v-if="description" class="mt-1 text-sm text-muted-foreground">
          {{ description }}
        </p>
      </div>
      <slot name="actions"></slot>
    </header>
    <slot></slot>
  </section>
</template>
