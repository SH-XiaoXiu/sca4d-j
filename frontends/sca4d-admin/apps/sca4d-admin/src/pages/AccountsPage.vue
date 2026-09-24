<script setup lang="ts">
import type {
  AccountDeletionOutcome,
  AccountStatus,
  CreateAccountInput,
  Int64String,
  PlatformAccount,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { AdminError, AUTHENTICATION_METHOD_CODES } from '@sca4d/admin';
import { message } from 'ant-design-vue';
import {
  Camera,
  KeyRound,
  Plus,
  Save,
  Trash2,
  UserCog,
  X,
} from 'lucide-vue-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';

import AvatarDisplay from '../components/AvatarDisplay.vue';
import { useI18n } from '../i18n';
import {
  resourceQueryId,
  withoutResourceQuery,
} from '../platform/resource-reference-routes';
import { useAdminStore } from '../store/business';

const store = useAdminStore();
const userStore = useUserStore();
const route = useRoute();
const router = useRouter();
const { t, dateTime, errorText } = useI18n();
const createOpen = ref(false);
const createSaving = ref(false);
const createFormRef = ref<FormInstance>();
const createForm = reactive({
  username: '',
  initialPassword: '',
  displayName: '',
  email: '',
  phoneNumber: '',
});
const roleDrawerOpen = ref(false);
const roleLoading = ref(false);
const roleSaving = ref(false);
const selectedAccount = ref<null | PlatformAccount>(null);
const selectedRoleIds = ref<string[]>([]);
const deleteOpen = ref(false);
const deleteSaving = ref(false);
const deleteStage = ref<
  | typeof AUTHENTICATION_METHOD_CODES.passkey
  | typeof AUTHENTICATION_METHOD_CODES.password
  | typeof AUTHENTICATION_METHOD_CODES.totp
>(AUTHENTICATION_METHOD_CODES.password);
const deleteAccount = ref<null | PlatformAccount>(null);
const deleteForm = reactive({ code: '', password: '' });
const avatarUrls = ref(new Map<Int64String, string>());
const retriedAvatarKeys = new Set<string>();
const avatarInput = ref<HTMLInputElement>();
const selectedAvatar = ref<File | null>(null);
const avatarPreviewUrl = ref<null | string>(null);
const avatarSaving = ref(false);
const avatarDeleting = ref(false);
const avatarTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);
const avatarMaxSize = 5 * 1024 * 1024;

const canManage = computed(() => store.session.capabilities.accountsManage);
const accountStatusOptions = computed(() =>
  (store.systemDictionaries.selections['iam.identity-status']?.options ?? [])
    .filter((option) => isAccountStatus(option.value))
    .map((option) => ({
      disabled: option.disabled,
      label: option.label,
      value: option.value,
    })),
);
const deletionMethodOptions = computed<{ label: string; value: string }[]>(() => {
  const methods =
    store.accounts.pendingDeletion?.verification.availableMethods ?? [];
  return methods.flatMap<{ label: string; value: string }>((method) => {
    if (method === AUTHENTICATION_METHOD_CODES.password)
      return [{ label: t('passwordCredential'), value: method }];
    if (method === AUTHENTICATION_METHOD_CODES.totp)
      return [{ label: t('totpCredential'), value: method }];
    if (method === AUTHENTICATION_METHOD_CODES.passkey)
      return [{ label: t('passkeys'), value: method }];
    return [];
  });
});

onBeforeUnmount(clearSelectedAvatar);
onMounted(() => void loadAccountStatusOptions());
watch(
  () => resourceQueryId(route.query, 'accountId'),
  (accountId) => {
    if (accountId) void openLinkedAccount(accountId);
  },
  { immediate: true },
);

