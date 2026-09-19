<script setup lang="ts">
import type {
  Int64String,
  SmsProvider,
  SmsProviderBinding,
  SmsProviderBindingInput,
  SmsProviderInput,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import { Eye, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-vue-next';

import { $t } from '#/locales';

import ResourceState from '../components/ResourceState.vue';
import { useI18n } from '../i18n';
import { useAdminStore } from '../store/business';

interface MappingRow {
  providerParameter: string;
  variable: string;
}

const store = useAdminStore();
const { errorText } = useI18n();
const canManage = computed(() => store.session.capabilities.smsManage);
const selectedProvider = ref<SmsProvider | null>(null);
const providerDrawerOpen = ref(false);
const providerSaving = ref(false);
const providerFormRef = ref<FormInstance>();
const editingProvider = ref<SmsProvider | null>(null);
const providerStatusId = ref<Int64String | null>(null);
const providerForm = reactive({
  code: '',
  credentialRef: '',
  priority: 100,
  type: 'ALIYUN',
});

const bindingDrawerOpen = ref(false);
const bindingSaving = ref(false);
const bindingFormRef = ref<FormInstance>();
const editingBinding = ref<SmsProviderBinding | null>(null);
const bindingStatusId = ref<Int64String | null>(null);
const mappingRows = ref<MappingRow[]>([]);
const bindingForm = reactive({
  languageTag: 'zh-CN',
  notificationCode: '',
  providerTemplateCode: '',
  route: 'china-mainland',
  signName: '',
});

const providerColumns = [
  { dataIndex: 'code', key: 'code', title: t('code') },
  { dataIndex: 'type', key: 'type', title: t('type'), width: 120 },
  {
    dataIndex: 'credentialRef',
    key: 'credentialRef',
    title: t('credentialRef'),
  },
  { dataIndex: 'priority', key: 'priority', title: t('priority'), width: 100 },
  { key: 'enabled', title: t('status'), width: 110 },
  { key: 'actions', title: t('actions'), width: 170 },
];
const bindingColumns = [
  {
    dataIndex: 'notificationCode',
    key: 'notificationCode',
    title: t('notificationCode'),
  },
  { dataIndex: 'route', key: 'route', title: t('route'), width: 160 },
  {
    dataIndex: 'languageTag',
    key: 'languageTag',
    title: t('locale'),
    width: 110,
  },
  {
    dataIndex: 'providerTemplateCode',
    key: 'providerTemplateCode',
    title: t('templateCode'),
  },
  { key: 'enabled', title: t('status'), width: 110 },
  { key: 'actions', title: t('actions'), width: 150 },
];

onMounted(() => void reload());

/** 读取 Provider，并在已有选择仍存在时刷新其绑定。 */
async function reload(): Promise<void> {
  try {
    await store.loadSmsProviders();
    const selected = selectedProvider.value;
    if (selected) {
      selectedProvider.value =
        store.smsNotifications.providers.find(
          (provider) => provider.providerId === selected.providerId,
        ) ?? null;
      if (selectedProvider.value)
        await store.loadSmsBindings(selected.providerId);
    }
  } catch {
    message.error(errorText(store.smsNotifications.error));
  }
}

/** 打开 Provider 创建或详情抽屉。 */
async function openProvider(
  provider: SmsProvider | null = null,
): Promise<void> {
  store.clearSmsProvider();
  editingProvider.value = provider;
  if (provider)
    editingProvider.value = await store.loadSmsProvider(provider.providerId);
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
    if (editingProvider.value) await store.replaceSmsProvider(input);
    else await store.createSmsProvider(input);
    providerDrawerOpen.value = false;
    message.success(t('saved'));
  } catch {
    message.error(errorText(store.smsNotifications.error));
  } finally {
    providerSaving.value = false;
  }
}

/** 选择 Provider 并读取绑定。 */
async function selectProvider(provider: SmsProvider): Promise<void> {
  selectedProvider.value = provider;
  try {
    await store.loadSmsBindings(provider.providerId);
  } catch {
    message.error(errorText(store.smsNotifications.error));
  }
}

/** 修改 Provider 启停状态。 */
async function changeProviderStatus(
  provider: SmsProvider,
  enabled: boolean,
): Promise<void> {
  providerStatusId.value = provider.providerId;
  try {
    await store.setSmsProviderEnabled(provider.providerId, enabled);
    message.success(t('saved'));
  } catch {
    message.error(errorText(store.smsNotifications.error));
  } finally {
    providerStatusId.value = null;
  }
}

/** 删除停用且没有绑定的 Provider。 */
async function removeProvider(provider: SmsProvider): Promise<void> {
  try {
    await store.deleteSmsProvider(provider.providerId);
    if (selectedProvider.value?.providerId === provider.providerId)
      selectedProvider.value = null;
    message.success(t('deleted'));
  } catch {
    message.error(errorText(store.smsNotifications.error));
  }
}

/** 打开模板绑定创建或详情抽屉。 */
async function openBinding(
  binding: SmsProviderBinding | null = null,
): Promise<void> {
  const provider = selectedProvider.value;
  if (!provider) return;
  store.clearSmsBinding();
  editingBinding.value = binding;
  if (binding)
    editingBinding.value = await store.loadSmsBinding(
      provider.providerId,
      binding.bindingId,
    );
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
    if (editingBinding.value) await store.replaceSmsBinding(input);
    else await store.createSmsBinding(provider.providerId, input);
    bindingDrawerOpen.value = false;
    message.success(t('saved'));
  } catch {
    message.error(errorText(store.smsNotifications.error));
  } finally {
    bindingSaving.value = false;
  }
}

