<script setup lang="ts">
import { ref, watch } from 'vue';

import { $t } from '#/locales';

import SjTabs from './SjTabs.vue';
/** 编辑语言的集合由服务端目录或资源契约提供。 */
const props = defineProps<{
  /** 必须保留的默认语言。 */
  defaultLocale?: string;
  /** 可编辑语言。 */
  locales: readonly string[];
}>();
const activeLocale = ref(props.defaultLocale ?? props.locales[0]);
watch(
  () => props.locales,
  (locales) => {
    if (!locales.includes(activeLocale.value ?? ''))
      activeLocale.value = props.defaultLocale ?? locales[0];
  },
);
</script>
<template>
  <SjTabs v-model:active-key="activeLocale" variant="section">
    <a-tab-pane
      v-for="locale in locales"
      :key="locale"
      :tab="
        locale === defaultLocale
          ? `${locale} · ${$t('sj.defaultLocale')}`
          : locale
      "
    >
      <slot :locale="locale"></slot>
    </a-tab-pane>
  </SjTabs>
</template>
