<script setup lang="ts">
import { onMounted } from 'vue';

import { RefreshCw } from 'lucide-vue-next';

import { SjPage, SjResourceState } from '#/components/sj';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const { t } = useSjPresentation();

const permissions = useSjControllerState(businessApplication.permissions);

onMounted(() => void load());

/** 读取按当前语言解析的只读权限目录。 */
async function load(): Promise<void> {
  try {
    await businessApplication.permissions.load();
  } catch {
    // Controller 已发布统一错误状态。
  }
}
</script>

<template>
  <SjPage auto-content-height>
    <a-card :title="t('permissions')" :bordered="false" class="h-full">
      <template #extra>
        <a-tooltip :title="t('refresh')">
          <a-button type="text" :aria-label="t('refresh')" @click="load">
            <RefreshCw :size="17" aria-hidden="true" />
          </a-button>
        </a-tooltip>
      </template>
      <SjResourceState
        :has-data="Boolean(permissions.catalog)"
        :phase="permissions.phase"
        :error="permissions.error"
        @retry="load"
      >
        <a-tree
          class="w-full bg-transparent"
          :tree-data="permissions.tree"
          default-expand-all
          block-node
        >
          <template #title="node">
            <span
              class="flex min-w-0 items-center justify-between gap-5 max-sm:flex-col max-sm:items-start max-sm:gap-0.5"
            >
              <strong class="truncate">{{ node.title }}</strong>
              <code class="text-muted-foreground text-xs max-sm:break-all">
                {{ node.code }}
              </code>
            </span>
          </template>
        </a-tree>
      </SjResourceState>
    </a-card>
  </SjPage>
</template>
