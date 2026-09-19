<script setup lang="ts">
import { onMounted } from 'vue';

import { Page } from '@vben/common-ui';

import { RefreshCw } from 'lucide-vue-next';

import ResourceState from '../components/ResourceState.vue';
import { useI18n } from '../i18n';
import { useAdminStore } from '../store/business';

const store = useAdminStore();
const { t } = useI18n();

onMounted(() => void load());

/** 读取按当前语言解析的只读权限目录。 */
async function load(): Promise<void> {
  try {
    await store.loadPermissions();
  } catch {
    // Controller 已发布统一错误状态。
  }
}
</script>

<template>
  <Page auto-content-height>
    <a-card :title="t('permissions')" :bordered="false" class="h-full">
      <template #extra>
        <a-tooltip :title="t('refresh')">
          <a-button type="text" :aria-label="t('refresh')" @click="load">
            <RefreshCw :size="17" aria-hidden="true" />
          </a-button>
        </a-tooltip>
      </template>
      <ResourceState
        :phase="store.permissions.phase"
        :error="store.permissions.error"
        @retry="load"
      >
        <a-tree
          class="w-full bg-transparent"
          :tree-data="store.permissions.tree"
          default-expand-all
          block-node
        >
          <template #title="node">
            <span
              class="flex min-w-0 items-center justify-between gap-5 max-sm:flex-col max-sm:items-start max-sm:gap-0.5"
            >
              <strong class="truncate">{{ node.title }}</strong>
              <code class="text-muted-foreground text-xs max-sm:break-all">{{
                node.code
              }}</code>
            </span>
          </template>
        </a-tree>
      </ResourceState>
    </a-card>
  </Page>
</template>
