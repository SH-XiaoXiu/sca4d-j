<script setup lang="ts">
import type {
  LocalizedFields,
  PermissionTreeItem,
  PlatformRole,
  RoleStatus,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import { KeyRound, Pencil, Plus, Trash2 } from 'lucide-vue-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';

import { useI18n } from '../i18n';
import {
  resourceQueryId,
  withoutResourceQuery,
} from '../platform/resource-reference-routes';
import { useAdminStore } from '../store/business';

interface PermissionTreeView extends PermissionTreeItem {
  readonly disableCheckbox: boolean;
  readonly children: readonly PermissionTreeView[];
}

const store = useAdminStore();
const route = useRoute();
const router = useRouter();
const { t, dateTime, errorText, language } = useI18n();
const roleModalOpen = ref(false);
const roleLoading = ref(false);
const roleSaving = ref(false);
const roleFormRef = ref<FormInstance>();
const editingRole = ref<null | PlatformRole>(null);
const roleForm = reactive<{
  code: string;
  names: Record<string, string>;
}>({
  code: '',
  names: {},
});
const roleStatusSavingId = ref<null | string>(null);
const permissionsOpen = ref(false);
const permissionsLoading = ref(false);
const permissionsSaving = ref(false);
const permissionsRole = ref<null | PlatformRole>(null);
const checkedPermissionCodes = ref<string[]>([]);

const canManage = computed(() => store.session.capabilities.rolesManage);
const roleStatusOptions = computed(() =>
  (store.systemDictionaries.selections['iam.role-status']?.options ?? [])
    .filter((option) => isRoleStatus(option.value))
    .map((option) => ({
      disabled: option.disabled,
      label: option.label,
      value: option.value,
    })),
);
const localeCatalog = computed(() => store.roles.localeCatalog);
const supportedLocales = computed(
  () => localeCatalog.value?.supportedLocales ?? [],
);
const permissionTree = computed<readonly PermissionTreeView[]>(() =>
  store.roles.permissionTree.map((item) => toTreeView(item)),
);

onMounted(() => void loadRoleStatusOptions());
watch(
  () => resourceQueryId(route.query, 'roleId'),
  (roleId) => {
    if (roleId) void openLinkedRole(roleId);
  },
  { immediate: true },
);

interface RoleSearchValues {
  name?: string;
  status?: RoleStatus;
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true },
        fieldName: 'name',
        label: t('roleName'),
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: roleStatusOptions.value,
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
        field: 'name',
        minWidth: 220,
        slots: { default: 'identity' },
        title: t('roleName'),
      },
      {
        field: 'builtIn',
        slots: { default: 'kind' },
        title: t('builtIn'),
        width: 110,
      },
      {
        field: 'status',
        slots: { default: 'status' },
        title: t('status'),
        width: 110,
      },
      {
        field: 'updatedAt',
        minWidth: 180,
        slots: { default: 'updatedAt' },
        title: t('updatedAt'),
      },
      {
        field: 'actions',
        fixed: 'right',
        slots: { default: 'actions' },
        title: t('actions'),
        width: 150,
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async (_params, values: RoleSearchValues) => {
          await store.loadRoles();
          const name = values.name?.trim().toLocaleLowerCase() ?? '';
          const items = store.roles.roles.filter(
            (role) =>
              (!name ||
                role.name.toLocaleLowerCase().includes(name) ||
                role.code.toLocaleLowerCase().includes(name)) &&
              (!values.status || role.status === values.status),
          );
          return { items, total: items.length };
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
  } as VxeTableGridOptions<PlatformRole>,
});

/** 打开创建或编辑角色窗口，并读取表单需要的完整 Locale 数据。 */
async function openRoleModal(role: null | PlatformRole = null): Promise<void> {
  editingRole.value = role;
  roleForm.code = role?.code ?? '';
  roleForm.names = {};
  roleModalOpen.value = true;
  roleLoading.value = true;
  try {
    await (role ? store.loadRoleI18n(role.id) : store.loadRoleLocales());
    roleForm.names = Object.fromEntries(
      supportedLocales.value.map((locale) => [
        locale,
        role ? (store.roles.i18n?.i18n[locale]?.name ?? '') : '',
      ]),
    );
    roleFormRef.value?.clearValidate();
  } catch {
    roleModalOpen.value = false;
    message.error(errorText(store.roles.error));
  } finally {
    roleLoading.value = false;
  }
}

/** 读取 URL 指定的角色并复用角色窗口展示完整多语言名称。 */
async function openLinkedRole(roleId: string): Promise<void> {
  try {
    const role = await store.loadRole(roleId);
    await openRoleModal(role);
  } catch {
    message.error(errorText(store.roles.error));
    await router.replace({
      query: withoutResourceQuery(route.query, 'roleId'),
    });
  }
}