interface AccountSearchValues {
  search?: string;
  status?: AccountStatus;
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: t('searchAccounts'),
        },
        fieldName: 'search',
        label: t('username'),
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: accountStatusOptions.value,
          placeholder: t('allStatuses'),
        },
        fieldName: 'status',
        label: t('status'),
      },
    ],
    submitOnEnter: true,
  },
  gridOptions: {
    columns: [
      {
        field: 'identity',
        minWidth: 220,
        slots: { default: 'identity' },
        title: t('username'),
      },
      { field: 'email', minWidth: 200, title: t('email') },
      { field: 'phoneNumber', minWidth: 160, title: t('phoneNumber') },
      {
        field: 'updatedAt',
        minWidth: 180,
        slots: { default: 'updatedAt' },
        title: t('updatedAt'),
      },
      {
        field: 'status',
        slots: { default: 'status' },
        title: t('status'),
        width: 120,
      },
      {
        field: 'actions',
        fixed: 'right',
        slots: { default: 'actions' },
        title: t('actions'),
        width: 120,
      },
    ],
    height: 'auto',
    pagerConfig: {},
    proxyConfig: {
      ajax: {
        query: async ({ page }, values: AccountSearchValues) => {
          await store.loadAccounts({
            page: page.currentPage,
            search: values.search?.trim() ?? '',
            size: page.pageSize,
            status: values.status ?? '',
          });
          await loadAccountAvatars();
          return store.accounts.page;
        },
      },
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      search: true,
      zoom: true,
    },
  } as VxeTableGridOptions<PlatformAccount>,
});

/** 开始账号删除认证，并按后端返回的方法选择首个可提交因素。 */
async function openDelete(account: PlatformAccount): Promise<void> {
  deleteAccount.value = account;
  deleteForm.code = '';
  deleteForm.password = '';
  deleteSaving.value = true;
  try {
    const outcome = await store.beginAccountDeletion(account);
    if (outcome === 'deleted') {
      await gridApi.query();
      message.success(t('deleteSuccess'));
      return;
    }
    selectAvailableDeletionMethod();
    deleteOpen.value = true;
  } catch {
    message.error(errorText(store.accounts.error));
  } finally {
    deleteSaving.value = false;
  }
}

/** 提交用户选择的认证方法；尚需其他因素时刷新可选方法。 */
async function submitDeletion(): Promise<void> {
  const account = deleteAccount.value;
  if (!account) return;
  deleteSaving.value = true;
  try {
    let outcome: AccountDeletionOutcome;
    if (deleteStage.value === AUTHENTICATION_METHOD_CODES.totp) {
      outcome = await store.submitAccountDeletionTotp(deleteForm.code.trim());
    } else if (deleteStage.value === AUTHENTICATION_METHOD_CODES.passkey) {
      outcome = await store.submitAccountDeletionPasskey();
    } else {
      outcome = await store.submitAccountDeletionPassword(deleteForm.password);
    }
    if (outcome === 'factorRequired') {
      deleteForm.code = '';
      deleteForm.password = '';
      selectAvailableDeletionMethod();
      return;
    }
    deleteOpen.value = false;
    await gridApi.query();
    message.success(t('deleteSuccess'));
  } catch {
    message.error(errorText(store.accounts.error));
  } finally {
    deleteSaving.value = false;
  }
}

/** 优先选择当前 verification 返回的第一个已支持认证方法。 */
function selectAvailableDeletionMethod(): void {
  const method = deletionMethodOptions.value[0]?.value;
  if (
    method === AUTHENTICATION_METHOD_CODES.password ||
    method === AUTHENTICATION_METHOD_CODES.totp ||
    method === AUTHENTICATION_METHOD_CODES.passkey
  ) {
    deleteStage.value = method;
  }
}

/** 关闭删除窗口并清除 Controller 中未完成的 verification。 */
function cancelDeletion(): void {
  store.cancelAccountDeletion();
  deleteOpen.value = false;
  deleteAccount.value = null;
  deleteForm.code = '';
  deleteForm.password = '';
}

/** 返回账号当前头像地址，未绑定或读取失败时返回空。 */
function avatarUrl(account: PlatformAccount): null | string {
  return avatarUrls.value.get(account.id) ?? null;
}

/** 批量读取当前页头像地址；任一地址无权读取时整页改用占位。 */
async function loadAccountAvatars(): Promise<void> {
  const fileIds = store.accounts.page.items.flatMap((account) =>
    account.avatarFileId ? [account.avatarFileId] : [],
  );
  if (fileIds.length === 0) {
    avatarUrls.value = new Map();
    return;
  }
  try {
    const resolved = await store.resolveAvatarUrls(fileIds);
    avatarUrls.value = new Map(
      store.accounts.page.items.flatMap((account) => {
        const url = account.avatarFileId
          ? resolved.get(account.avatarFileId)
          : null;
        return url ? [[account.id, url] as const] : [];
      }),
    );
  } catch {
    avatarUrls.value = new Map();
  }
}

