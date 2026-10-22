<script setup lang="ts">
import type {
  Int64String,
  SmsProvider,
  SmsProviderBinding,
} from '@sca4d/admin';

import { computed, onMounted, ref } from 'vue';

import { message } from 'ant-design-vue';
import { Eye, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-vue-next';

import { SjDataGrid, SjPage, SjResourceState } from '#/components/sj';
import { SjRowActions } from '#/components/sj';
import { SjConfirmSwitch } from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import SmsBindingEditor from '#/features/sms/SmsBindingEditor.vue';
import SmsProviderEditor from '#/features/sms/SmsProviderEditor.vue';
import { $t } from '#/locales';
import { $t as sjTranslate } from '#/locales';
import { businessApplication } from '#/platform/business-application';

const providerEditor = ref<InstanceType<typeof SmsProviderEditor>>();
const bindingEditor = ref<InstanceType<typeof SmsBindingEditor>>();

const { errorText } = useSjPresentation();

const smsNotifications = useSjControllerState(
  businessApplication.smsNotifications,
);
const session = useSjAccess();
const canManage = computed(() => session.value.capabilities.smsManage);
const selectedProvider = ref<null | SmsProvider>(null);
const providerStatusId = ref<Int64String | null>(null);
const bindingStatusId = ref<Int64String | null>(null);

const providerColumns = [
  { field: 'code', title: t('code') },
  { field: 'type', title: t('type'), width: 120 },
  {
    field: 'credentialRef',
    title: t('credentialRef'),
  },
  { field: 'priority', title: t('priority'), width: 100 },
  { field: 'enabled', title: t('status'), width: 110 },
  { field: 'actions', title: t('actions'), width: 170 },
];
const bindingColumns = [
  {
    field: 'notificationCode',
    title: t('notificationCode'),
  },
  { field: 'route', title: t('route'), width: 160 },
  {
    field: 'languageTag',
    title: t('locale'),
    width: 110,
  },
  {
    field: 'providerTemplateCode',
    title: t('templateCode'),
  },
  { field: 'enabled', title: t('status'), width: 110 },
  { field: 'actions', title: t('actions'), width: 150 },
];

onMounted(() => void reload());

/** 读取 Provider，并在已有选择仍存在时刷新其绑定。 */
async function reload(): Promise<void> {
  try {
    await businessApplication.smsNotifications.loadProviders();
    const selected = selectedProvider.value;
    if (selected) {
      selectedProvider.value =
        smsNotifications.value.providers.find(
          (provider) => provider.providerId === selected.providerId,
        ) ?? null;
      if (selectedProvider.value)
        await businessApplication.smsNotifications.loadBindings(
          selected.providerId,
        );
    }
  } catch {
    message.error(errorText(smsNotifications.value.error));
  }
}

/** 选择 Provider 并读取绑定。 */
async function selectProvider(provider: SmsProvider): Promise<void> {
  selectedProvider.value = provider;
  try {
    await businessApplication.smsNotifications.loadBindings(
      provider.providerId,
    );
  } catch {
    message.error(errorText(smsNotifications.value.error));
  }
}

/** 修改 Provider 启停状态。 */
async function changeProviderStatus(
  provider: SmsProvider,
  enabled: boolean,
): Promise<void> {
  providerStatusId.value = provider.providerId;
  try {
    await businessApplication.smsNotifications.setProviderEnabled(
      provider.providerId,
      enabled,
    );
    message.success(t('saved'));
  } catch {
    message.error(errorText(smsNotifications.value.error));
  } finally {
    providerStatusId.value = null;
  }
}

/** 删除停用且没有绑定的 Provider。 */
async function removeProvider(provider: SmsProvider): Promise<void> {
  try {
    await businessApplication.smsNotifications.deleteProvider(
      provider.providerId,
    );
    if (selectedProvider.value?.providerId === provider.providerId)
      selectedProvider.value = null;
    message.success(t('deleted'));
  } catch {
    message.error(errorText(smsNotifications.value.error));
  }
}

/** 修改绑定启停状态。 */
async function changeBindingStatus(
  binding: SmsProviderBinding,
  enabled: boolean,
): Promise<void> {
  bindingStatusId.value = binding.bindingId;
  try {
    await businessApplication.smsNotifications.setBindingEnabled(
      binding.providerId,
      binding.bindingId,
      enabled,
    );
    message.success(t('saved'));
  } catch {
    message.error(errorText(smsNotifications.value.error));
  } finally {
    bindingStatusId.value = null;
  }
}

/** 删除停用绑定。 */
async function removeBinding(binding: SmsProviderBinding): Promise<void> {
  try {
    await businessApplication.smsNotifications.deleteBinding(
      binding.providerId,
      binding.bindingId,
    );
    message.success(t('deleted'));
  } catch {
    message.error(errorText(smsNotifications.value.error));
  }
}

function t(key: string): string {
  return $t(`page.smsNotifications.${key}`);
}
</script>

<template>
  <SjPage auto-content-height>
    <div class="mb-4 flex items-center justify-between gap-3">
      <h2 class="text-base font-medium">{{ t('title') }}</h2>
      <div class="flex gap-2">
        <a-button @click="reload">
          <RefreshCw :size="17" />
          {{ t('refresh') }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          @click="providerEditor?.open()"
        >
          <Plus :size="17" />
          {{ t('createProvider') }}
        </a-button>
      </div>
    </div>
    <SjResourceState
      :has-data="smsNotifications.providers.length > 0"
      :phase="smsNotifications.phase"
      :error="smsNotifications.error"
      @retry="reload"
    >
      <SjDataGrid
        :columns="providerColumns"
        :items="smsNotifications.providers"
        key-field="providerId"
      >
        <template #cell="{ column, row, value }">
          <a-button
            v-if="column.field === 'code'"
            type="link"
            @click="selectProvider(row)"
          >
            {{ row.code }}
          </a-button>
          <SjConfirmSwitch
            v-else-if="column.field === 'enabled' && canManage"
            :checked="row.enabled"
            :loading="providerStatusId === row.providerId"
            @confirm="changeProviderStatus(row, $event as boolean)"
            :confirm-title="sjTranslate('sj.status')"
            :ok-text="
              row.enabled
                ? sjTranslate('sj.disabled')
                : sjTranslate('sj.active')
            "
            :cancel-text="sjTranslate('sj.cancel')"
          />
          <a-tag
            v-else-if="column.field === 'enabled'"
            :color="row.enabled ? 'green' : 'default'"
          >
            {{ row.enabled ? t('enabled') : t('disabled') }}
          </a-tag>
          <SjRowActions
            v-else-if="column.field === 'actions'"
            :actions="[
              {
                key: 'action-0',
                label: sjTranslate('sj.edit'),
                icon: canManage ? Pencil : Eye,
                run: async () => {
                  await providerEditor?.open(row);
                },
              },
              {
                key: 'action-1',
                label: sjTranslate('sj.remove'),
                icon: Trash2,
                run: async () => {
                  await removeProvider(row);
                },
                visible: canManage,
                disabled: row.enabled,
                disabledReason: sjTranslate('sj.actionUnavailable'),
                danger: true,
                confirm: {
                  title: t('confirmDeleteProvider'),
                  content: row.code,
                  okText: sjTranslate('sj.remove'),
                },
              },
            ]"
          />
          <span v-if="!['actions', 'code', 'enabled'].includes(column.field)">
            {{ value ?? '-' }}
          </span>
        </template>
      </SjDataGrid>
    </SjResourceState>

    <a-card
      v-if="selectedProvider"
      class="mt-4"
      :title="`${t('bindings')}: ${selectedProvider.code}`"
    >
      <template #extra>
        <a-button
          v-if="canManage"
          type="primary"
          @click="bindingEditor?.open()"
        >
          <Plus :size="17" />
          {{ t('createBinding') }}
        </a-button>
      </template>
      <SjDataGrid
        :columns="bindingColumns"
        :items="smsNotifications.bindings"
        key-field="bindingId"
      >
        <template #cell="{ column, row, value }">
          <SjConfirmSwitch
            v-if="column.field === 'enabled' && canManage"
            :checked="row.enabled"
            :loading="bindingStatusId === row.bindingId"
            @confirm="changeBindingStatus(row, $event as boolean)"
            :confirm-title="sjTranslate('sj.status')"
            :ok-text="
              row.enabled
                ? sjTranslate('sj.disabled')
                : sjTranslate('sj.active')
            "
            :cancel-text="sjTranslate('sj.cancel')"
          />
          <a-tag
            v-else-if="column.field === 'enabled'"
            :color="row.enabled ? 'green' : 'default'"
          >
            {{ row.enabled ? t('enabled') : t('disabled') }}
          </a-tag>
          <SjRowActions
            v-else-if="column.field === 'actions'"
            :actions="[
              {
                key: 'action-0',
                label: sjTranslate('sj.edit'),
                icon: canManage ? Pencil : Eye,
                run: async () => {
                  await bindingEditor?.open(row);
                },
              },
              {
                key: 'action-1',
                label: sjTranslate('sj.remove'),
                icon: Trash2,
                run: async () => {
                  await removeBinding(row);
                },
                visible: canManage,
                disabled: row.enabled,
                disabledReason: sjTranslate('sj.actionUnavailable'),
                danger: true,
                confirm: {
                  title: t('confirmDeleteBinding'),
                  content: row.notificationCode,
                  okText: sjTranslate('sj.remove'),
                },
              },
            ]"
          />
          <span v-if="!['actions', 'enabled'].includes(column.field)">
            {{ value ?? '-' }}
          </span>
        </template>
      </SjDataGrid>
    </a-card>
  </SjPage>

  <SmsProviderEditor ref="providerEditor" />

  <SmsBindingEditor ref="bindingEditor" :provider="selectedProvider" />
</template>

<!-- SMS Provider 配置 -->

<!-- SMS 模板绑定 -->
