<script setup lang="ts">
import type { SmsProvider, SmsProviderInput } from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { onScopeDispose as disposeEditor, watch as watchEditor } from 'vue';
import { computed, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { SjResourceEditor } from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { businessApplication } from '#/platform/business-application';

const { errorText } = useSjPresentation();

let editorGeneration = 0;
disposeEditor(() => {
  editorGeneration++;
});

const smsNotifications = useSjControllerState(
  businessApplication.smsNotifications,
);
const session = useSjAccess();
const canManage = computed(() => session.value.capabilities.smsManage);
const providerDrawerOpen = ref(false);
const providerSaving = ref(false);
const providerFormRef = ref<FormInstance>();
const editingProvider = ref<null | SmsProvider>(null);
const providerForm = reactive({
  code: '',
  credentialRef: '',
  priority: 100,
  type: 'ALIYUN',
});

/** 打开 Provider 创建或详情抽屉。 */
async function openProvider(
  provider: null | SmsProvider = null,
): Promise<void> {
  const read = ++editorGeneration;
  businessApplication.smsNotifications.clearProvider();
  editingProvider.value = provider;
  if (provider)
    editingProvider.value =
      await businessApplication.smsNotifications.requireProvider(
        provider.providerId,
      );
  if (read !== editorGeneration) return;
  providerForm.code = editingProvider.value?.code ?? '';
  providerForm.type = editingProvider.value?.type ?? 'ALIYUN';
  providerForm.credentialRef = editingProvider.value?.credentialRef ?? '';
  providerForm.priority = editingProvider.value?.priority ?? 100;
  providerDrawerOpen.value = true;
  providerFormRef.value?.clearValidate();
}

/** 创建或完整替换停用 Provider。 */
async function saveProvider(): Promise<void> {
  await providerFormRef.value?.validate();
  providerSaving.value = true;
  const input: SmsProviderInput = {
    code: providerForm.code.trim(),
    credentialRef: providerForm.credentialRef.trim(),
    priority: providerForm.priority,
    type: providerForm.type.trim().toUpperCase(),
  };
  try {
    await (editingProvider.value
      ? businessApplication.smsNotifications.replaceProvider(input)
      : businessApplication.smsNotifications.createProvider(input));
    providerDrawerOpen.value = false;
    message.success(t('saved'));
  } catch {
    message.error(errorText(smsNotifications.value.error));
  } finally {
    providerSaving.value = false;
  }
}

function t(key: string): string {
  return $t(`page.smsNotifications.${key}`);
}
/** 提供 SMS Provider 配置 的独立工作区入口。 */
defineExpose({ open: openProvider });

watchEditor(providerDrawerOpen, (open) => {
  if (!open) editorGeneration++;
});
</script>
<template>
  <SjResourceEditor
    :read-only="!canManage"
    v-model:open="providerDrawerOpen"
    :title="editingProvider ? t('providerDetails') : t('createProvider')"
    :create="editingProvider === null"
    size="lg"
    :saving="providerSaving"
  >
    <a-form
      ref="providerFormRef"
      layout="vertical"
      :model="providerForm"
      :disabled="!canManage || Boolean(editingProvider?.enabled)"
    >
      <a-form-item name="code" :label="t('code')" :rules="[{ required: true }]">
        <a-input v-model:value="providerForm.code" />
      </a-form-item>
      <a-form-item name="type" :label="t('type')" :rules="[{ required: true }]">
        <a-input v-model:value="providerForm.type" />
      </a-form-item>
      <a-form-item
        name="credentialRef"
        :label="t('credentialRef')"
        :rules="[{ required: true }]"
      >
        <a-input v-model:value="providerForm.credentialRef" />
      </a-form-item>
      <a-form-item name="priority" :label="t('priority')">
        <a-input-number v-model:value="providerForm.priority" class="w-full" />
      </a-form-item>
    </a-form>
    <template #footer>
      <div class="flex justify-end gap-2">
        <a-button @click="providerDrawerOpen = false">
          {{ t('cancel') }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          :loading="providerSaving"
          :disabled="Boolean(editingProvider?.enabled)"
          @click="saveProvider"
        >
          {{ t('save') }}
        </a-button>
      </div>
    </template>
  </SjResourceEditor>
</template>
