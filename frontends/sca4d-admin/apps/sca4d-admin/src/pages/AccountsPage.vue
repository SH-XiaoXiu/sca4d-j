<script setup lang="ts">
import type { AccountStatus, PlatformAccount } from '@sca4d/admin';

import type { SjRowAction } from '#/components/sj';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { message } from 'ant-design-vue';
import { Plus, Trash2, UserCog } from 'lucide-vue-next';

import {
  SjConfirmSwitch,
  SjFileAvatar,
  SjPage,
  SjRowActions,
  useSjVxeGrid,
} from '#/components/sj';
import AccountCreateModal from '#/features/accounts/AccountCreateModal.vue';
import AccountDeletionDialog from '#/features/accounts/AccountDeletionDialog.vue';
import AccountSettingsDrawer from '#/features/accounts/AccountSettingsDrawer.vue';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';
import {
  resourceQueryId,
  withoutResourceQuery,
} from '#/platform/resource-reference-routes';

const { t, dateTime, errorText, language } = useSjPresentation();

const accounts = useSjControllerState(businessApplication.accounts);
const dictionaries = useSjControllerState(
  businessApplication.systemDictionaries,
);
const session = useSjAccess();
const route = useRoute();
const router = useRouter();
const createOpen = ref(false);
const selectedAccount = ref<null | PlatformAccount>(null);
const deletionAccount = ref<null | PlatformAccount>(null);
const statusSaving = ref(new Set<string>());
const canManage = computed(() => session.value.capabilities.accountsManage);
const statusOptions = computed(() =>
  (dictionaries.value.selections['iam.identity-status']?.options ?? []).filter(
    (item) => item.value === 'ACTIVE' || item.value === 'DISABLED',
  ),
);
/** 账号筛选字段，页码和页大小由 Grid 提供。 */
interface AccountSearchValues {
  search?: string;
  status?: AccountStatus;
}
const [Grid, gridApi] = useSjVxeGrid<PlatformAccount>(() => ({
  formOptions: {
    schema: [
      {
        component: 'Input',
        fieldName: 'search',
        label: t('username'),
        componentProps: { allowClear: true, placeholder: t('searchAccounts') },
      },
      {
        component: 'Select',
        fieldName: 'status',
        label: t('status'),
        componentProps: {
          allowClear: true,
          options: statusOptions.value,
          placeholder: t('allStatuses'),
        },
      },
    ],
  },
  gridOptions: {
    columns: [
      {
        field: 'identity',
        title: t('username'),
        minWidth: 220,
        slots: { default: 'identity' },
      },
      { field: 'email', title: t('email'), minWidth: 200 },
      { field: 'phoneNumber', title: t('phoneNumber'), minWidth: 160 },
      {
        field: 'updatedAt',
        title: t('updatedAt'),
        minWidth: 180,
        slots: { default: 'updatedAt' },
      },
      {
        field: 'status',
        title: t('status'),
        width: 120,
        slots: { default: 'status' },
      },
      {
        field: 'actions',
        title: t('actions'),
        width: 120,
        slots: { default: 'actions' },
      },
    ],
    pagerConfig: {},
    rowConfig: { keyField: 'id' },
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          values: AccountSearchValues,
        ) => {
          await businessApplication.accounts.load({
            page: page.currentPage,
            size: page.pageSize,
            search: values.search?.trim() ?? '',
            status: values.status ?? '',
          });
          return accounts.value.page;
        },
      },
    },
  },
}));
// Controller 写入已经刷新当前查询，Grid 只同步快照，避免重复读取。
watch(
  () => accounts.value.page,
  (page) =>
    gridApi.setGridOptions({
      data: [...page.items],
      pagerConfig: {
        currentPage: page.page,
        pageSize: page.size,
        total: page.total,
      },
    }),
);
watch(language, () => {
  void gridApi.query();
});
onMounted(async () => {
  try {
    await businessApplication.systemDictionaries.loadSelections([
      'iam.identity-status',
    ]);
    gridApi.formApi.updateSchema([
      {
        fieldName: 'status',
        componentProps: {
          allowClear: true,
          options: statusOptions.value,
          placeholder: t('allStatuses'),
        },
      },
    ]);
  } catch {
    /* 字典不是账号读取权限的前置条件，状态仍使用后端固定值。 */
  }
});
watch(
  () => resourceQueryId(route.query, 'accountId'),
  async (id) => {
    if (!id) return;
    try {
      const account = await businessApplication.accounts.require(id);
      if (resourceQueryId(route.query, 'accountId') === id)
        selectedAccount.value = account;
    } catch (error) {
      if (resourceQueryId(route.query, 'accountId') === id) {
        message.error(errorText(error));
        await closeLinkedAccount();
      }
    }
  },
  { immediate: true },
);
/** 关闭已消费的资源链接，保留 URL 中其他查询条件。 */
async function closeLinkedAccount() {
  if (resourceQueryId(route.query, 'accountId'))
    await router.replace({
      query: withoutResourceQuery(route.query, 'accountId'),
    });
}
/** 账号保护规则只限制展示；最终判断仍由服务端执行。 */
function protectedAccount(account: PlatformAccount) {
  return (
    account.protectedRoot ||
    account.id === session.value.session?.account.accountId
  );
}
/** 为当前账号声明查看及删除动作，删除先确认对象与直接后果。 */
function actions(account: PlatformAccount): readonly SjRowAction[] {
  return [
    {
      key: 'settings',
      label: t('accountSettings'),
      icon: UserCog,
      run: () => {
        selectedAccount.value = account;
      },
    },
    {
      key: 'delete',
      label: t('deleteAccount'),
      icon: Trash2,
      visible: canManage.value,
      disabled: protectedAccount(account) || deletionAccount.value !== null,
      disabledReason: protectedAccount(account)
        ? t('protectedRoot')
        : undefined,
      danger: true,
      confirm: {
        title: t('deleteAccount'),
        content: `${account.displayName} (${account.username}) — ${t('deleteAccountWarning')}`,
        okText: t('deleteAccount'),
      },
      run: () => {
        deletionAccount.value = account;
      },
    },
  ];
}
/** 只保护当前行的状态写入，其他账号仍可独立操作。 */
async function changeStatus(account: PlatformAccount, active: boolean) {
  if (statusSaving.value.has(account.id)) return;
  statusSaving.value.add(account.id);
  try {
    await businessApplication.accounts.changeStatus(
      account,
      active ? 'ACTIVE' : 'DISABLED',
    );
    message.success(t('updateSuccess'));
  } catch (error) {
    message.error(errorText(error));
  } finally {
    statusSaving.value.delete(account.id);
  }
}
</script>
<template>
  <SjPage :title="t('accounts')">
    <Grid>
      <template #toolbar-tools>
        <a-button v-if="canManage" type="primary" @click="createOpen = true">
          <Plus :size="17" aria-hidden="true" />
          {{ t('createAccount') }}
        </a-button>
      </template>
      <template #identity="{ row }">
        <div class="flex min-w-0 items-center gap-3">
          <SjFileAvatar :file-id="row.avatarFileId" :name="row.displayName" />
          <div class="min-w-0">
            <strong class="block truncate">{{ row.displayName }}</strong>
            <span class="text-xs text-muted-foreground">
              {{ row.username }}
            </span>
          </div>
        </div>
      </template>
      <template #updatedAt="{ row }">{{ dateTime(row.updatedAt) }}</template>
      <template #status="{ row }">
        <SjConfirmSwitch
          v-if="canManage"
          :checked="row.status === 'ACTIVE'"
          :disabled="protectedAccount(row)"
          :loading="statusSaving.has(row.id)"
          :aria-label="row.displayName"
          :checked-children="t('active')"
          :un-checked-children="t('disabled')"
          :confirm-title="`${row.displayName}: ${row.status === 'ACTIVE' ? t('disabled') : t('active')}`"
          :ok-text="row.status === 'ACTIVE' ? t('disabled') : t('active')"
          :cancel-text="t('cancel')"
          @confirm="changeStatus(row, $event)"
        />
        <a-tag v-else>
          {{ row.status === 'ACTIVE' ? t('active') : t('disabled') }}
        </a-tag>
      </template>
      <template #actions="{ row }">
        <SjRowActions :actions="actions(row)" />
      </template>
    </Grid>
    <AccountCreateModal v-model:open="createOpen" />
    <AccountSettingsDrawer
      v-model:account="selectedAccount"
      @closed="closeLinkedAccount"
    />
    <AccountDeletionDialog v-model:account="deletionAccount" />
  </SjPage>
</template>
