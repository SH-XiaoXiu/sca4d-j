<script setup lang="ts">
defineOptions({ name: "SortableOrderList" });

import { GripVertical } from "lucide-vue-next";
import Sortable from "sortablejs";
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";

/** 排序列表中由调用页面持有的一项；组件只调整数组顺序，不修改项目内容。 */
export interface SortableOrderItem {
  /** 保存排序时提交给后端的项目 ID。 */
  id: string;
  /** 排序列表中展示并用于读屏播报的业务名称。 */
  label: string;
  /** 帮助用户区分同名项目的可选摘要。 */
  description?: string | undefined;
}

const props = withDefaults(defineProps<{
  modelValue: SortableOrderItem[];
  dragLabel: string;
  disabled?: boolean;
}>(), {
  disabled: false
});
const emit = defineEmits<{ "update:modelValue": [value: SortableOrderItem[]] }>();
const list = ref<HTMLElement | null>(null);
const grabbedIndex = ref<number | null>(null);
const announcement = ref("");
let originalOrder: SortableOrderItem[] = [];
let sortable: Sortable | null = null;

function initialize(): void {
  sortable?.destroy();
  if (!list.value) return;
  sortable = Sortable.create(list.value, {
    animation: typeof window.matchMedia === "function"
      && window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 180,
    handle: ".sortable-order-list__handle",
    ghostClass: "sortable-order-list__item--ghost",
    chosenClass: "sortable-order-list__item--chosen",
    disabled: props.disabled,
    scroll: true,
    scrollSensitivity: 64,
    scrollSpeed: 12,
    delay: 120,
    delayOnTouchOnly: true,
    onEnd(event) {
      if (event.oldIndex == null || event.newIndex == null || event.oldIndex === event.newIndex) return;
      const ordered = [...props.modelValue];
      const [moved] = ordered.splice(event.oldIndex, 1);
      if (!moved) return;
      ordered.splice(event.newIndex, 0, moved);
      emit("update:modelValue", ordered);
    }
  });
}

function focusHandle(itemId: string): void {
  void nextTick(() => {
    const handles = list.value?.querySelectorAll<HTMLElement>("[data-order-id]") ?? [];
    [...handles].find((handle) => handle.dataset.orderId === itemId)?.focus();
  });
}

function grab(index: number): void {
  if (props.disabled || grabbedIndex.value !== null) return;
  grabbedIndex.value = index;
  originalOrder = [...props.modelValue];
  announcement.value = `${props.modelValue[index]?.label ?? ""} ${props.dragLabel}`;
}

function moveGrabbed(direction: -1 | 1): void {
  if (grabbedIndex.value === null) return;
  const target = grabbedIndex.value + direction;
  if (target < 0 || target >= props.modelValue.length) return;
  const ordered = [...props.modelValue];
  const item = ordered[grabbedIndex.value]!;
  [ordered[grabbedIndex.value], ordered[target]] = [ordered[target]!, item];
  grabbedIndex.value = target;
  emit("update:modelValue", ordered);
  announcement.value = `${item.label}: ${target + 1} / ${ordered.length}`;
  focusHandle(item.id);
}

function drop(): void {
  if (grabbedIndex.value === null) return;
  const item = props.modelValue[grabbedIndex.value];
  grabbedIndex.value = null;
  originalOrder = [];
  announcement.value = item ? `${item.label}: ${props.dragLabel}` : props.dragLabel;
}

function cancel(): void {
  if (grabbedIndex.value === null) return;
  const item = props.modelValue[grabbedIndex.value];
  grabbedIndex.value = null;
  emit("update:modelValue", originalOrder);
  originalOrder = [];
  announcement.value = item?.label ?? "";
  if (item) focusHandle(item.id);
}

onMounted(initialize);
watch(() => props.modelValue.length, () => void nextTick(initialize));
watch(() => props.disabled, (disabled) => sortable?.option("disabled", disabled));
onBeforeUnmount(() => sortable?.destroy());
</script>

<template>
  <div
    ref="list"
    class="sortable-order-list"
  >
    <div
      v-for="(item, index) in modelValue"
      :key="item.id"
      class="sortable-order-list__item"
    >
      <a-tooltip :title="dragLabel">
        <button
          type="button"
          class="sortable-order-list__handle"
          :class="{ 'sortable-order-list__handle--grabbed': grabbedIndex === index }"
          :data-order-id="item.id"
          :disabled="disabled"
          :aria-label="`${dragLabel}: ${item.label}`"
          :aria-pressed="grabbedIndex === index"
          @keydown.space.prevent="grab(index)"
          @keydown.up.prevent="moveGrabbed(-1)"
          @keydown.down.prevent="moveGrabbed(1)"
          @keydown.enter.prevent="drop"
          @keydown.escape.prevent="cancel"
        >
          <GripVertical :size="18" />
        </button>
      </a-tooltip>
      <div class="sortable-order-list__content">
        <strong>{{ item.label }}</strong>
        <span v-if="item.description">{{ item.description }}</span>
      </div>
    </div>
    <div
      class="sortable-order-list__status"
      aria-live="polite"
      aria-atomic="true"
    >
      {{ announcement }}
    </div>
  </div>
</template>

<style scoped>
.sortable-order-list {
  display: grid;
  max-height: min(62vh, 560px);
  overflow-y: auto;
  gap: 6px;
}

.sortable-order-list__item {
  display: flex;
  min-height: 48px;
  align-items: center;
  padding: 6px 10px 6px 4px;
  gap: 8px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
}

.sortable-order-list__handle {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  place-items: center;
  padding: 0;
  color: var(--app-text-tertiary);
  background: transparent;
  border: 0;
  border-radius: var(--app-radius);
  cursor: grab;
}

.sortable-order-list__handle:active { cursor: grabbing; }
.sortable-order-list__handle:focus-visible { outline: 2px solid var(--app-primary); }
.sortable-order-list__handle--grabbed { color: var(--app-primary); background: var(--app-primary-soft); }
.sortable-order-list__content { display: grid; min-width: 0; gap: 2px; }
.sortable-order-list__content strong,
.sortable-order-list__content span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sortable-order-list__content span { color: var(--app-text-secondary); font-size: 12px; }
.sortable-order-list__item--ghost {
  opacity: 0.45;
  box-shadow: inset 0 2px 0 var(--app-primary);
}
.sortable-order-list__item--chosen { border-color: var(--app-primary); }
.sortable-order-list__status {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (prefers-reduced-motion: reduce) {
  .sortable-order-list__item { transition: none; }
}
</style>
