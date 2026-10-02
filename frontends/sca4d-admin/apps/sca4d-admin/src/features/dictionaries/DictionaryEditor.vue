<script setup lang="ts">
import type { ManagedDictionary } from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { SjResourceEditor } from '#/components/sj';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const { t, errorText } = useSjPresentation();

const systemDictionaries = useSjControllerState(
  businessApplication.systemDictionaries,
);
const dictionaryModalOpen = ref(false);
const dictionarySaving = ref(false);
const dictionaryFormRef = ref<FormInstance>();
const editingDictionary = ref<ManagedDictionary | null>(null);
const dictionaryForm = reactive({
  enabled: true,
  key: '',
  label: '',
  sortOrder: 0,
});

/** 打开新建或编辑动态字典窗口。 */
function openDictionaryModal(
  dictionary: ManagedDictionary | null = null,
): void {
  editingDictionary.value = dictionary;
  dictionaryForm.key = dictionary?.key ?? '';
  dictionaryForm.label = dictionary?.label ?? '';
  dictionaryForm.enabled = dictionary?.enabled ?? true;
  dictionaryForm.sortOrder = dictionary?.sortOrder ?? 0;
  dictionaryModalOpen.value = true;
}

/** 校验字典表单并创建或更新动态字典。 */
async function saveDictionary(): Promise<void> {
  await dictionaryFormRef.value?.validate();
  dictionarySaving.value = true;
  try {
    const dictionary = editingDictionary.value;
    if (dictionary) {
      if (dictionary.version === null) return;
      await businessApplication.systemDictionaries.update(dictionary, {
        enabled: dictionaryForm.enabled,
        label: dictionaryForm.label.trim(),
        sortOrder: dictionaryForm.sortOrder,
        version: dictionary.version,
      });
      message.success(t('updateSuccess'));
    } else {
      await businessApplication.systemDictionaries.create({
        enabled: dictionaryForm.enabled,
        key: dictionaryForm.key.trim(),
        label: dictionaryForm.label.trim(),
        sortOrder: dictionaryForm.sortOrder,
      });
      message.success(t('createSuccess'));
    }
    dictionaryModalOpen.value = false;
  } catch {
    message.error(errorText(systemDictionaries.value.error));
  } finally {
    dictionarySaving.value = false;
  }
}
/** 提供 动态字典创建与编辑 的独立工作区入口。 */
defineExpose({ open: openDictionaryModal });
</script>
<template>
  <SjResourceEditor
    v-model:open="dictionaryModalOpen"
    :saving="dictionarySaving"
    :title="editingDictionary ? t('editDictionary') : t('createDictionary')"
    @submit="saveDictionary"
    :create="editingDictionary === null"
    size="lg"
  >
    <a-form ref="dictionaryFormRef" layout="vertical" :model="dictionaryForm">
      <a-form-item
        name="key"
        :label="t('dictionaryKey')"
        :rules="[
          { required: true, message: t('required') },
          {
            pattern: /^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+$/,
            message: t('required'),
          },
        ]"
      >
        <a-input
          v-model:value="dictionaryForm.key"
          :disabled="editingDictionary !== null"
        />
      </a-form-item>
      <a-form-item
        name="label"
        :label="t('dictionaryLabel')"
        :rules="[{ required: true, message: t('required') }]"
      >
        <a-input v-model:value="dictionaryForm.label" />
      </a-form-item>
      <a-form-item name="enabled" :label="t('status')">
        <a-switch
          v-model:checked="dictionaryForm.enabled"
          :checked-children="t('active')"
          :un-checked-children="t('disabled')"
        />
      </a-form-item>
      <a-form-item name="sortOrder" :label="t('sortOrder')">
        <a-input-number
          v-model:value="dictionaryForm.sortOrder"
          :max="1_000_000"
          :min="0"
          class="w-full"
        />
      </a-form-item>
    </a-form>
  </SjResourceEditor>
</template>
