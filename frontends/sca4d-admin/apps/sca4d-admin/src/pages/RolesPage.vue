<script setup lang="ts">
import type { PlatformRole } from '@sca4d/admin';

import type { SjRowAction } from '#/components/sj';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { message } from 'ant-design-vue';
import { Pencil, Plus, Power, RefreshCw, Trash2 } from 'lucide-vue-next';

import { SjMasterDetail, SjRowActions } from '#/components/sj';
import RoleEditor from '#/features/roles/RoleEditor.vue';
import RolePermissionsPanel from '#/features/roles/RolePermissionsPanel.vue';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';
import {
  resourceQueryId,
  withoutResourceQuery,
} from '#/platform/resource-reference-routes';

const permissionPanel = ref<InstanceType<typeof RolePermissionsPanel>>();

const { t, errorText, language } = useSjPresentation();

const roles = useSjControllerState(businessApplication.roles);
const session = useSjAccess();
const route = useRoute();
const router = useRouter();
const editingRole = ref<null | PlatformRole>(null);
const editorOpen = ref(false);
const search = ref('');
const selectedId = ref<string>();
const selectionPending = ref(false);
const canManage = computed(() => session.value.capabilities.rolesManage);
const filtered = computed(() =>
  roles.value.roles.filter(
    (role) =>
      !search.value.trim() ||
      `${role.name} ${role.code}`
        .toLocaleLowerCase()
        .includes(search.value.trim().toLocaleLowerCase()),
  ),
);
const selected = computed(() =>
  roles.value.roles.find((role) => role.id === selectedId.value),
);
onMounted(() => {
  void load();
});
watch(language, () => {
  void load();
});
watch(
  () => resourceQueryId(route.query, 'roleId'),
  (id) => {
    if (id) void select(id);
  },
  { immediate: true },
);
/** 刷新角色列表并保留仍然存在的选中对象。 */
async function load() {
  try {
    await businessApplication.roles.load();
    if (!roles.value.roles.some((role) => role.id === selectedId.value))
      selectedId.value = roles.value.roles[0]?.id;
  } catch (error) {
    message.error(errorText(error));
  }
}
/** 切换对象前等待权限面板确认是否放弃草稿。 */
async function select(id: string) {
  if (selectionPending.value || id === selectedId.value) return;
  selectionPending.value = true;
  try {
    if (
      permissionPanel.value &&
      !(await permissionPanel.value.confirmDiscard())
    )
      return;
    selectedId.value = id;
  } finally {
    selectionPending.value = false;
  }
}
/** 创建和名称编辑共用独立表单流程。 */
function edit(role: null | PlatformRole = null) {
  editingRole.value = role;
  editorOpen.value = true;
}
/** 清除已经消费的角色链接。 */
async function editorClosed() {
  if (resourceQueryId(route.query, 'roleId'))
    await router.replace({
      query: withoutResourceQuery(route.query, 'roleId'),
    });
}
/** 声明角色名称、状态和删除命令，ROOT 与内置限制保持可见。 */
function actions(role: PlatformRole): readonly SjRowAction[] {
  return [
    {
      key: 'edit',
      label: t('editRoleNames'),
      icon: Pencil,
      visible: canManage.value,
      run: () => edit(role),
    },
    {
      key: 'status',
      label: role.status === 'ACTIVE' ? t('disabled') : t('active'),
      icon: Power,
      visible: canManage.value,
      disabled: role.builtIn,
      disabledReason: role.builtIn ? t('builtIn') : undefined,
      danger: role.status === 'ACTIVE',
      confirm: {
        title: `${role.name}: ${role.status === 'ACTIVE' ? t('disabled') : t('active')}`,
        okText: role.status === 'ACTIVE' ? t('disabled') : t('active'),
      },
      run: () =>
        businessApplication.roles.changeStatus(
          role,
          role.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
        ),
    },
    {
      key: 'delete',
      label: t('remove'),
      icon: Trash2,
      visible: canManage.value,
      disabled: role.builtIn,
      disabledReason: role.builtIn ? t('builtIn') : undefined,
      danger: true,
      confirm: {
        title: t('confirmDeleteRole'),
        content: role.name,
        okText: t('remove'),
      },
      run: async () => {
        await businessApplication.roles.delete(role);
        if (selectedId.value === role.id)
          selectedId.value = roles.value.roles[0]?.id;
      },
    },
  ];
}
</script>
<template>
  <SjMasterDetail :title="t('roles')">
    <template #left>
      <div class="flex h-full min-h-0 flex-col gap-3 p-3">
        <a-input-search
          v-model:value="search"
          :placeholder="t('roleName')"
          :aria-label="t('roleName')"
        />
        <div class="flex justify-between gap-2">
          <a-button
            :aria-label="t('refresh')"
            :loading="roles.phase === 'loading'"
            @click="load"
          >
            <RefreshCw :size="17" aria-hidden="true" />
          </a-button>
          <a-button v-if="canManage" type="primary" @click="edit()">
            <Plus :size="17" aria-hidden="true" />
            {{ t('createRole') }}
          </a-button>
        </div>
        <a-alert
          v-if="roles.error"
          type="warning"
          :message="errorText(roles.error)"
        />
        <div class="min-h-0 flex-1 overflow-y-auto">
          <div
            v-for="role in filtered"
            :key="role.id"
            class="mb-1 rounded-md p-2"
            :class="role.id === selectedId ? 'bg-accent' : ''"
          >
            <button
              type="button"
              class="block w-full text-left focus-visible:ring-2 focus-visible:ring-ring"
              :aria-pressed="role.id === selectedId"
              @click="select(role.id)"
            >
              <strong class="block truncate">{{ role.name }}</strong>
              <span class="text-xs text-muted-foreground">{{ role.code }}</span>
            </button>
            <SjRowActions :actions="actions(role)" />
          </div>
          <a-empty
            v-if="filtered.length === 0 && roles.phase !== 'loading'"
            :description="t('noData')"
          />
        </div>
      </div>
    </template>
    <template #mobile>
      <div class="flex flex-wrap gap-2">
        <a-select
          :value="selectedId"
          class="min-w-48 flex-1"
          :aria-label="t('roles')"
          :options="
            filtered.map((role) => ({ label: role.name, value: role.id }))
          "
          @change="select(String($event))"
        />
        <a-button :aria-label="t('refresh')" @click="load">
          <RefreshCw :size="17" aria-hidden="true" />
        </a-button>
        <a-button v-if="canManage" type="primary" @click="edit()">
          <Plus :size="17" aria-hidden="true" />
          {{ t('createRole') }}
        </a-button>
        <SjRowActions v-if="selected" :actions="actions(selected)" />
      </div>
    </template>
    <RolePermissionsPanel
      v-if="selected"
      ref="permissionPanel"
      :role="selected"
    />
    <a-empty v-else :description="t('noData')" />
  </SjMasterDetail>
  <RoleEditor
    v-model:open="editorOpen"
    :role="editingRole"
    @closed="editorClosed"
  />
</template>
