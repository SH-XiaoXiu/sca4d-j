<script setup lang="ts">
defineOptions({ name: "LocalizedTextEditor" });

import { Check, CircleAlert } from "lucide-vue-next";
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useLanguageStore } from "@/stores/languages";

const props = withDefaults(defineProps<{
  modelValue: Record<string, string>;
  disabled?: boolean;
  maxlength?: number;
}>(), {
  disabled: false,
  maxlength: 128
});
const emit = defineEmits<{ "update:modelValue": [value: Record<string, string>] }>();
const { locale, t } = useI18n();
const languageStore = useLanguageStore();
const selectedLocale = ref("");
const selectedLanguage = computed(() =>
  languageStore.languages.find((language) => language.code === selectedLocale.value) ?? null
);
const completed = computed(() => Boolean(props.modelValue[selectedLocale.value]?.trim()));
const text = computed({
  get: () => props.modelValue[selectedLocale.value] ?? "",
  set: (value: string) => {
    if (selectedLocale.value) {
      emit("update:modelValue", { ...props.modelValue, [selectedLocale.value]: value });
    }
  }
});

watch(
  () => languageStore.languages,
  (languages) => {
    if (languages.some((language) => language.code === selectedLocale.value)) return;
    selectedLocale.value = languages.find((language) => language.code === locale.value)?.code
      ?? languages.find((language) => language.defaultLanguage)?.code
      ?? languages[0]?.code
      ?? "";
  },
  { immediate: true }
);
onMounted(() => void languageStore.load());
</script>

<template>
  <div class="localized-text-editor">
    <div class="localized-text-editor__selector">
      <a-select
        v-model:value="selectedLocale"
        :disabled="disabled || languageStore.loading || !languageStore.languages.length"
        :loading="languageStore.loading"
        :aria-label="t('app.language')"
        class="localized-text-editor__language"
      >
        <a-select-option
          v-for="language in languageStore.languages"
          :key="language.code"
          :value="language.code"
        >
          <span class="localized-text-editor__option">
            <span>{{ language.nativeName }}<sup v-if="language.required">*</sup></span>
            <Check
              v-if="modelValue[language.code]?.trim()"
              :size="15"
              aria-hidden="true"
            />
          </span>
        </a-select-option>
      </a-select>
      <a-tag :color="completed ? 'success' : selectedLanguage?.required ? 'warning' : 'default'">
        {{ t(completed ? "common.completed" : selectedLanguage?.required ? "common.required" : "common.optional") }}
      </a-tag>
    </div>
    <a-input
      v-model:value="text"
      :disabled="disabled"
      :maxlength="maxlength"
      show-count
    />
    <a-alert
      v-if="languageStore.loadError"
      type="error"
      :message="languageStore.loadError"
      class="localized-text-editor__error"
    >
      <template #icon>
        <CircleAlert :size="16" />
      </template>
    </a-alert>
  </div>
</template>

<style scoped>
.localized-text-editor {
  display: grid;
  grid-template-columns: minmax(180px, 220px) minmax(0, 1fr);
  gap: 8px;
}
.localized-text-editor__selector { display: flex; min-width: 0; align-items: center; gap: 6px; }
.localized-text-editor__language { min-width: 0; flex: 1; }
.localized-text-editor__option { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.localized-text-editor__option svg { color: var(--app-success); }
.localized-text-editor__option sup { color: var(--app-error); }
.localized-text-editor__error { grid-column: 1 / -1; }

@media (max-width: 480px) {
  .localized-text-editor {
    grid-template-columns: 1fr;
  }
}
</style>