/** 读取账号状态的聚合字典，并更新筛选控件使用的选项。 */
async function loadAccountStatusOptions(): Promise<void> {
  try {
    await store.loadDictionarySelections(['iam.identity-status']);
    gridApi.formApi.updateSchema([
      {
        componentProps: {
          allowClear: true,
          options: accountStatusOptions.value,
          placeholder: t('allStatuses'),
        },
        fieldName: 'status',
      },
    ]);
  } catch {
    // 没有字典读取权限时，账号操作仍使用后端固定状态值。
  }
}

/** 打开头像文件选择器，并允许再次选择同一个文件。 */
function chooseAvatar(): void {
  if (!avatarInput.value) return;
  avatarInput.value.value = '';
  avatarInput.value.click();
}

/** 校验头像媒体类型和大小，并生成确认前使用的本地预览。 */
function stageAvatar(event: Event): void {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  if (!avatarTypes.has(file.type)) {
    message.warning(t('avatarInvalidType'));
    return;
  }
  if (file.size > avatarMaxSize) {
    message.warning(t('avatarTooLarge'));
    return;
  }
  clearSelectedAvatar();
  selectedAvatar.value = file;
  avatarPreviewUrl.value = URL.createObjectURL(file);
}

/** 上传预览文件并把它绑定为目标账号头像。 */
async function saveAvatar(): Promise<void> {
  const account = selectedAccount.value;
  const file = selectedAvatar.value;
  if (!account || !file) return;
  avatarSaving.value = true;
  try {
    const fileId = await store.uploadAvatar(account.id, file);
    await store.replaceAccountAvatar(account.id, fileId);
    selectedAccount.value =
      store.accounts.page.items.find(
        (candidate) => candidate.id === account.id,
      ) ?? account;
    const resolvedUrls = await store.resolveAvatarUrls([fileId], true);
    const url = resolvedUrls.get(fileId);
    const next = new Map(avatarUrls.value);
    if (url) next.set(account.id, url);
    else next.delete(account.id);
    avatarUrls.value = next;
    syncCurrentUserAvatar(account.id, url ?? null);
    clearSelectedAvatar();
    message.success(t('avatarUpdated'));
  } catch (error) {
    message.error(
      error instanceof AdminError ? errorText(error) : t('avatarUploadFailed'),
    );
  } finally {
    avatarSaving.value = false;
  }
}

/** 删除目标账号头像并立即恢复固定占位。 */
async function deleteAvatar(): Promise<void> {
  const account = selectedAccount.value;
  if (!account) return;
  avatarDeleting.value = true;
  try {
    await store.deleteAccountAvatar(account.id);
    selectedAccount.value =
      store.accounts.page.items.find(
        (candidate) => candidate.id === account.id,
      ) ?? account;
    const next = new Map(avatarUrls.value);
    next.delete(account.id);
    avatarUrls.value = next;
    syncCurrentUserAvatar(account.id, null);
    clearSelectedAvatar();
    message.success(t('avatarDeleted'));
  } catch (error) {
    message.error(
      error instanceof AdminError ? errorText(error) : t('avatarDeleteFailed'),
    );
  } finally {
    avatarDeleting.value = false;
  }
}

/** 图片加载失败时只为同一账号的同一文件重新申请一次地址。 */
async function retryAvatar(account: PlatformAccount): Promise<void> {
  const fileId = account.avatarFileId;
  if (!fileId) return;
  const retryKey = `${account.id}:${fileId}`;
  if (retriedAvatarKeys.has(retryKey)) {
    const next = new Map(avatarUrls.value);
    next.delete(account.id);
    avatarUrls.value = next;
    return;
  }
  retriedAvatarKeys.add(retryKey);
  try {
    const resolvedUrls = await store.resolveAvatarUrls([fileId], true);
    const url = resolvedUrls.get(fileId);
    const next = new Map(avatarUrls.value);
    if (url) next.set(account.id, url);
    else next.delete(account.id);
    avatarUrls.value = next;
  } catch {
    const next = new Map(avatarUrls.value);
    next.delete(account.id);
    avatarUrls.value = next;
  }
}

/** 修改当前登录账号时同步 Vben 顶部用户菜单头像。 */
function syncCurrentUserAvatar(
  accountId: Int64String,
  value: null | string,
): void {
  if (accountId !== store.session.session?.account.accountId) return;
  const current = userStore.userInfo;
  if (current) userStore.setUserInfo({ ...current, avatar: value ?? '' });
}

