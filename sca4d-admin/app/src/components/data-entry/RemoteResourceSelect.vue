<script setup lang="ts">
import type { Id, PageResult } from "@sca4d/admin-common";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

/** 远程资源选择器展示的一条候选项或已选快照。 */
export interface RemoteResourceOption {
  id: Id;
  label: string;
  description?: string | null;
  disabled?: boolean;
}

const props = defineProps<{
  // 清空选择时组件向 v-model 写入 undefined；单选传一个 ID，多选传 ID 数组。
  modelValue?: Id | Id[] | undefined;
  multiple?: boolean;
  disabled?: boolean;
  // 组件开始新搜索或卸载时会中止 signal；加载函数应取消对应 HTTP 请求且不再返回旧列表。
  loadOptions: (
    search: string,
    page: number,
    size: number,
    signal: AbortSignal
  ) => Promise<PageResult<RemoteResourceOption>>;
  placeholder: string;
  emptyText: string;
  // 初始值不在当前搜索页时，页面传入已选项快照，选择器才能显示对应名称。
  selectedOption?: RemoteResourceOption | undefined;
  selectedOptions?: RemoteResourceOption[] | undefined;
}>();
const emit = defineEmits<{
  "update:modelValue": [value: Id | Id[] | undefined];
  // 当前请求未被取消且加载失败时触发；被新搜索替换的请求不会触发。
  loadError: [error: unknown];
}>();

const loading = ref(false);
const search = ref("");
const page = ref(1);
const total = ref(0);
const records = ref<RemoteResourceOption[]>([]);
let controller: AbortController | null = null;
let debounceTimer: ReturnType<typeof setTimeout> | null = null;

const options = computed(() => {
  const items = new Map<Id, RemoteResourceOption>();
  if (props.selectedOption) items.set(props.selectedOption.id, props.selectedOption);
  for (const option of props.selectedOptions ?? []) items.set(option.id, option);
  for (const record of records.value) items.set(record.id, record);
  return [...items.values()].map((option) => ({
    value: option.id,
    label: option.label,
    disabled: option.disabled,
    title: option.description
  }));
});

async function load(nextPage: number, append: boolean): Promise<void> {
  controller?.abort();
  const requestController = new AbortController();
  controller = requestController;
  loading.value = true;
  try {
    const result = await props.loadOptions(search.value, nextPage, 20, requestController.signal);
    if (requestController.signal.aborted || controller !== requestController) return;
    records.value = append ? [...records.value, ...result.records] : result.records;
    page.value = result.page;
    total.value = result.total;
  } catch (error) {
    if (!requestController.signal.aborted) emit("loadError", error);
  } finally {
    if (controller === requestController) loading.value = false;
  }
}

function handleSearch(value: string): void {
  search.value = value.trim();
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => void load(1, false), 250);
}

function handlePopupScroll(event: Event): void {
  const target = event.target as HTMLElement;
  const nearBottom = target.scrollHeight - target.scrollTop - target.clientHeight < 32;
  if (nearBottom && !loading.value && records.value.length < total.value) void load(page.value + 1, true);
}

function handleValueChange(value: Id | Id[] | undefined): void {
  emit("update:modelValue", value);
}

onMounted(() => void load(1, false));
onBeforeUnmount(() => {
  controller?.abort();
  if (debounceTimer) clearTimeout(debounceTimer);
});
</script>

<template>
  <a-select
    :value="modelValue"
    :mode="multiple ? 'multiple' : undefined"
    :options="options"
    :loading="loading"
    :disabled="disabled"
    :placeholder="placeholder"
    allow-clear
    show-search
    :filter-option="false"
    @search="handleSearch"
    @popup-scroll="handlePopupScroll"
    @update:value="handleValueChange"
  >
    <template #option="option">
      <span class="remote-resource-option">
        <strong>{{ option.label }}</strong>
        <small v-if="option.title">{{ option.title }}</small>
      </span>
    </template>
    <template #notFoundContent>
      <a-spin
        v-if="loading"
        size="small"
      />
      <span v-else>{{ emptyText }}</span>
    </template>
  </a-select>
</template>

<style scoped>
.remote-resource-option {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 2px 0;
  gap: 2px;
}

.remote-resource-option strong,
.remote-resource-option small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remote-resource-option strong {
  color: var(--app-text);
  font-weight: 500;
}

.remote-resource-option small {
  color: var(--app-text-tertiary);
}
</style>