/** 修改绑定启停状态。 */
async function changeBindingStatus(
  binding: SmsProviderBinding,
  enabled: boolean,
): Promise<void> {
  bindingStatusId.value = binding.bindingId;
  try {
    await store.setSmsBindingEnabled(
      binding.providerId,
      binding.bindingId,
      enabled,
    );
    message.success(t('saved'));
  } catch {
    message.error(errorText(store.smsNotifications.error));
  } finally {
    bindingStatusId.value = null;
  }
}

/** 删除停用绑定。 */
async function removeBinding(binding: SmsProviderBinding): Promise<void> {
  try {
    await store.deleteSmsBinding(binding.providerId, binding.bindingId);
    message.success(t('deleted'));
  } catch {
    message.error(errorText(store.smsNotifications.error));
  }
}

function addMapping(): void {
  mappingRows.value.push({ providerParameter: '', variable: '' });
}

function t(key: string): string {
  return $t(`page.smsNotifications.${key}`);
}
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 flex items-center justify-between gap-3">
      <h2 class="text-base font-medium">{{ t('title') }}</h2>
      <div class="flex gap-2">
        <a-button @click="reload"
          ><RefreshCw :size="17" />{{ t('refresh') }}</a-button
        >
        <a-button v-if="canManage" type="primary" @click="openProvider()"
          ><Plus :size="17" />{{ t('createProvider') }}</a-button
        >
      </div>
    </div>
    <ResourceState
      :phase="store.smsNotifications.phase"
      :error="store.smsNotifications.error"
      @retry="reload"
    >
      <a-table
        :columns="providerColumns"
        :data-source="store.smsNotifications.providers"
        row-key="providerId"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <a-button
            v-if="column.key === 'code'"
            type="link"
            @click="selectProvider(record)"
            >{{ record.code }}</a-button
          >
          <a-switch
            v-else-if="column.key === 'enabled' && canManage"
            :checked="record.enabled"
            :loading="providerStatusId === record.providerId"
            @change="changeProviderStatus(record, $event as boolean)"
          />
          <a-tag
            v-else-if="column.key === 'enabled'"
            :color="record.enabled ? 'green' : 'default'"
            >{{ record.enabled ? t('enabled') : t('disabled') }}</a-tag
          >
          <div v-else-if="column.key === 'actions'" class="flex gap-1">
            <a-button type="text" @click="openProvider(record)"
              ><Pencil v-if="canManage" :size="17" /><Eye v-else :size="17"
            /></a-button>
            <a-popconfirm
              v-if="canManage"
              :title="t('confirmDeleteProvider')"
              @confirm="removeProvider(record)"
              ><a-button type="text" danger :disabled="record.enabled"
                ><Trash2 :size="17" /></a-button
            ></a-popconfirm>
          </div>
        </template>
      </a-table>
    </ResourceState>

    <a-card
      v-if="selectedProvider"
      class="mt-4"
      :title="`${t('bindings')}: ${selectedProvider.code}`"
    >
      <template #extra
        ><a-button v-if="canManage" type="primary" @click="openBinding()"
          ><Plus :size="17" />{{ t('createBinding') }}</a-button
        ></template
      >
      <a-table
        :columns="bindingColumns"
        :data-source="store.smsNotifications.bindings"
        row-key="bindingId"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <a-switch
            v-if="column.key === 'enabled' && canManage"
            :checked="record.enabled"
            :loading="bindingStatusId === record.bindingId"
            @change="changeBindingStatus(record, $event as boolean)"
          />
          <a-tag
            v-else-if="column.key === 'enabled'"
            :color="record.enabled ? 'green' : 'default'"
            >{{ record.enabled ? t('enabled') : t('disabled') }}</a-tag
          >
          <div v-else-if="column.key === 'actions'" class="flex gap-1">
            <a-button type="text" @click="openBinding(record)"
              ><Pencil v-if="canManage" :size="17" /><Eye v-else :size="17"
            /></a-button>
            <a-popconfirm
              v-if="canManage"
              :title="t('confirmDeleteBinding')"
              @confirm="removeBinding(record)"
              ><a-button type="text" danger :disabled="record.enabled"
                ><Trash2 :size="17" /></a-button
            ></a-popconfirm>
          </div>
        </template>
      </a-table>
    </a-card>

    <a-drawer
      v-model:open="providerDrawerOpen"
      :title="editingProvider ? t('providerDetails') : t('createProvider')"
      width="min(600px, 100vw)"
    >
      <a-form
        ref="providerFormRef"
        layout="vertical"
        :model="providerForm"
        :disabled="!canManage || Boolean(editingProvider?.enabled)"
      >
        <a-form-item
          name="code"
          :label="t('code')"
          :rules="[{ required: true }]"
          ><a-input v-model:value="providerForm.code"
        /></a-form-item>
        <a-form-item
          name="type"
          :label="t('type')"
          :rules="[{ required: true }]"
          ><a-input v-model:value="providerForm.type"
        /></a-form-item>
        <a-form-item
          name="credentialRef"
          :label="t('credentialRef')"
          :rules="[{ required: true }]"
          ><a-input v-model:value="providerForm.credentialRef"
        /></a-form-item>
        <a-form-item name="priority" :label="t('priority')"
          ><a-input-number v-model:value="providerForm.priority" class="w-full"
        /></a-form-item>
      </a-form>
      <template #footer
        ><div class="flex justify-end gap-2">
          <a-button @click="providerDrawerOpen = false">{{
            t('cancel')
          }}</a-button
          ><a-button
            v-if="canManage"
            type="primary"
            :loading="providerSaving"
            :disabled="Boolean(editingProvider?.enabled)"
            @click="saveProvider"
            >{{ t('save') }}</a-button
          >
        </div></template
      >
    </a-drawer>

    <a-drawer
      v-model:open="bindingDrawerOpen"
      :title="editingBinding ? t('bindingDetails') : t('createBinding')"
      width="min(680px, 100vw)"
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
          ><a-input v-model:value="bindingForm.notificationCode"
        /></a-form-item>
        <div class="grid grid-cols-2 gap-3">
          <a-form-item
            name="route"
            :label="t('route')"
            :rules="[{ required: true }]"
            ><a-input v-model:value="bindingForm.route" /></a-form-item
          ><a-form-item
            name="languageTag"
            :label="t('locale')"
            :rules="[{ required: true }]"
            ><a-input v-model:value="bindingForm.languageTag"
          /></a-form-item>
        </div>
        <a-form-item
          name="signName"
          :label="t('signName')"
          :rules="[{ required: true }]"
          ><a-input v-model:value="bindingForm.signName"
        /></a-form-item>
        <a-form-item
          name="providerTemplateCode"
          :label="t('templateCode')"
          :rules="[{ required: true }]"
          ><a-input v-model:value="bindingForm.providerTemplateCode"
        /></a-form-item>
        <a-form-item :label="t('parameterMapping')">
          <div
            v-for="(row, index) in mappingRows"
            :key="index"
            class="mb-2 grid grid-cols-[1fr_1fr_40px] gap-2"
          >
            <a-input
              v-model:value="row.providerParameter"
              :placeholder="t('providerParameter')"
            /><a-input
              v-model:value="row.variable"
              :placeholder="t('notificationVariable')"
            /><a-button type="text" danger @click="mappingRows.splice(index, 1)"
              ><Trash2 :size="16"
            /></a-button>
          </div>
          <a-button type="dashed" block @click="addMapping"
            ><Plus :size="16" />{{ t('addMapping') }}</a-button
          >
        </a-form-item>
      </a-form>
      <template #footer
        ><div class="flex justify-end gap-2">
          <a-button @click="bindingDrawerOpen = false">{{
            t('cancel')
          }}</a-button
          ><a-button
            v-if="canManage"
            type="primary"
            :loading="bindingSaving"
            :disabled="Boolean(editingBinding?.enabled)"
            @click="saveBinding"
            >{{ t('save') }}</a-button
          >
        </div></template
      >
    </a-drawer>
  </Page>
</template>