/** 释放头像维护窗口创建的本地预览地址。 */
function clearSelectedAvatar(): void {
  if (avatarPreviewUrl.value) URL.revokeObjectURL(avatarPreviewUrl.value);
  avatarPreviewUrl.value = null;
  selectedAvatar.value = null;
}

/** 校验表单并创建账号。 */
async function createAccount(): Promise<void> {
  await createFormRef.value?.validate();
  createSaving.value = true;
  try {
    const input: CreateAccountInput = {
      username: createForm.username.trim(),
      initialPassword: createForm.initialPassword,
      displayName: createForm.displayName.trim(),
      email: createForm.email.trim() || null,
      phoneNumber: createForm.phoneNumber.trim() || null,
    };
    await store.createAccount(input);
    await gridApi.reload();
    createOpen.value = false;
    createFormRef.value?.resetFields();
    message.success(t('createSuccess'));
  } catch {
    message.error(errorText(store.accounts.error));
  } finally {
    createSaving.value = false;
  }
}

/** 使用当前行版本启用或停用账号。 */
async function changeStatus(
  account: PlatformAccount,
  checked: boolean,
): Promise<void> {
  try {
    await store.changeAccountStatus(account, checked ? 'ACTIVE' : 'DISABLED');
    await gridApi.query();
    message.success(t('updateSuccess'));
  } catch {
    message.error(errorText(store.accounts.error));
  }
}

/** 打开账号设置抽屉并读取当前角色关系。 */
async function openAccountSettings(account: PlatformAccount): Promise<void> {
  clearSelectedAvatar();
  selectedAccount.value = account;
  roleDrawerOpen.value = true;
  roleLoading.value = true;
  try {
    await store.loadAccountRoles(account.id);
    selectedRoleIds.value = [...(store.accounts.selectedRoles?.roleIds ?? [])];
  } catch {
    message.error(errorText(store.accounts.error));
  } finally {
    roleLoading.value = false;
  }
}

/** 读取 URL 指定的账号并复用账号设置抽屉展示完整资料。 */
async function openLinkedAccount(accountId: Int64String): Promise<void> {
  try {
    const account = await store.loadAccount(accountId);
    await openAccountSettings(account);
  } catch {
    message.error(errorText(store.accounts.error));
    await router.replace({
      query: withoutResourceQuery(route.query, 'accountId'),
    });
  }
}

/** 账号设置抽屉关闭后释放未提交的头像预览。 */
function accountSettingsVisibilityChanged(open: boolean): void {
  if (open) return;
  clearSelectedAvatar();
  if (resourceQueryId(route.query, 'accountId')) {
    void router.replace({
      query: withoutResourceQuery(route.query, 'accountId'),
    });
  }
}

/** 原子替换抽屉中账号的角色。 */
async function saveRoles(): Promise<void> {
  roleSaving.value = true;
  try {
    await store.replaceAccountRoles(selectedRoleIds.value);
    roleDrawerOpen.value = false;
    message.success(t('saveSuccess'));
  } catch {
    message.error(errorText(store.accounts.error));
  } finally {
    roleSaving.value = false;
  }
}

function statusText(status: AccountStatus): string {
  return (
    accountStatusOptions.value.find((option) => option.value === status)
      ?.label ?? status
  );
}

/** 检查聚合字典项是否仍是 Platform Account 支持的固定状态。 */
function isAccountStatus(value: string): value is AccountStatus {
  return value === 'ACTIVE' || value === 'DISABLED';
}
</script>

