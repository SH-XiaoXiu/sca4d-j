<script setup lang="ts">
/** 同一页面的独立工作区或详情章节；访问后的内容由 Ant Tabs 保留。 */
withDefaults(
  defineProps<{
    /** 页签所处的布局层级。 */
    variant?: 'page' | 'section';
  }>(),
  { variant: 'page' },
);
</script>
<template>
  <a-tabs
    :animated="false"
    :destroy-inactive-tab-pane="false"
    class="min-h-0"
    :class="variant === 'page' ? 'sj-tabs-page' : 'sj-tabs-section'"
  >
    <slot></slot>
  </a-tabs>
</template>
<style scoped>
.sj-tabs-page {
  padding: 0 16px;
}
.sj-tabs-section {
  min-width: 0;
}
</style>
