<script setup lang="ts">
import type { FederatedProvider } from '@sca4d/admin';

import { computed, onMounted, ref } from 'vue';

import { message } from 'ant-design-vue';
import { Eye, Globe2, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-vue-next';

import { SjDataGrid, SjPage, SjResourceState } from '#/components/sj';
import { SjRowActions } from '#/components/sj';
import { SjConfirmSwitch } from '#/components/sj';
import FederatedProviderEditor from '#/features/federated/FederatedProviderEditor.vue';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { $t } from '#/locales';
import { $t as sjTranslate } from '#/locales';
import { businessApplication } from '#/platform/business-application';

const providerEditor = ref<InstanceType<typeof FederatedProviderEditor>>();

const federatedProviders = useSjControllerState(
  businessApplication.federatedProviders,
);
const session = useSjAccess();
const statusSavingCode = ref<null | string>(null);
const deletingCode = ref<null | string>(null);

const canManage = computed(
  () => session.value.capabilities.federatedProvidersManage,
);
const providerColumns = computed(() => [
  { field: 'provider', title: t('provider'), width: 240 },
  { field: 'protocol', title: t('protocol'), width: 110 },
  { field: 'status', title: t('status'), width: 130 },
  { field: 'loginPage', title: t('loginPage'), width: 130 },
  { field: 'actions', title: t('actions'), width: 150 },
]);

onMounted(() => void reload());

/** 读取 Provider 列表并让 ResourceState 显示权限或网络失败。 */
async function reload(): Promise<void> {
  try {
    await businessApplication.federatedProviders.load();
  } catch {
    // Controller 已保存统一错误和加载阶段。
  }
}

/** 使用 Provider 当前版本启用或停用登录入口。 */
async function changeStatus(
  provider: FederatedProvider,
  active: boolean,
): Promise<void> {
  statusSavingCode.value = provider.code;
  try {
    await businessApplication.federatedProviders.setStatus(
      provider,
      active ? 'ACTIVE' : 'DISABLED',
    );
    message.success(t('updated'));
  } catch {
    message.error(t('saveFailed'));
  } finally {
    statusSavingCode.value = null;
  }
}

/** 删除停用 Provider；后端会拒绝仍有身份绑定的 Provider。 */
async function remove(provider: FederatedProvider): Promise<void> {
  deletingCode.value = provider.code;
  try {
    await businessApplication.federatedProviders.delete(provider);
    message.success(t('deleted'));
  } catch {
    message.error(t('deleteFailed'));
  } finally {
    deletingCode.value = null;
  }
}

function t(key: string): string {
  return $t(`page.federatedProviders.${key}`);
}
</script>

<template>
  <SjPage auto-content-height>
    <SjResourceState
      :has-data="federatedProviders.providers.length > 0"
      :error="federatedProviders.error"
      :phase="federatedProviders.phase"
      @retry="reload"
    >
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-2 text-base font-medium">
          <Globe2 :size="19" aria-hidden="true" />
          <span>{{ t('title') }}</span>
        </div>
        <div class="flex items-center gap-2">
          <a-tooltip :title="t('refresh')">
            <a-button :aria-label="t('refresh')" @click="reload">
              <RefreshCw :size="17" aria-hidden="true" />
            </a-button>
          </a-tooltip>
          <a-button
            v-if="canManage"
            type="primary"
            @click="providerEditor?.create"
          >
            <Plus :size="17" aria-hidden="true" />
            {{ t('create') }}
          </a-button>
        </div>
      </div>

      <SjDataGrid
        :columns="providerColumns"
        :items="federatedProviders.providers"
        key-field="code"
      >
        <template #cell="{ column, row, value }">
          <div v-if="column.field === 'provider'" class="flex min-w-0 flex-col">
            <strong class="truncate">{{ row.name }}</strong>
            <span class="text-muted-foreground truncate text-xs">
              {{ row.code }} · {{ row.buttonText }}
            </span>
          </div>
          <a-tag v-else-if="column.field === 'protocol'" color="blue">
            {{ row.protocol === 'OIDC' ? t('oidc') : t('oauth2') }}
          </a-tag>
          <template v-else-if="column.field === 'status'">
            <SjConfirmSwitch
              v-if="canManage"
              :checked="row.status === 'ACTIVE'"
              :checked-children="t('active')"
              :disabled="statusSavingCode !== null"
              :loading="statusSavingCode === row.code"
              :un-checked-children="t('disabled')"
              :aria-label="`${row.name} ${row.status === 'ACTIVE' ? t('active') : t('disabled')}`"
              @confirm="changeStatus(row, $event as boolean)"
              :confirm-title="`${row.name} ${row.status === 'ACTIVE' ? t('active') : t('disabled')}`"
              :ok-text="
                row.status === 'ACTIVE'
                  ? sjTranslate('sj.disabled')
                  : sjTranslate('sj.active')
              "
              :cancel-text="sjTranslate('sj.cancel')"
            />
            <a-tag
              v-else
              :color="row.status === 'ACTIVE' ? 'green' : 'default'"
            >
              {{ row.status === 'ACTIVE' ? t('active') : t('disabled') }}
            </a-tag>
          </template>
          <a-tag
            v-else-if="column.field === 'loginPage'"
            :color="row.loginPageVisible ? 'cyan' : 'default'"
          >
            {{ row.loginPageVisible ? t('visible') : t('hidden') }}
          </a-tag>
          <SjRowActions
            v-else-if="column.field === 'actions'"
            :actions="[
              {
                key: 'action-0',
                label: canManage ? t('edit') : t('view'),
                icon: canManage ? Pencil : Eye,
                run: async () => {
                  await providerEditor?.open(row);
                },
              },
              {
                key: 'action-1',
                label: t('delete'),
                icon: Trash2,
                run: async () => {
                  await remove(row);
                },
                visible: canManage,
                disabled: row.status === 'ACTIVE' || deletingCode !== null,
                disabledReason: sjTranslate('sj.actionUnavailable'),
                loading: deletingCode === row.code,
                danger: true,
                confirm: {
                  title: t('confirmDelete'),
                  content: row.name,
                  okText: t('delete'),
                },
              },
            ]"
          />
          <span
            v-if="
              ![
                'actions',
                'loginPage',
                'protocol',
                'provider',
                'status',
              ].includes(column.field)
            "
          >
            {{ value ?? '-' }}
          </span>
        </template>
      </SjDataGrid>
    </SjResourceState>
  </SjPage>

  <FederatedProviderEditor ref="providerEditor" />
</template>

<!-- 联邦 Provider 配置 -->
