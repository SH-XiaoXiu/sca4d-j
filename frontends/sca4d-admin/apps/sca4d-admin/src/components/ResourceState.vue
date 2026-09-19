<script setup lang="ts">
import type { AdminError, ResourcePhase } from '@sca4d/admin';

import { RefreshCw } from 'lucide-vue-next';

import { useI18n } from '../i18n';

/** 资源状态组件接收的加载阶段和统一错误。 */
const props = defineProps<{
  empty?: boolean;
  error: AdminError | null;
  phase: ResourcePhase;
}>();

/** 请求重新加载当前资源。 */
const emit = defineEmits<{ retry: [] }>();
const { t, errorText } = useI18n();
</script>

<template>
  <div
    v-if="props.phase === 'loading' || props.phase === 'idle'"
    class="grid min-h-60 place-items-center"
    role="status"
  >
    <a-spin />
  </div>
  <a-result
    v-else-if="props.phase === 'forbidden'"
    status="403"
    :title="t('noPermission')"
    :sub-title="t('noPermissionDetail')"
  />
  <a-result
    v-else-if="props.phase === 'conflict'"
    status="warning"
    :title="t('versionConflict')"
    :sub-title="t('versionConflictDetail')"
  >
    <template #extra>
      <a-button @click="emit('retry')">
        <RefreshCw :size="16" aria-hidden="true" />{{ t('retry') }}
      </a-button>
    </template>
  </a-result>
  <a-result
    v-else-if="props.phase === 'error'"
    status="error"
    :title="t('loadFailed')"
    :sub-title="errorText(props.error)"
  >
    <template #extra>
      <a-button @click="emit('retry')">
        <RefreshCw :size="16" aria-hidden="true" />{{ t('retry') }}
      </a-button>
    </template>
  </a-result>
  <a-empty
    v-else-if="props.phase === 'empty' || props.empty"
    :description="t('noData')"
  />
  <slot v-else></slot>
</template>
