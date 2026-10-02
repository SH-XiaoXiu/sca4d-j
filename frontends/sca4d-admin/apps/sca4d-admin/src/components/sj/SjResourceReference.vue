<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';

import { computed } from 'vue';

import { ExternalLink } from 'lucide-vue-next';

interface SjResourceReferenceProps {
  /** 可复制的稳定资源标识。 */
  id?: null | string;
  /** 没有名称或类型时显示的占位文本。 */
  emptyText?: string;
  /** 已解析的资源展示名称。 */
  name?: null | string;
  /** 可定位资源的内部路由。 */
  to?: RouteLocationRaw;
  /** 资源类型的本地化文本。 */
  typeLabel?: null | string;
  /** 有权限时可直接打开的外部短期地址。 */
  externalUrl?: null | string;
  /** 是否为 ID 显示复制按钮。 */
  copyable?: boolean;
}

const props = withDefaults(defineProps<SjResourceReferenceProps>(), {
  copyable: true,
  emptyText: '-',
  externalUrl: null,
  id: null,
  name: null,
  to: undefined,
  typeLabel: null,
});

const primaryText = computed(
  () =>
    props.name?.trim() ||
    props.typeLabel?.trim() ||
    props.id?.trim() ||
    props.emptyText,
);
const identifier = computed(() => props.id?.trim() || null);
const showIdentifier = computed(
  () => identifier.value !== null && identifier.value !== primaryText.value,
);
</script>

<template>
  <div
    v-if="identifier || name || typeLabel"
    class="flex min-w-0 flex-col gap-0.5 text-left"
  >
    <RouterLink
      v-if="to"
      class="inline-flex min-w-0 items-center gap-1 truncate"
      :to="to"
      :title="primaryText"
    >
      <span class="truncate">{{ primaryText }}</span>
    </RouterLink>
    <a
      v-else-if="externalUrl"
      class="inline-flex min-w-0 items-center gap-1 truncate"
      :href="externalUrl"
      rel="noopener noreferrer"
      target="_blank"
      :title="primaryText"
    >
      <span class="truncate">{{ primaryText }}</span>
      <ExternalLink class="shrink-0" :size="13" aria-hidden="true" />
    </a>
    <a-typography-text
      v-else-if="identifier === primaryText && copyable"
      class="truncate"
      copyable
      :title="identifier"
    >
      <code>{{ identifier }}</code>
    </a-typography-text>
    <strong v-else class="truncate" :title="primaryText">
      {{ primaryText }}
    </strong>

    <a-typography-text
      v-if="showIdentifier && copyable"
      class="text-muted-foreground truncate text-xs"
      copyable
      :title="identifier"
    >
      <code>{{ identifier }}</code>
    </a-typography-text>
    <code
      v-else-if="showIdentifier"
      class="text-muted-foreground truncate text-xs"
    >
      {{ identifier }}
    </code>
  </div>
  <span v-else>{{ emptyText }}</span>
</template>