<template>
  <Page auto-content-height>
    <Grid :table-title="t('accounts')">
      <template #toolbar-tools>
        <a-button v-if="canManage" type="primary" @click="createOpen = true">
          <Plus :size="17" aria-hidden="true" />{{ t('createAccount') }}
        </a-button>
      </template>
      <template #identity="{ row }">
        <div class="flex min-w-0 items-center gap-2.5 text-left">
          <AvatarDisplay
            :name="row.displayName"
            :src="avatarUrl(row)"
            @image-error="retryAvatar(row)"
          />
          <div class="flex min-w-0 flex-1 flex-col gap-0.5">
            <strong class="truncate">{{ row.displayName }}</strong>
            <span class="text-muted-foreground truncate text-xs">{{
              row.username
            }}</span>
          </div>
        </div>
      </template>
      <template #updatedAt="{ row }">
        {{ dateTime(row.updatedAt) }}
      </template>
      <template #status="{ row }">
        <a-switch
          v-if="canManage"
          :checked="row.status === 'ACTIVE'"
          :checked-children="statusText('ACTIVE')"
          :un-checked-children="statusText('DISABLED')"
          :disabled="
            row.protectedRoot ||
            row.id === store.session.session?.account.accountId
          "
          :aria-label="`${row.username} ${statusText(row.status)}`"
          @change="changeStatus(row, $event as boolean)"
        />
        <a-tag v-else :color="row.status === 'ACTIVE' ? 'green' : 'default'">
          {{ statusText(row.status) }}
        </a-tag>
      </template>
      <template #actions="{ row }">
        <div class="flex justify-center gap-1">
          <a-tooltip :title="t('accountSettings')">
            <a-button
              type="text"
              :aria-label="t('accountSettings')"
              @click="openAccountSettings(row)"
            >
              <UserCog :size="17" aria-hidden="true" />
            </a-button>
          </a-tooltip>
          <a-tooltip
            :title="row.protectedRoot ? t('protectedRoot') : t('deleteAccount')"
          >
            <a-button
              v-if="canManage"
              type="text"
              danger
              :disabled="
                row.protectedRoot ||
                row.id === store.session.session?.account.accountId
              "
              :aria-label="t('deleteAccount')"
              @click="openDelete(row)"
            >
              <Trash2 :size="17" aria-hidden="true" />
            </a-button>
          </a-tooltip>
        </div>
      </template>
    </Grid>

    <a-modal
      v-model:open="createOpen"
      :title="t('createAccount')"
      :confirm-loading="createSaving"
      :ok-text="t('save')"
      :cancel-text="t('cancel')"
      @ok="createAccount"
    >
      <a-form ref="createFormRef" layout="vertical" :model="createForm">
        <a-form-item
          name="username"
          :label="t('username')"
          :rules="[{ required: true, message: t('required') }]"
        >
          <a-input
            v-model:value="createForm.username"
            autocomplete="username"
          />
        </a-form-item>
        <a-form-item
          name="initialPassword"
          :label="t('initialPassword')"
          :rules="[
            { required: true, message: t('required') },
            { min: 8, max: 128, message: t('passwordLength') },
          ]"
        >
          <a-input-password
            v-model:value="createForm.initialPassword"
            autocomplete="new-password"
          />
        </a-form-item>
        <a-form-item
          name="displayName"
          :label="t('displayName')"
          :rules="[{ required: true, message: t('required') }]"
        >
          <a-input v-model:value="createForm.displayName" autocomplete="name" />
        </a-form-item>
        <a-form-item
          name="email"
          :label="t('email')"
          :rules="[{ type: 'email', message: t('invalidEmail') }]"
        >
          <a-input v-model:value="createForm.email" autocomplete="email" />
        </a-form-item>
        <a-form-item
          name="phoneNumber"
          :label="t('phoneNumber')"
          :rules="[
            { pattern: /^\+[1-9]\d{1,14}$/, message: t('invalidPhone') },
          ]"
        >
          <a-input v-model:value="createForm.phoneNumber" autocomplete="tel" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-drawer
      v-model:open="roleDrawerOpen"
      :title="t('accountSettings')"
      :width="480"
      @after-open-change="accountSettingsVisibilityChanged"
    >
      <a-spin :spinning="roleLoading">
        <div
          v-if="selectedAccount"
          class="mb-5 flex min-h-32 items-center gap-5 border-b pb-6 max-sm:flex-col max-sm:items-start"
        >
          <button
            v-if="canManage"
            class="group relative inline-flex shrink-0 overflow-hidden rounded-full border-0 bg-transparent p-0"
            type="button"
            :aria-label="t('chooseAvatar')"
            :disabled="avatarSaving || avatarDeleting"
            @click="chooseAvatar"
          >
            <AvatarDisplay
              :name="selectedAccount.displayName"
              :src="avatarPreviewUrl || avatarUrl(selectedAccount)"
              :size="88"
              @image-error="retryAvatar(selectedAccount)"
            />
            <span
              class="absolute inset-0 grid place-items-center rounded-full bg-slate-950/60 text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100"
              aria-hidden="true"
            >
              <Camera :size="21" />
            </span>
          </button>
          <AvatarDisplay
            v-else
            :name="selectedAccount.displayName"
            :src="avatarUrl(selectedAccount)"
            :size="88"
            @image-error="retryAvatar(selectedAccount)"
          />
          <div class="flex min-w-0 flex-1 flex-col items-start gap-2">
            <strong>{{ selectedAccount.displayName }}</strong>
            <span
              class="text-muted-foreground block max-w-full truncate text-xs"
            >
              {{ selectedAvatar?.name || selectedAccount.username }}
            </span>
            <input
              ref="avatarInput"
              class="hidden"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              @change="stageAvatar"
            />
            <div v-if="canManage" class="flex flex-wrap items-center gap-2">
              <a-button
                v-if="selectedAvatar"
                type="primary"
                :loading="avatarSaving"
                @click="saveAvatar"
              >
                <Save :size="17" aria-hidden="true" />{{ t('useAvatar') }}
              </a-button>
              <a-tooltip v-if="selectedAvatar" :title="t('cancel')">
                <a-button
                  class="w-9 px-0"
                  :aria-label="t('cancel')"
                  :disabled="avatarSaving"
                  @click="clearSelectedAvatar"
                >
                  <X :size="17" aria-hidden="true" />
                </a-button>
              </a-tooltip>
              <a-popconfirm
                v-if="selectedAccount.avatarFileId && !selectedAvatar"
                :title="t('confirmDeleteAvatar')"
                :ok-text="t('remove')"
                :cancel-text="t('cancel')"
                @confirm="deleteAvatar"
              >
                <a-button danger :loading="avatarDeleting">
                  <Trash2 :size="17" aria-hidden="true" />{{
                    t('removeAvatar')
                  }}
                </a-button>
              </a-popconfirm>
            </div>
            <small class="text-muted-foreground">{{
              t('avatarRequirements')
            }}</small>
          </div>
        </div>
        <h3 class="mb-3 text-sm font-semibold">
          {{ t('accountRoles') }}
        </h3>
        <a-checkbox-group v-model:value="selectedRoleIds" class="flex flex-col">
          <a-checkbox
            v-for="role in store.accounts.roles"
            :key="role.id"
            :value="role.id"
            :disabled="!canManage"
          >
            <span>{{ role.name }}</span>
            <small class="text-muted-foreground ml-2">{{ role.code }}</small>
          </a-checkbox>
        </a-checkbox-group>
      </a-spin>
      <template #footer>
        <div class="flex justify-end gap-2">
          <a-button @click="roleDrawerOpen = false">
            {{ t('cancel') }}
          </a-button>
          <a-button
            v-if="canManage"
            type="primary"
            :loading="roleSaving"
            @click="saveRoles"
          >
            {{ t('save') }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-modal
      v-model:open="deleteOpen"
      :title="t('deleteAccount')"
      :confirm-loading="deleteSaving"
      :ok-text="t('deletePermanently')"
      :cancel-text="t('cancel')"
      ok-type="danger"
      :closable="false"
      :mask-closable="false"
      @ok="submitDeletion"
      @cancel="cancelDeletion"
    >
      <a-alert type="error" show-icon :message="t('deleteAccountWarning')" />
      <a-segmented
        v-if="deletionMethodOptions.length > 1"
        v-model:value="deleteStage"
        class="mt-5"
        :options="deletionMethodOptions"
      />
      <a-form
        class="mt-5"
        layout="vertical"
        :model="deleteForm"
        @finish="submitDeletion"
      >
        <a-form-item
          v-if="deleteStage === AUTHENTICATION_METHOD_CODES.totp"
          :label="t('totp')"
          required
        >
          <a-input
            v-model:value="deleteForm.code"
            :aria-label="t('totp')"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            autofocus
          >
            <template #prefix>
              <KeyRound :size="17" aria-hidden="true" />
            </template>
          </a-input>
        </a-form-item>
        <a-form-item
          v-else-if="deleteStage === AUTHENTICATION_METHOD_CODES.password"
          :label="t('currentPassword')"
          required
        >
          <a-input-password
            v-model:value="deleteForm.password"
            :aria-label="t('currentPassword')"
            autocomplete="current-password"
            autofocus
          />
        </a-form-item>
        <a-alert
          v-else
          type="info"
          show-icon
          :message="t('usePasskeyToContinue')"
        />
      </a-form>
    </a-modal>
  </Page>
</template>
