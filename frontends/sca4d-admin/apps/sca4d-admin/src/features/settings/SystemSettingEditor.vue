<script setup lang="ts">
import type { SystemSetting } from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { SjResourceEditor } from '#/components/sj';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const { t, errorText } = useSjPresentation();

const systemSettings = useSjControllerState(businessApplication.systemSettings);
const settingModalOpen = ref(false);
const settingSaving = ref(false);
const settingFormRef = ref<FormInstance>();
const editingSetting = ref<null | SystemSetting>(null);
const settingForm = reactive({ value: '' });

/** 打开设置覆盖窗口；敏感设置不回填当前值。 */
function openSettingModal(setting: SystemSetting): void {
  editingSetting.value = setting;
  settingForm.value = setting.secret
    ? ''
    : settingInputValue(setting.value ?? setting.defaultValue);
  settingModalOpen.value = true;
}

/** 把文本提交给后端对应 SettingCodec 校验和保存。 */
async function saveSetting(): Promise<void> {
  const setting = editingSetting.value;
  if (!setting) return;
  await settingFormRef.value?.validate();
  settingSaving.value = true;
  try {
    const value: unknown =
      setting.valueType === 'object'
        ? JSON.parse(settingForm.value)
        : settingForm.value;
    await businessApplication.systemSettings.replace(setting, value);
    settingModalOpen.value = false;
    message.success(t('saveSuccess'));
  } catch {
    message.error(errorText(systemSettings.value.error));
  } finally {
    settingSaving.value = false;
  }
}

/** 把设置 JSON 值转换为表单和表格可以稳定展示的文本。 */
function settingInputValue(value: unknown): string {
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
}
/** 提供 系统设置编辑 的独立工作区入口。 */
defineExpose({ open: openSettingModal });
</script>
<template>
  <SjResourceEditor
    v-model:open="settingModalOpen"
    :saving="settingSaving"
    :title="t('editSetting')"
    @submit="saveSetting"
    @closed="
      settingForm.value = '';
      editingSetting = null;
    "
    :create="false"
    size="md"
  >
    <a-form ref="settingFormRef" layout="vertical" :model="settingForm">
      <a-form-item :label="editingSetting?.label">
        <a-input :value="editingSetting?.key" disabled />
      </a-form-item>
      <a-form-item :label="t('valueType')">
        <a-tag color="blue">{{ editingSetting?.valueType }}</a-tag>
      </a-form-item>
      <a-form-item
        name="value"
        :label="t('settingsValue')"
        :rules="[{ required: true, message: t('required') }]"
      >
        <a-input-password
          v-if="editingSetting?.secret"
          v-model:value="settingForm.value"
        />
        <a-textarea
          v-else-if="editingSetting?.valueType === 'object'"
          v-model:value="settingForm.value"
          :auto-size="{ minRows: 5, maxRows: 14 }"
        />
        <a-input v-else v-model:value="settingForm.value" />
      </a-form-item>
    </a-form>
  </SjResourceEditor>
</template>
