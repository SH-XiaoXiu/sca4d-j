<script setup lang="ts">
import { computed, useSlots } from 'vue';

import { Page } from '@vben/common-ui';

import { ArrowLeft } from 'lucide-vue-next';

defineOptions({ name: 'SjPage' });

const props = withDefaults(
  defineProps<{
    /** 是否按 Vben 可用内容高度固定页面主体。 */
    autoContentHeight?: boolean;
    /** 返回按钮的可访问名称；传入后显示返回入口。 */
    backLabel?: string;
    /** 页面主体附加类名。 */
    contentClass?: string;
    /** 页面说明。 */
    description?: string;
    /** 页面标题区附加类名。 */
    headerClass?: string;
    /** 列表页或详情页布局。 */
    mode?: 'detail' | 'list';
    /** 页面主标题。 */
    title?: string;
  }>(),
  {
    autoContentHeight: undefined,
    backLabel: undefined,
    contentClass: undefined,
    description: undefined,
    headerClass: undefined,
    mode: 'list',
    title: undefined,
  },
);

const emit = defineEmits<{
  /** 用户点击详情页返回入口。 */
  back: [];
}>();

const slots = useSlots();
const usesAutoContentHeight = computed(
  () => props.autoContentHeight ?? props.mode === 'list',
);
const pageContentClass = computed(() =>
  [
    props.mode === 'list'
      ? 'flex min-h-0 flex-col overflow-auto'
      : 'flex w-full flex-col gap-6',
    props.contentClass,
  ]
    .filter(Boolean)
    .join(' '),
);
const pageHeaderClass = computed(() =>
  ['gap-4 max-sm:flex-col max-sm:items-stretch', props.headerClass]
    .filter(Boolean)
    .join(' '),
);
</script>

<template>
  <Page
    :auto-content-height="usesAutoContentHeight"
    :content-class="pageContentClass"
    :header-class="pageHeaderClass"
  >
    <template v-if="title || slots.title" #title>
      <div class="flex min-w-0 items-start gap-2">
        <a-tooltip v-if="backLabel" :title="backLabel">
          <a-button
            type="text"
            class="mt-[-2px] shrink-0"
            :aria-label="backLabel"
            @click="emit('back')"
          >
            <ArrowLeft :size="18" aria-hidden="true" />
          </a-button>
        </a-tooltip>
        <div class="min-w-0">
          <slot name="title">
            <h1 class="m-0 text-lg font-semibold leading-7">{{ title }}</h1>
          </slot>
          <slot name="title-meta"></slot>
        </div>
      </div>
    </template>

    <template v-if="description || slots.description" #description>
      <slot name="description">
        <p class="m-0 text-sm text-muted-foreground">{{ description }}</p>
      </slot>
    </template>

    <template v-if="slots.extra" #extra>
      <div
        class="flex flex-wrap items-center justify-end gap-2 max-sm:justify-start"
      >
        <slot name="extra"></slot>
      </div>
    </template>

    <slot></slot>
  </Page>
</template>
