<script setup lang="ts">
import type {
  SmsProvider,
  SmsProviderBinding,
  SmsProviderBindingInput,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { onScopeDispose as disposeEditor, watch as watchEditor } from 'vue';
import { computed, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';
import { Plus, Trash2 } from 'lucide-vue-next';

import { SjResourceEditor } from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { businessApplication } from '#/platform/business-application';

const props = defineProps<{
  /** 绑定所属的 Provider。 */ provider: null | SmsProvider;
}>();

const { errorText } = useSjPresentation();

let editorGeneration = 0;
disposeEditor(() => {
  editorGeneration++;
});

interface MappingRow {
  providerParameter: string;
  variable: string;
}

const smsNotifications = useSjControllerState(
  businessApplication.smsNotifications,
);
const session = useSjAccess();
const canManage = computed(() => session.value.capabilities.smsManage);
const selectedProvider = computed(() => props.provider);

const bindingDrawerOpen = ref(false);
const bindingSaving = ref(false);
const bindingFormRef = ref<FormInstance>();
const editingBinding = ref<null | SmsProviderBinding>(null);
const mappingRows = ref<MappingRow[]>([]);
const bindingForm = reactive({
  languageTag: 'zh-CN',
  notificationCode: '',
  providerTemplateCode: '',
  route: 'china-mainland',
  signName: '',
});

/** 打开模板绑定创建或详情抽屉。 */
async function openBinding(
  binding: null | SmsProviderBinding = null,
): Promise<void> {
  const read = ++editorGeneration;
  const provider = selectedProvider.value;
  if (!provider) return;
  businessApplication.smsNotifications.clearBinding();
  editingBinding.value = binding;
  if (binding)
    editingBinding.value =
      await businessApplication.smsNotifications.requireBinding(
        provider.providerId,
        binding.bindingId,
      );
  if (read !== editorGeneration) return;
  const detail = editingBinding.value;
  bindingForm.notificationCode = detail?.notificationCode ?? '';
  bindingForm.route = detail?.route ?? 'china-mainland';
  bindingForm.languageTag = detail?.languageTag ?? 'zh-CN';
  bindingForm.signName = detail?.signName ?? '';
  bindingForm.providerTemplateCode = detail?.providerTemplateCode ?? '';
  mappingRows.value = Object.entries(detail?.parameterMapping ?? {}).map(
    ([providerParameter, variable]) => ({ providerParameter, variable }),
  );
  if (mappingRows.value.length === 0)
    mappingRows.value.push({ providerParameter: '', variable: '' });
  bindingDrawerOpen.value = true;
  bindingFormRef.value?.clearValidate();
}

/** 创建或完整替换停用绑定。 */
async function saveBinding(): Promise<void> {
  const provider = selectedProvider.value;
  if (!provider) return;
  await bindingFormRef.value?.validate();
  const parameterMapping: Record<string, string> = {};
  for (const row of mappingRows.value) {
    const key = row.providerParameter.trim();
    const value = row.variable.trim();
    if (!key || !value || key in parameterMapping) {
      message.error(t('invalidMapping'));
      return;
    }
    parameterMapping[key] = value;
  }
  const input: SmsProviderBindingInput = {
    languageTag: bindingForm.languageTag.trim(),
    notificationCode: bindingForm.notificationCode.trim(),
    parameterMapping,
    providerTemplateCode: bindingForm.providerTemplateCode.trim(),
    route: bindingForm.route.trim(),
    signName: bindingForm.signName.trim(),
  };
  bindingSaving.value = true;
  try {
    await (editingBinding.value
      ? businessApplication.smsNotifications.replaceBinding(input)
      : businessApplication.smsNotifications.createBinding(
          provider.providerId,
          input,
        ));
    bindingDrawerOpen.value = false;
    message.success(t('saved'));
  } catch {
    message.error(errorText(smsNotifications.value.error));
  } finally {
    bindingSaving.value = false;
  }
}

function addMapping(): void {
  mappingRows.value.push({ providerParameter: '', variable: '' });
}

function t(key: string): string {
  return $t(`page.smsNotifications.${key}`);
}
/** 提供 SMS 模板绑定 的独立工作区入口。 */
defineExpose({ open: openBinding });

watchEditor(bindingDrawerOpen, (open) => {
  if (!open) editorGeneration++;
});
</script>
<template>
  <SjResourceEditor
    :read-only="!canManage"
    v-model:open="bindingDrawerOpen"
    :title="editingBinding ? t('bindingDetails') : t('createBinding')"
    :create="editingBinding === null"
    size="lg"
    :saving="bindingSaving"
  >
    <a-form
      ref="bindingFormRef"
      layout="vertical"
      :model="bindingForm"
      :disabled="!canManage || Boolean(editingBinding?.enabled)"
    >
      <a-form-item
        name="notificationCode"
        :label="t('notificationCode')"
        :rules="[{ required: true }]"
      >
        <a-input v-model:value="bindingForm.notificationCode" />
      </a-form-item>
      <div class="grid grid-cols-2 gap-3">
        <a-form-item
          name="route"
          :label="t('route')"
          :rules="[{ required: true }]"
        >
          <a-input v-model:value="bindingForm.route" />
        </a-form-item>
        <a-form-item
          name="languageTag"
          :label="t('locale')"
          :rules="[{ required: true }]"
        >
          <a-input v-model:value="bindingForm.languageTag" />
        </a-form-item>
      </div>
      <a-form-item
        name="signName"
        :label="t('signName')"
        :rules="[{ required: true }]"
      >
        <a-input v-model:value="bindingForm.signName" />
      </a-form-item>
      <a-form-item
        name="providerTemplateCode"
        :label="t('templateCode')"
        :rules="[{ required: true }]"
      >
        <a-input v-model:value="bindingForm.providerTemplateCode" />
      </a-form-item>
      <a-form-item :label="t('parameterMapping')">
        <div
          v-for="(row, index) in mappingRows"
          :key="index"
          class="mb-2 grid grid-cols-[1fr_1fr_40px] gap-2"
        >
          <a-input
            v-model:value="row.providerParameter"
            :placeholder="t('providerParameter')"
          />
          <a-input
            v-model:value="row.variable"
            :placeholder="t('notificationVariable')"
          />
          <a-button type="text" danger @click="mappingRows.splice(index, 1)">
            <Trash2 :size="16" />
          </a-button>
        </div>
        <a-button type="dashed" block @click="addMapping">
          <Plus :size="16" />
          {{ t('addMapping') }}
        </a-button>
      </a-form-item>
    </a-form>
    <template #footer>
      <div class="flex justify-end gap-2">
        <a-button @click="bindingDrawerOpen = false">
          {{ t('cancel') }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          :loading="bindingSaving"
          :disabled="Boolean(editingBinding?.enabled)"
          @click="saveBinding"
        >
          {{ t('save') }}
        </a-button>
      </div>
    </template>
  </SjResourceEditor>
</template>
