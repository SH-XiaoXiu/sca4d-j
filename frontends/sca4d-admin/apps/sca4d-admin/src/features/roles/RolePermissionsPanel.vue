<script setup lang="ts">
import type { PermissionTreeItem, PlatformRole } from '@sca4d/admin';

import { computed, ref, watch } from 'vue';

import { message } from 'ant-design-vue';
import { Save } from 'lucide-vue-next';

import { SjResourceHeader, SjSection } from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjDraftGuard } from '#/features/sj-draft-guard';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

/** 编辑选中角色的权限；目录未展示的已有权限不因保存而丢失。 */
const props = defineProps<{ /** 当前角色。 */ role: PlatformRole }>();

const { t, errorText } = useSjPresentation();

const roles = useSjControllerState(businessApplication.roles);
const session = useSjAccess();
const baseline = ref('');
const checked = ref<string[]>([]);
const retained = ref<string[]>([]);
const loading = ref(false);
const saving = ref(false);
const loadedRoleId = ref<string>();
const canManage = computed(
  () => session.value.capabilities.rolesManage && props.role.code !== 'ROOT',
);
const tree = computed(() =>
  roles.value.permissionTree.map((item) => toTree(item)),
);
const available = computed(() => grantable(roles.value.permissionTree));
const draft = computed(() =>
  [
    ...new Set([
      ...retained.value,
      ...checked.value.filter((code) => available.value.has(code)),
    ]),
  ].toSorted(),
);
const dirty = computed(
  () =>
    Boolean(baseline.value) && JSON.stringify(draft.value) !== baseline.value,
);
const { confirmDiscard } = useSjDraftGuard(
  () => dirty.value,
  () => saving.value,
);
defineExpose({ confirmDiscard });
watch(
  () => props.role.id,
  (id) => {
    void load(id);
  },
  { immediate: true },
);
/** 读取对象与权限目录，只初始化仍然选中的角色草稿。 */
async function load(id = props.role.id) {
  loading.value = true;
  loadedRoleId.value = undefined;
  baseline.value = '';
  checked.value = [];
  retained.value = [];
  try {
    await businessApplication.roles.loadPermissions(id);
    if (props.role.id !== id) return;
    const current = roles.value.permissions?.permissionCodes ?? [];
    checked.value = current.filter((code) => available.value.has(code));
    retained.value = current.filter((code) => !available.value.has(code));
    baseline.value = JSON.stringify(draft.value);
    loadedRoleId.value = id;
  } catch (error) {
    if (props.role.id === id) message.error(errorText(error));
  } finally {
    if (props.role.id === id) loading.value = false;
  }
}
/** 保存完整权限集合，失败保留用户草稿及原来的资源版本。 */
async function save() {
  if (
    !canManage.value ||
    saving.value ||
    loading.value ||
    loadedRoleId.value !== props.role.id
  )
    return;
  saving.value = true;
  try {
    await businessApplication.roles.replacePermissions(draft.value);
    baseline.value = JSON.stringify(draft.value);
    message.success(t('saveSuccess'));
  } catch (error) {
    message.error(errorText(error));
  } finally {
    saving.value = false;
  }
}
/** 严格勾选返回对象或数组时都只接收明确的字符串权限码。 */
function updateChecked(value: unknown) {
  let keys: unknown = [];
  if (Array.isArray(value)) keys = value;
  else if (value && typeof value === 'object')
    keys = Reflect.get(value, 'checked');
  checked.value = Array.isArray(keys)
    ? keys.filter((key): key is string => typeof key === 'string')
    : [];
}
/** 分组节点不可授予，不能因树组件的联动勾选扩大授权。 */
function toTree(
  item: PermissionTreeItem,
): PermissionTreeItem & { disableCheckbox: boolean } {
  return {
    ...item,
    disableCheckbox: !item.grantable,
    children: item.children.map((item) => toTree(item)),
  };
}
/** 返回服务端目录明确允许授予的节点。 */
function grantable(items: readonly PermissionTreeItem[]): Set<string> {
  const result = new Set<string>();
  for (const item of items) {
    if (item.grantable) result.add(item.code);
    for (const code of grantable(item.children)) result.add(code);
  }
  return result;
}
</script>
<template>
  <div class="flex min-h-0 flex-col gap-6 overflow-y-auto">
    <SjResourceHeader :title="role.name" :identifier="role.code">
      <template #status>
        <a-tag>{{ role.builtIn ? t('builtIn') : t('custom') }}</a-tag>
        <a-tag>
          {{ role.status === 'ACTIVE' ? t('active') : t('disabled') }}
        </a-tag>
      </template>
    </SjResourceHeader>
    <SjSection :title="t('rolePermissions')">
      <template #actions>
        <a-button
          v-if="canManage"
          type="primary"
          :disabled="loading || !dirty"
          :loading="saving"
          @click="save"
        >
          <Save :size="17" aria-hidden="true" />
          {{ t('save') }}
        </a-button>
      </template>
      <a-alert
        v-if="roles.error"
        type="warning"
        show-icon
        :message="errorText(roles.error)"
      >
        <template #action>
          <a-button @click="load()">{{ t('retry') }}</a-button>
        </template>
      </a-alert>
      <a-spin :spinning="loading">
        <a-tree
          :checked-keys="checked"
          @check="updateChecked"
          checkable
          check-strictly
          default-expand-all
          :tree-data="tree"
          :disabled="!canManage || loadedRoleId !== role.id"
        />
      </a-spin>
    </SjSection>
  </div>
</template>
