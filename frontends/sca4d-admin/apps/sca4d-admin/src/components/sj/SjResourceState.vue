<script setup lang="ts">
import type { AdminError, ResourcePhase } from '@sca4d/admin';

import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
/** 首次读取使用完整反馈；刷新期间保留仍可使用的数据。 */
withDefaults(
  defineProps<{
    /** 当前内容是否为空。 */
    empty?: boolean;
    /** 可安全展示的请求错误。 */
    error: AdminError | null;
    /** 是否已有仍可使用的内容。 */
    hasData?: boolean;
    /** 当前请求阶段。 */
    phase: ResourcePhase;
  }>(),
  { hasData: false, empty: false },
);
const emit = defineEmits<{ /** 请求调用方重新读取当前资源。 */ retry: [] }>();
const { errorText } = useSjPresentation();
</script>
<template>
  <div
    v-if="(phase === 'idle' || phase === 'loading') && !hasData"
    class="grid min-h-48 place-items-center"
    role="status"
  >
    <a-spin />
  </div>
  <a-result
    v-else-if="phase === 'forbidden'"
    status="403"
    :title="$t('sj.noPermission')"
    :sub-title="$t('sj.noPermissionDetail')"
  />
  <a-result
    v-else-if="(phase === 'error' || phase === 'conflict') && !hasData"
    :status="phase === 'conflict' ? 'warning' : 'error'"
    :title="$t('sj.loadFailed')"
    :sub-title="errorText(error)"
  >
    <template #extra>
      <a-button @click="emit('retry')">
        {{ $t('sj.retry') }}
      </a-button>
    </template>
  </a-result>
  <template v-else>
    <a-alert
      v-if="error"
      class="mb-4"
      type="warning"
      show-icon
      :message="errorText(error)"
    >
      <template #action>
        <a-button size="small" @click="emit('retry')">
          {{ $t('sj.retry') }}
        </a-button>
      </template>
    </a-alert>
    <a-spin :spinning="phase === 'loading'">
      <a-empty
        v-if="(empty || phase === 'empty') && !hasData"
        :description="$t('sj.noData')"
      />
      <slot v-else></slot>
    </a-spin>
  </template>
</template>
