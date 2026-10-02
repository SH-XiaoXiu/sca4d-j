<script setup lang="ts">
import type { SjRowAction } from './row-actions';

import { computed, ref } from 'vue';

import { message, Modal } from 'ant-design-vue';
import { Ellipsis } from 'lucide-vue-next';

import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
/** 行操作集中维护可访问名称、更多菜单、确认和单个动作的重复提交保护。 */
const props = defineProps<{
  /** 按业务优先级排列的动作。 */ actions: readonly SjRowAction[];
}>();
const pending = ref(new Set<string>());
const { errorText } = useSjPresentation();
const visible = computed(() =>
  props.actions.filter((action) => action.visible !== false),
);
const direct = computed(() =>
  visible.value.slice(0, visible.value.length > 3 ? 2 : 3),
);
const overflow = computed(() =>
  visible.value.length > 3 ? visible.value.slice(2) : [],
);
/** 等待命令完成，失败显示可恢复提示，不释放其他操作的状态。 */
async function perform(action: SjRowAction) {
  if (action.disabled || action.loading || pending.value.has(action.key))
    return;
  pending.value.add(action.key);
  try {
    await action.run();
  } catch (error) {
    message.error(errorText(error));
    throw error;
  } finally {
    pending.value.delete(action.key);
  }
}
/** 危险操作先确认对象；普通动作直接执行。 */
function invoke(action: SjRowAction) {
  if (action.disabled || action.loading || pending.value.has(action.key))
    return;
  if (action.confirm)
    Modal.confirm({
      title: action.confirm.title,
      content: action.confirm.content,
      okText: action.confirm.okText,
      cancelText: $t('sj.cancel'),
      okButtonProps: { danger: action.danger },
      onOk: () => perform(action),
    });
  else
    void perform(action).catch(() => {
      /* 当前组件已经展示失败。 */
    });
}
</script>
<template>
  <div class="inline-flex items-center justify-center gap-1">
    <a-tooltip
      v-for="action in direct"
      :key="action.key"
      :title="action.disabledReason || action.label"
    >
      <span>
        <a-button
          type="text"
          :aria-label="action.label"
          :danger="action.danger"
          :disabled="action.disabled"
          :loading="action.loading || pending.has(action.key)"
          @click="invoke(action)"
        >
          <component :is="action.icon" :size="17" aria-hidden="true" />
        </a-button>
      </span>
    </a-tooltip>
    <a-dropdown v-if="overflow.length > 0" :trigger="['click']">
      <a-button type="text" :aria-label="$t('sj.actions')">
        <Ellipsis :size="17" aria-hidden="true" />
      </a-button>
      <template #overlay>
        <a-menu>
          <a-menu-item
            v-for="action in overflow"
            :key="action.key"
            :danger="action.danger"
            :disabled="
              action.disabled || action.loading || pending.has(action.key)
            "
            @click="invoke(action)"
          >
            <span class="inline-flex items-center gap-2">
              <component :is="action.icon" :size="16" aria-hidden="true" />
              {{ action.label }}
            </span>
          </a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>
  </div>
</template>