/** 角色窗口关闭后清除已经消费的深链接参数。 */
function roleModalClosed(): void {
  if (resourceQueryId(route.query, 'roleId')) {
    void router.replace({ query: withoutResourceQuery(route.query, 'roleId') });
  }
}

/** 创建角色或原子替换当前角色的完整多语言名称。 */
async function saveRole(): Promise<void> {
  await roleFormRef.value?.validate();
  roleSaving.value = true;
  try {
    const i18n = roleI18n();
    if (editingRole.value) {
      await store.replaceRoleI18n(i18n);
      message.success(t('updateSuccess'));
    } else {
      await store.createRole(roleForm.code.trim(), i18n);
      message.success(t('createSuccess'));
    }
    await gridApi.query();
    roleModalOpen.value = false;
  } catch {
    message.error(errorText(store.roles.error));
  } finally {
    roleSaving.value = false;
  }
}

/** 使用统一角色 ETag 单独启用或停用角色。 */
async function changeRoleStatus(
  role: PlatformRole,
  checked: boolean,
): Promise<void> {
  roleStatusSavingId.value = role.id;
  try {
    await store.changeRoleStatus(role, checked ? 'ACTIVE' : 'DISABLED');
    await gridApi.query();
    message.success(t('updateSuccess'));
  } catch {
    message.error(errorText(store.roles.error));
  } finally {
    roleStatusSavingId.value = null;
  }
}

/** 删除非内置角色。 */
async function removeRole(role: PlatformRole): Promise<void> {
  try {
    await store.deleteRole(role);
    await gridApi.query();
    message.success(t('deleteSuccess'));
  } catch {
    message.error(errorText(store.roles.error));
  }
}

/** 打开权限抽屉并读取角色当前权限。 */
async function openPermissions(role: PlatformRole): Promise<void> {
  permissionsRole.value = role;
  permissionsOpen.value = true;
  permissionsLoading.value = true;
  try {
    await store.loadRolePermissions(role.id);
    checkedPermissionCodes.value = [
      ...(store.roles.permissions?.permissionCodes ?? []),
    ];
  } catch {
    message.error(errorText(store.roles.error));
  } finally {
    permissionsLoading.value = false;
  }
}

/** 保存权限树中可以授予的选中项。 */
async function savePermissions(): Promise<void> {
  permissionsSaving.value = true;
  try {
    await store.replaceRolePermissions(checkedPermissionCodes.value);
    permissionsOpen.value = false;
    message.success(t('saveSuccess'));
  } catch {
    message.error(errorText(store.roles.error));
  } finally {
    permissionsSaving.value = false;
  }
}

/** 读取角色状态的聚合字典，并更新筛选控件使用的选项。 */
async function loadRoleStatusOptions(): Promise<void> {
  try {
    await store.loadDictionarySelections(['iam.role-status']);
    gridApi.formApi.updateSchema([
      {
        componentProps: {
          allowClear: true,
          options: roleStatusOptions.value,
          placeholder: t('allStatuses'),
        },
        fieldName: 'status',
      },
    ]);
  } catch {
    // 没有字典读取权限时，角色操作仍使用后端固定状态值。
  }
}

/** 接收 Ant Design Tree 的选中结果。 */
function updateChecked(keys: string[] | { checked: string[] }): void {
  checkedPermissionCodes.value = Array.isArray(keys)
    ? keys.map(String)
    : keys.checked.map(String);
}

function toTreeView(item: PermissionTreeItem): PermissionTreeView {
  return {
    ...item,
    disableCheckbox: !item.grantable,
    children: item.children.map((child) => toTreeView(child)),
  };
}

function roleStatus(status: RoleStatus): string {
  return (
    roleStatusOptions.value.find((option) => option.value === status)?.label ??
    status
  );
}

/** 检查聚合字典项是否仍是 Platform Role 支持的固定状态。 */
function isRoleStatus(value: string): value is RoleStatus {
  return value === 'ACTIVE' || value === 'DISABLED';
}

function roleI18n(): LocalizedFields {
  return Object.fromEntries(
    supportedLocales.value.flatMap((locale) => {
      const name = roleForm.names[locale]?.trim();
      return name ? [[locale, { name }]] : [];
    }),
  );
}

watch(language, () => void gridApi.query());
</script>

