<script setup lang="ts">
defineOptions({ name: "IconPicker" });

import { CircleHelp, Search } from "lucide-vue-next";
import { computed, ref, shallowRef, watch, type Component } from "vue";
import { useI18n } from "vue-i18n";
import { loadMenuIconCatalog } from "@/components/navigation/menu-icon-catalog";

const props = withDefaults(defineProps<{ modelValue: string; disabled?: boolean }>(), { disabled: false });
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const { t } = useI18n();
const open = ref(false);
const search = ref("");
const loading = ref(false);
const renderCount = ref(180);
const catalog = shallowRef<Array<[string, Component]>>([]);
const iconMap = shallowRef<Record<string, Component>>({});
const filteredIcons = computed(() => {
  const keyword = search.value.trim().toLowerCase();
  return keyword
    ? catalog.value.filter(([name]) => name.toLowerCase().includes(keyword))
    : catalog.value;
});
const visibleIcons = computed(() => filteredIcons.value.slice(0, renderCount.value));
const selectedIcon = computed(() => iconMap.value[props.modelValue] ?? CircleHelp);

async function loadCatalog(): Promise<void> {
  if (catalog.value.length || loading.value) return;
  loading.value = true;
  try {
    const icons = await loadMenuIconCatalog();
    iconMap.value = icons;
    catalog.value = Object.entries(icons)
      .filter(([name]) => /^[A-Z][A-Za-z0-9]*$/.test(name))
      .sort(([left], [right]) => left.localeCompare(right)) as Array<[string, Component]>;
  } finally {
    loading.value = false;
  }
}

function loadNextBatch(event: Event): void {
  const target = event.currentTarget as HTMLElement;
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 80) {
    renderCount.value = Math.min(renderCount.value + 180, filteredIcons.value.length);
  }
}

function select(name: string): void {
  emit("update:modelValue", name);
  open.value = false;
  search.value = "";
}

watch(search, () => { renderCount.value = 180; });
watch(open, (value) => {
  if (value) void loadCatalog();
  else search.value = "";
});
</script>

<template>
  <a-popover
    v-model:open="open"
    trigger="click"
    placement="bottomLeft"
    overlay-class-name="icon-picker-overlay"
  >
    <a-tooltip :title="t('menu.chooseIcon')">
      <a-button
        class="icon-picker__trigger"
        :disabled="disabled"
        :aria-label="t('menu.chooseIcon')"
      >
        <component
          :is="selectedIcon"
          :size="20"
        />
      </a-button>
    </a-tooltip>
    <template #content>
      <div class="icon-picker__panel">
        <a-input
          v-model:value="search"
          allow-clear
          :placeholder="t('menu.searchIcon')"
        >
          <template #prefix>
            <Search :size="16" />
          </template>
        </a-input>
        <a-spin
          v-if="loading"
          class="icon-picker__loading"
        />
        <div
          v-else-if="visibleIcons.length"
          class="icon-picker__grid"
          role="listbox"
          :aria-label="t('menu.icon')"
          @scroll="loadNextBatch"
        >
          <a-tooltip
            v-for="[name, icon] in visibleIcons"
            :key="name"
            :title="name"
          >
            <button
              type="button"
              class="icon-picker__option"
              :class="{ 'icon-picker__option--selected': name === modelValue }"
              role="option"
              :aria-label="name"
              :aria-selected="name === modelValue"
              @click="select(name)"
            >
              <component
                :is="icon"
                :size="19"
              />
            </button>
          </a-tooltip>
        </div>
        <a-empty
          v-else
          :description="t('menu.iconEmpty')"
        />
      </div>
    </template>
  </a-popover>
</template>

<style scoped>
.icon-picker__trigger {
  width: 40px;
  height: 40px;
  padding: 0;
}

.icon-picker__panel {
  display: grid;
  width: min(420px, calc(100vw - 40px));
  gap: 12px;
}

.icon-picker__grid {
  display: grid;
  max-height: min(52vh, 420px);
  padding: 2px;
  overflow-y: auto;
  grid-template-columns: repeat(auto-fill, minmax(40px, 1fr));
  gap: 4px;
}

.icon-picker__loading {
  min-height: 160px;
  align-content: center;
}

.icon-picker__option {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  padding: 0;
  color: var(--app-text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--app-radius);
  cursor: pointer;
}

.icon-picker__option:hover {
  color: var(--app-primary);
  background: var(--app-primary-soft);
}

.icon-picker__option--selected {
  color: var(--app-primary);
  border-color: var(--app-primary);
}

.icon-picker__option:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 1px;
}
</style>
