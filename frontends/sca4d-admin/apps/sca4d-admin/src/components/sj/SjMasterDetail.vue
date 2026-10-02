<script setup lang="ts">
import { useSlots } from 'vue';

import { ColPage, Page } from '@vben/common-ui';

import { useMediaQuery } from '@vueuse/core';

defineOptions({ name: 'SjMasterDetail' });

withDefaults(
  defineProps<{
    /** 左栏默认宽度百分比。 */
    leftWidth?: number;
    /** 页面标题。 */
    title: string;
  }>(),
  { leftWidth: 22 },
);

const slots = useSlots();
const desktop = useMediaQuery('(min-width: 1024px)');
</script>

<template>
  <ColPage
    v-if="desktop"
    auto-content-height
    class="sj-master-detail"
    :left-max-width="32"
    :left-min-width="18"
    :left-width="leftWidth"
    :right-min-width="60"
    :right-width="100 - leftWidth"
    split-handle
    split-line
  >
    <template #title>
      <h1 class="m-0 text-lg font-semibold leading-7">{{ title }}</h1>
    </template>
    <template #left>
      <aside class="sj-master-detail__sidebar">
        <slot name="left"></slot>
      </aside>
    </template>
    <main class="sj-master-detail__content">
      <slot></slot>
    </main>
  </ColPage>

  <Page v-else auto-content-height>
    <template #title>
      <h1 class="m-0 text-lg font-semibold leading-7">{{ title }}</h1>
    </template>
    <div class="sj-master-detail__mobile">
      <div v-if="slots.mobile" class="sj-master-detail__mobile-selector">
        <slot name="mobile"></slot>
      </div>
      <main class="sj-master-detail__content">
        <slot></slot>
      </main>
    </div>
  </Page>
</template>

<style scoped>
.sj-master-detail__sidebar {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid hsl(var(--border));
  border-radius: 6px 0 0 6px;
}

.sj-master-detail__content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  min-height: 0;
  padding: 0 16px 16px;
  overflow: hidden;
  border: 1px solid hsl(var(--border));
  border-left: 0;
  border-radius: 0 6px 6px 0;
}

.sj-master-detail__mobile {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.sj-master-detail__mobile-selector {
  flex: none;
}

@media (max-width: 1023px) {
  .sj-master-detail__content {
    height: auto;
    padding: 12px;
    overflow: visible;
    border: 1px solid hsl(var(--border));
    border-radius: 6px;
  }
}
</style>
