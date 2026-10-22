<script setup lang="ts">
import { computed, useId, useSlots } from 'vue';

defineOptions({ name: 'SjSection' });

withDefaults(
  defineProps<{
    /** 章节内容附加类名。 */
    contentClass?: string;
    /** 章节说明。 */
    description?: string;
    /** 是否隐藏与上一个章节之间的分隔线。 */
    divided?: boolean;
    /** 章节标题。 */
    title?: string;
  }>(),
  {
    contentClass: undefined,
    description: undefined,
    divided: true,
    title: undefined,
  },
);

const slots = useSlots();
const generatedId = useId();
const titleId = computed(() => `sj-section-${generatedId}`);
</script>

<template>
  <section
    :aria-labelledby="title || slots.title ? titleId : undefined"
    class="min-w-0"
    :class="[{ 'border-t border-border pt-6': divided }]"
  >
    <header
      v-if="
        title ||
        description ||
        slots.title ||
        slots.description ||
        slots.actions
      "
      class="mb-4 flex min-w-0 items-start justify-between gap-4 max-sm:flex-col"
    >
      <div class="min-w-0">
        <slot name="title">
          <h2 v-if="title" :id="titleId" class="m-0 text-base font-semibold">
            {{ title }}
          </h2>
        </slot>
        <slot name="description">
          <p v-if="description" class="mt-1 mb-0 text-sm text-muted-foreground">
            {{ description }}
          </p>
        </slot>
      </div>
      <div v-if="slots.actions" class="flex shrink-0 flex-wrap gap-2">
        <slot name="actions"></slot>
      </div>
    </header>
    <div class="min-w-0" :class="[contentClass]">
      <slot></slot>
    </div>
  </section>
</template>