<template>
  <Page auto-content-height>
    <Grid :table-title="t('roles')">
      <template #toolbar-tools>
        <a-button v-if="canManage" type="primary" @click="openRoleModal()">
          <Plus :size="17" aria-hidden="true" />{{ t('createRole') }}
        </a-button>
      </template>
      <template #identity="{ row }">
        <div class="flex min-w-0 flex-col gap-0.5 text-left">
          <strong class="truncate">{{ row.name }}</strong>
          <span class="text-muted-foreground truncate text-xs">{{
            row.code
          }}</span>
        </div>
      </template>
      <template #kind="{ row }">
        <a-tag :color="row.builtIn ? 'blue' : 'default'">
          {{ row.builtIn ? t('builtIn') : t('custom') }}
        </a-tag>
      </template>
      <template #status="{ row }">
        <a-switch
          v-if="canManage"
          :checked="row.status === 'ACTIVE'"
          :checked-children="t('active')"
          :un-checked-children="t('disabled')"
          :loading="roleStatusSavingId === row.id"
          :disabled="row.builtIn || roleStatusSavingId !== null"
          :aria-label="`${row.name} ${roleStatus(row.status)}`"
          @change="changeRoleStatus(row, $event as boolean)"
        />
        <a-tag v-else :color="row.status === 'ACTIVE' ? 'green' : 'default'">
          {{ roleStatus(row.status) }}
        </a-tag>
      </template>
      <template #updatedAt="{ row }">
        {{ dateTime(row.updatedAt) }}
      </template>
      <template #actions="{ row }">
        <div class="flex justify-center gap-1">
          <a-tooltip :title="t('assignPermissions')">
            <a-button
              type="text"
              :aria-label="t('assignPermissions')"
              @click="openPermissions(row)"
            >
              <KeyRound :size="17" aria-hidden="true" />
            </a-button>
          </a-tooltip>
          <a-tooltip :title="t('edit')">
            <a-button
              type="text"
              :aria-label="t('edit')"
              :disabled="!canManage"
              @click="openRoleModal(row)"
            >
              <Pencil :size="17" aria-hidden="true" />
            </a-button>
          </a-tooltip>
          <a-popconfirm
            :title="t('confirmDeleteRole')"
            :ok-text="t('remove')"
            :cancel-text="t('cancel')"
            @confirm="removeRole(row)"
          >
            <a-tooltip :title="t('remove')">
              <a-button
                type="text"
                danger
                :aria-label="t('remove')"
                :disabled="!canManage || row.builtIn"
              >
                <Trash2 :size="17" aria-hidden="true" />
              </a-button>
            </a-tooltip>
          </a-popconfirm>
        </div>
      </template>
    </Grid>

    <a-modal
      v-model:open="roleModalOpen"
      :title="editingRole ? t('editRoleNames') : t('createRole')"
      :confirm-loading="roleSaving"
      :ok-button-props="{ disabled: roleLoading || !canManage }"
      :ok-text="t('save')"
      :cancel-text="t('cancel')"
      :after-close="roleModalClosed"
      @ok="saveRole"
    >
      <a-spin :spinning="roleLoading">
        <a-form
          ref="roleFormRef"
          layout="vertical"
          :model="roleForm"
          :disabled="!canManage"
        >
          <a-form-item
            name="code"
            :label="t('roleCode')"
            :rules="[
              { required: true, whitespace: true, message: t('required') },
            ]"
          >
            <a-input
              v-model:value="roleForm.code"
              :disabled="editingRole !== null"
              :maxlength="128"
            />
          </a-form-item>
          <a-form-item
            v-for="locale in supportedLocales"
            :key="locale"
            :name="['names', locale]"
            :rules="[
              {
                required: locale === localeCatalog?.defaultLocale,
                whitespace: locale === localeCatalog?.defaultLocale,
                message: t('required'),
              },
              { max: 128, message: t('roleNameLength') },
            ]"
          >
            <template #label>
              <span class="inline-flex items-center gap-2">
                {{ t('roleNameForLocale', { locale }) }}
                <a-tag
                  v-if="locale === localeCatalog?.defaultLocale"
                  color="blue"
                >
                  {{ t('defaultLocale') }}
                </a-tag>
              </span>
            </template>
            <a-input v-model:value="roleForm.names[locale]" :maxlength="128" />
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>

    <a-drawer
      v-model:open="permissionsOpen"
      :title="t('rolePermissions')"
      :width="520"
    >
      <a-spin :spinning="permissionsLoading">
        <a-tree
          v-if="permissionTree.length > 0"
          checkable
          default-expand-all
          :tree-data="permissionTree"
          :checked-keys="checkedPermissionCodes"
          :disabled="!canManage || permissionsRole?.code === 'ROOT'"
          @check="updateChecked($event as string[] | { checked: string[] })"
        />
        <a-empty v-else :description="t('noData')" />
      </a-spin>
      <template #footer>
        <div class="flex justify-end gap-2">
          <a-button @click="permissionsOpen = false">
            {{ t('cancel') }}
          </a-button>
          <a-button
            v-if="canManage && permissionsRole?.code !== 'ROOT'"
            type="primary"
            :loading="permissionsSaving"
            @click="savePermissions"
          >
            {{ t('save') }}
          </a-button>
        </div>
      </template>
    </a-drawer>
  </Page>
</template>
