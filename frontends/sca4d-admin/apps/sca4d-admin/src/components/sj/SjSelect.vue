<script
  setup
  lang="ts"
  generic="Value extends string | number | null | string[] | number[]"
>
import { computed, useSlots } from 'vue';

import { Empty } from 'ant-design-vue';
import { Plus } from 'lucide-vue-next';

defineOptions({
  name: 'SjSelect',
  inheritAttrs: false,
});

withDefaults(
  defineProps<{
    /** 无候选项时显示的创建命令；未提供时只显示空状态。 */
    createText?: string;
    /** 无候选项时说明缺少哪类资源。 */
    emptyText: string;
  }>(),
  { createText: undefined },
);

const emit = defineEmits<{
  /** 用户从空状态进入对应资源创建流程。 */
  create: [];
}>();

const value = defineModel<Value>('value');
const slots = useSlots();
const delegatedSlots = computed(() =>
  Object.keys(slots).filter((name) => name !== 'notFoundContent'),
);
</script>

<template>
  <a-select v-bind="$attrs" v-model:value="value">
    <template
      v-for="slotName in delegatedSlots"
      :key="slotName"
      #[slotName]="slotProps"
    >
      <slot :name="slotName" v-bind="slotProps"></slot>
    </template>
    <template #notFoundContent>
      <div class="flex min-h-28 flex-col items-center justify-center py-3">
        <a-empty
          class="m-0"
          :description="emptyText"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
          :image-style="{ height: '36px' }"
        />
        <a-button
          v-if="createText"
          type="link"
          class="mt-1 h-9 px-3"
          @mousedown.prevent
          @click.stop="emit('create')"
        >
          <Plus :size="16" aria-hidden="true" />
          {{ createText }}
        </a-button>
      </div>
    </template>
  </a-select>
</template>
