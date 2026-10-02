<script setup lang="ts">
import type {
  LocalizedFields,
  ManagedDictionary,
  ManagedDictionaryOption,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { computed, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { SjResourceEditor } from '#/components/sj';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const props = defineProps<{
  /** 选项所属字典。 */ dictionary: ManagedDictionary | null;
}>();

const { t, errorText } = useSjPresentation();

const systemDictionaries = useSjControllerState(
  businessApplication.systemDictionaries,
);
const optionModalOpen = ref(false);
const optionSaving = ref(false);
const optionFormRef = ref<FormInstance>();
const editingOption = ref<ManagedDictionaryOption | null>(null);
const optionForm = reactive({
  enabled: true,
  label: '',
  labels: {} as Record<string, string>,
  sortOrder: 0,
  value: '',
});
const supportedLocales = computed(
  () => systemDictionaries.value.localeCatalog?.supportedLocales ?? [],
);
const optionDictionary = computed(() => props.dictionary);

/** 打开新建或编辑数据库字典项窗口。 */
function openOptionModal(option: ManagedDictionaryOption | null = null): void {
  editingOption.value = option;
  optionForm.value = option?.value ?? '';
  optionForm.label = option?.fallbackLabel ?? '';
  optionForm.labels = Object.fromEntries(
    supportedLocales.value.map((locale) => [
      locale,
      option?.i18n?.[locale]?.label ?? '',
    ]),
  );
  optionForm.enabled = option?.enabled ?? true;
  optionForm.sortOrder = option?.sortOrder ?? 0;
  optionModalOpen.value = true;
}

/** 校验选项表单并创建或更新数据库字典项。 */
async function saveOption(): Promise<void> {
  const dictionary = optionDictionary.value;
  if (!dictionary) return;
  await optionFormRef.value?.validate();
  optionSaving.value = true;
  try {
    const option = editingOption.value;
    if (option) {
      if (option.version === null) return;
      await businessApplication.systemDictionaries.updateOption(
        dictionary,
        option,
        {
          enabled: optionForm.enabled,
          i18n: optionI18n(),
          label: optionForm.label.trim(),
          sortOrder: optionForm.sortOrder,
          version: option.version,
        },
      );
      message.success(t('updateSuccess'));
    } else {
      await businessApplication.systemDictionaries.createOption(
        dictionary.key,
        {
          enabled: optionForm.enabled,
          i18n: optionI18n(),
          label: optionForm.label.trim(),
          sortOrder: optionForm.sortOrder,
          value: optionForm.value.trim(),
        },
      );
      message.success(t('createSuccess'));
    }
    optionModalOpen.value = false;
  } catch {
    message.error(errorText(systemDictionaries.value.error));
  } finally {
    optionSaving.value = false;
  }
}

/** 把已填写的 Locale 标签转换成后端完整替换使用的 LocalizedFields。 */
function optionI18n(): LocalizedFields {
  return Object.fromEntries(
    supportedLocales.value.flatMap((locale) => {
      const label = optionForm.labels[locale]?.trim();
      return label ? [[locale, { label }]] : [];
    }),
  );
}
/** 提供 字典选项创建与编辑 的独立工作区入口。 */
defineExpose({ open: openOptionModal });
</script>
<template>
  <SjResourceEditor
    v-model:open="optionModalOpen"
    :saving="optionSaving"
    :title="editingOption ? t('editOption') : t('createOption')"
    @submit="saveOption"
    :create="editingOption === null"
    size="lg"
  >
    <a-form ref="optionFormRef" layout="vertical" :model="optionForm">
      <a-form-item
        name="value"
        :label="t('optionValue')"
        :rules="[
          { required: true, message: t('required') },
          {
            pattern: /^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$/,
            message: t('required'),
          },
        ]"
      >
        <a-input
          v-model:value="optionForm.value"
          :disabled="editingOption !== null"
        />
      </a-form-item>
      <a-form-item
        name="label"
        :label="t('optionLabel')"
        :rules="[{ required: true, message: t('required') }]"
      >
        <a-input v-model:value="optionForm.label" />
      </a-form-item>
      <a-form-item
        v-for="locale in supportedLocales"
        :key="locale"
        :name="['labels', locale]"
        :label="t('optionLabelForLocale', { locale })"
      >
        <a-input
          v-model:value="optionForm.labels[locale]"
          :maxlength="160"
          show-count
        />
      </a-form-item>
      <a-form-item name="enabled" :label="t('status')">
        <a-switch
          v-model:checked="optionForm.enabled"
          :checked-children="t('active')"
          :un-checked-children="t('disabled')"
        />
      </a-form-item>
      <a-form-item name="sortOrder" :label="t('sortOrder')">
        <a-input-number
          v-model:value="optionForm.sortOrder"
          :max="1_000_000"
          :min="0"
          class="w-full"
        />
      </a-form-item>
    </a-form>
  </SjResourceEditor>
</template>
