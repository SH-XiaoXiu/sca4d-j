<script setup lang="ts">
defineOptions({ name: "RoleManagementPage" });

import {
  createIdempotencyKey,
  formatDateTime,
  isApiError,
  normalizePageData,
  type DictionaryOptionView,
  type ManagedRolePermissionView,
  type ManagedRoleView,
  type RoleApi,
  type RoleStatus
} from "@sca4d/admin-common";
import { Eye, KeyRound, Pencil, CirclePlus, Filter, RotateCcw, Search, ShieldCheck, Trash2 } from "lucide-vue-next";
import { message, Modal } from "ant-design-vue";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import { platformRoleApi, platformTenantRoleApi, systemDictionaryApi, tenantRoleApi } from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import { useSessionStore } from "@/stores/session";

interface PermissionTreeNode extends ManagedRolePermissionView {
  children: PermissionTreeNode[];
}

const auth = useSessionStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loading = ref(false);
const submitting = ref(false);
const permissionLoading = ref(false);
const records = ref<ManagedRoleView[]>([]);
const permissions = ref<ManagedRolePermissionView[]>([]);
const statusOptions = ref<DictionaryOptionView[]>([]);
const total = ref(0);
const filterOpen = ref(false);
const editorOpen = ref(false);
const editorMode = ref<"create" | "basic" | "permissions">("create");
const editing = ref<ManagedRoleView | null>(null);
const detailOpen = computed(() => typeof route.params.roleId === "string" && Boolean(route.params.roleId));
const detail = ref<ManagedRoleView | null>(null);
const checkedPermissionIds = ref<string[]>([]);
const query = reactive({
  page: 1,
  size: 20,
  search: "",
  status: undefined as RoleStatus | undefined
});
const form = reactive({
  code: "",
  name: "",
  status: "ACTIVE" as RoleStatus
});
let listController: AbortController | null = null;
let permissionController: AbortController | null = null;
let optionController: AbortController | null = null;

const tenantId = computed(() => String(route.params.tenantId ?? ""));
const scope = computed<"platform" | "platform-tenant" | "tenant">(() => {
  if (String(route.name).startsWith("platform-tenant-role")) return "platform-tenant";
  if (String(route.name).startsWith("tenant-role")) return "tenant";
  return "platform";
});
const api = computed<RoleApi>(() => {
  if (scope.value === "platform-tenant") return platformTenantRoleApi(tenantId.value);
  if (scope.value === "tenant") return tenantRoleApi(tenantId.value);
  return platformRoleApi;
});
const managePermission = computed(() => {
  if (scope.value === "platform-tenant") return "platform:tenants:roles:manage";
  if (scope.value === "tenant") return "tenant:roles:manage";
  return "platform:roles:manage";
});
const canManage = computed(() => auth.hasPermission(managePermission.value));
const pageTitle = computed(() => t(scope.value === "platform" ? "role.platformTitle" : "role.tenantTitle"));
const columns = computed(() => [
  { title: t("role.name"), key: "role", width: 280 },
  { title: t("role.kind"), key: "kind", width: 120 },
  { title: t("role.status"), key: "status", width: 100 },
  { title: t("role.permissionCount"), key: "permissionCount", width: 110 },
  { title: t("role.principalCount"), dataIndex: "principalCount", key: "principalCount", width: 110 },
  { title: t("common.updatedAt"), key: "updatedAt", width: 180 },
  { title: t("common.actions"), key: "actions", width: 310, fixed: "right" as const }
]);
const permissionTree = computed<PermissionTreeNode[]>(() => {
  const nodes = new Map<string, PermissionTreeNode>();
  for (const permission of permissions.value) {
    nodes.set(permission.permissionId, { ...permission, children: [] });
  }
  const roots: PermissionTreeNode[] = [];
  for (const node of nodes.values()) {
    const parent = node.parentId ? nodes.get(node.parentId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  }
  const sort = (items: PermissionTreeNode[]): PermissionTreeNode[] => items
    .sort((left, right) => left.sortOrder - right.sortOrder || left.code.localeCompare(right.code))
    .map((item) => ({ ...item, children: sort(item.children) }));
  return sort(roots);
});
const editorTitle = computed(() => {
  if (editorMode.value === "permissions") return t("role.editPermissions");
  return t(editorMode.value === "create" ? "role.create" : "role.editBasic");
});

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function statusLabel(status: RoleStatus): string {
  return statusOptions.value.find((option) => option.value === status)?.label ?? t("common.unavailable");
}

function statusColor(status: RoleStatus): string {
  const tone = statusOptions.value.find((option) => option.value === status)?.tone;
  if (tone === "SUCCESS") return "success";
  if (tone === "WARNING") return "warning";
  if (tone === "DANGER") return "error";
  if (tone === "INFO") return "processing";
  return "default";
}

function listRoute(): string {
  if (scope.value === "platform-tenant") return `/platform/tenants/${tenantId.value}/roles`;
  if (scope.value === "tenant") return `/tenant/tenants/${tenantId.value}/roles`;
  return "/platform/roles";
}

function detailRoute(roleId: string): string {
  return `${listRoute()}/${roleId}`;
}

async function load(): Promise<void> {
  listController?.abort();
  const requestController = new AbortController();
  listController = requestController;
  loading.value = true;
  try {
    const page = normalizePageData(await api.value.page({
      page: query.page,
      size: query.size,
      ...(query.search.trim() ? { search: query.search.trim() } : {}),
      ...(query.status ? { status: query.status } : {})
    }, requestController.signal));
    if (requestController.signal.aborted || listController !== requestController) return;
    records.value = page.records;
    total.value = page.total;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "role.loadFailed"));
    }
  } finally {
    if (listController === requestController) loading.value = false;
  }
}

async function loadPermissions(): Promise<void> {
  permissionController?.abort();
  const requestController = new AbortController();
  permissionController = requestController;
  permissionLoading.value = true;
  try {
    const result = await api.value.permissions(requestController.signal);
    if (requestController.signal.aborted || permissionController !== requestController) return;
    permissions.value = result;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "role.permissionLoadFailed"));
    }
  } finally {
    if (permissionController === requestController) permissionLoading.value = false;
  }
}

async function loadStatusOptions(): Promise<void> {
  optionController?.abort();
  const requestController = new AbortController();
  optionController = requestController;
  try {
    const selections = await systemDictionaryApi.options(["iam.role-status"], requestController.signal);
    if (requestController.signal.aborted || optionController !== requestController) return;
    statusOptions.value = selections.find((selection) => selection.code === "iam.role-status")?.options ?? [];
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "role.optionsLoadFailed"));
    }
  }
}

function resetQuery(): void {
  Object.assign(query, { page: 1, search: "", status: undefined });
  void load();
}

function openCreate(): void {
  editorMode.value = "create";
  editing.value = null;
  Object.assign(form, { code: "", name: "", status: "ACTIVE" });
  checkedPermissionIds.value = [];
  editorOpen.value = true;
}

function openBasic(role: ManagedRoleView): void {
  editorMode.value = "basic";
  editing.value = role;
  Object.assign(form, { code: role.code, name: role.name, status: role.status });
  editorOpen.value = true;
}

function openPermissions(role: ManagedRoleView): void {
  editorMode.value = "permissions";
  editing.value = role;
  checkedPermissionIds.value = role.permissions.map((permission) => permission.permissionId);
  editorOpen.value = true;
}

async function save(): Promise<void> {
  if (editorMode.value !== "permissions" && !form.name.trim()) {
    message.warning(t("role.nameRequired"));
    return;
  }
  if (editorMode.value === "create" && !form.code.trim()) {
    message.warning(t("role.codeRequired"));
    return;
  }
  submitting.value = true;
  try {
    if (editorMode.value === "create") {
      const roleId = await api.value.create({
        code: form.code.trim(),
        name: form.name.trim(),
        permissionIds: checkedPermissionIds.value,
        idempotencyKey: createIdempotencyKey()
      });
      message.success(t("role.createSuccess"));
      editorOpen.value = false;
      await load();
      await router.push(detailRoute(roleId));
      return;
    }
    if (!editing.value) return;
    if (editorMode.value === "basic") {
      await api.value.update(editing.value.roleId, {
        name: form.name.trim(),
        status: form.status,
        version: editing.value.version
      });
      message.success(t("role.updateSuccess"));
    } else {
      await api.value.replacePermissions(
        editing.value.roleId,
        checkedPermissionIds.value,
        editing.value.version
      );
      message.success(t("role.permissionSuccess"));
    }
    editorOpen.value = false;
    await load();
    if (detail.value?.roleId === editing.value.roleId) await loadDetail(editing.value.roleId);
  } catch (error) {
    if (isApiError(error) && error.httpStatus === 409) {
      const roleId = editing.value?.roleId;
      await load();
      if (roleId) {
        try {
          editing.value = await api.value.detail(roleId);
        } catch {
          editing.value = null;
          editorOpen.value = false;
        }
      }
      message.warning(t("role.conflict"));
      return;
    }
    message.error(errorMessage(error, "role.saveFailed"));
  } finally {
    submitting.value = false;
  }
}

async function openDetail(role: ManagedRoleView): Promise<void> {
  await router.push(detailRoute(role.roleId));
}

async function loadDetail(roleId: string): Promise<void> {
  try {
    detail.value = await api.value.detail(roleId);
  } catch (error) {
    message.error(errorMessage(error, "role.detailLoadFailed"));
    await router.replace(listRoute());
  }
}

async function closeDetail(): Promise<void> {
  detail.value = null;
  await router.push(listRoute());
}

function remove(role: ManagedRoleView): void {
  if (role.builtIn || role.principalCount > 0) return;
  Modal.confirm({
    title: t("role.deleteTitle", { name: role.name }),
    content: t("role.deleteWarning"),
    okText: t("common.delete"),
    cancelText: t("common.cancel"),
    okType: "danger",
    onOk: async () => {
      try {
        await api.value.delete(role.roleId, role.version, createIdempotencyKey());
        message.success(t("role.deleteSuccess"));
        if (detail.value?.roleId === role.roleId) await closeDetail();
        await load();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 409) {
          await load();
          message.warning(t("role.conflict"));
          return;
        }
        message.error(errorMessage(error, "role.deleteFailed"));
      }
    }
  });
}

function handlePage(page: number, size: number): void {
  query.page = page;
  query.size = size;
  void load();
}

watch(
  () => [route.name, route.params.tenantId, route.params.roleId],
  async ([, , roleId]) => {
    if (typeof roleId === "string" && roleId) await loadDetail(roleId);
    else {
      detail.value = null;
    }
  }
);

onMounted(async () => {
  await Promise.all([load(), loadPermissions(), loadStatusOptions()]);
  if (typeof route.params.roleId === "string") await loadDetail(route.params.roleId);
});

onBeforeUnmount(() => {
  listController?.abort();
  permissionController?.abort();
  optionController?.abort();
});
</script>

<template>
  <div class="page-container">
    <PageHeader
      :title="pageTitle"
      :description="t('role.description')"
    >
      <template #actions>
        <a-button @click="filterOpen = true">
          <Filter :size="16" />{{ t("common.filter") }}
        </a-button>
        <a-button @click="resetQuery">
          <RotateCcw :size="16" />{{ t("common.reset") }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          @click="openCreate"
        >
          <CirclePlus :size="16" />{{ t("role.create") }}
        </a-button>
      </template>
    </PageHeader>

    <a-form
      class="filter-panel desktop-only"
      layout="inline"
      @submit.prevent="query.page = 1; load()"
    >
      <a-form-item :label="t('role.search')">
        <a-input
          v-model:value="query.search"
          allow-clear
          :placeholder="t('role.searchPlaceholder')"
        >
          <template #prefix>
            <Search :size="15" />
          </template>
        </a-input>
      </a-form-item>
      <a-form-item :label="t('role.status')">
        <a-select
          v-model:value="query.status"
          allow-clear
          :placeholder="t('common.allStatuses')"
          class="role-status-filter"
        >
          <a-select-option
            v-for="option in statusOptions"
            :key="option.value"
            :value="option.value"
            :disabled="option.disabled"
          >
            {{ option.label }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button
          type="primary"
          html-type="submit"
        >
          {{ t("common.query") }}
        </a-button>
      </a-form-item>
    </a-form>

    <section class="surface desktop-only role-table-panel">
      <a-table
        row-key="roleId"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="{
          current: query.page,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          showTotal: (value: number) => t('common.total', { total: value })
        }"
        :scroll="{ x: 1210 }"
        @change="(pagination: { current?: number; pageSize?: number }) => handlePage(pagination.current ?? 1, pagination.pageSize ?? 20)"
      >
        <template #bodyCell="{ column, record }: { column: { key: string }; record: ManagedRoleView }">
          <template v-if="column.key === 'role'">
            <button
              type="button"
              class="role-name-button"
              @click="openDetail(record)"
            >
              <ShieldCheck :size="18" />
              <span><strong>{{ record.name }}</strong><code>{{ record.code }}</code></span>
            </button>
          </template>
          <template v-else-if="column.key === 'kind'">
            <a-tag :color="record.builtIn ? 'processing' : 'default'">
              {{ t(record.builtIn ? "role.builtIn" : "role.custom") }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'permissionCount'">
            {{ record.permissionCount }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <div class="role-row-actions">
              <RowActionButton
                :label="t('common.details')"
                @click="openDetail(record)"
              >
                <Eye :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                :label="t('common.edit')"
                @click="openBasic(record)"
              >
                <Pencil :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                :label="record.builtIn ? t('role.builtInPermissionHint') : t('role.permissions')"
                :disabled="record.builtIn"
                @click="openPermissions(record)"
              >
                <KeyRound :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                :label="record.builtIn ? t('role.builtInDeleteHint') : record.principalCount > 0 ? t('role.inUseDeleteHint') : t('common.delete')"
                danger
                :disabled="record.builtIn || record.principalCount > 0"
                @click="remove(record)"
              >
                <Trash2 :size="17" />
              </RowActionButton>
            </div>
          </template>
        </template>
      </a-table>
    </section>

    <div class="mobile-only">
      <a-spin :spinning="loading">
        <a-empty
          v-if="!loading && records.length === 0"
          :description="t('role.empty')"
        />
        <div class="mobile-card-list">
          <article
            v-for="role in records"
            :key="role.roleId"
            class="mobile-record"
          >
            <div class="mobile-record__header">
              <div>
                <div class="mobile-record__title">
                  {{ role.name }}
                </div>
                <code>{{ role.code }}</code>
              </div>
              <a-tag :color="statusColor(role.status)">
                {{ statusLabel(role.status) }}
              </a-tag>
            </div>
            <div class="mobile-record__meta">
              <span>{{ t("role.kind") }}</span><strong>{{ t(role.builtIn ? "role.builtIn" : "role.custom") }}</strong>
              <span>{{ t("role.permissionCount") }}</span><strong>{{ role.permissionCount }}</strong>
              <span>{{ t("role.principalCount") }}</span><strong>{{ role.principalCount }}</strong>
            </div>
            <div class="mobile-record__actions">
              <RowActionButton
                :label="t('common.details')"
                @click="openDetail(role)"
              >
                <Eye :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                :label="t('common.edit')"
                @click="openBasic(role)"
              >
                <Pencil :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                :label="t('role.permissions')"
                :disabled="role.builtIn"
                @click="openPermissions(role)"
              >
                <KeyRound :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                danger
                :label="t('common.delete')"
                :disabled="role.builtIn || role.principalCount > 0"
                @click="remove(role)"
              >
                <Trash2 :size="17" />
              </RowActionButton>
            </div>
          </article>
        </div>
      </a-spin>
      <a-pagination
        v-if="total > query.size"
        simple
        :current="query.page"
        :page-size="query.size"
        :total="total"
        class="mobile-pagination"
        @change="(page: number) => handlePage(page, query.size)"
      />
    </div>

    <a-drawer
      :open="detailOpen"
      :title="detail ? `${detail.name} · ${t('common.details')}` : t('common.details')"
      :width="680"
      class="responsive-drawer"
      @close="closeDetail"
    >
      <template v-if="detail">
        <a-descriptions
          :column="1"
          bordered
          size="small"
        >
          <a-descriptions-item :label="t('role.name')">
            {{ detail.name }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('role.code')">
            <code>{{ detail.code }}</code>
          </a-descriptions-item>
          <a-descriptions-item :label="t('role.kind')">
            {{ t(detail.builtIn ? "role.builtIn" : "role.custom") }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('role.status')">
            {{ statusLabel(detail.status) }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('role.principalCount')">
            {{ detail.principalCount }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('common.updatedAt')">
            {{ formatDateTime(detail.updatedAt) }}
          </a-descriptions-item>
        </a-descriptions>
        <section class="role-detail-permissions">
          <h2>{{ t("role.permissions") }}</h2>
          <a-empty
            v-if="detail.permissions.length === 0"
            :description="t('role.noPermissions')"
          />
          <a-list
            v-else
            size="small"
            bordered
            :data-source="detail.permissions"
          >
            <template #renderItem="{ item }: { item: ManagedRolePermissionView }">
              <a-list-item><span>{{ item.name }}</span><code>{{ item.code }}</code></a-list-item>
            </template>
          </a-list>
        </section>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="filterOpen"
      :title="t('role.filterTitle')"
      :width="360"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('role.search')">
          <a-input
            v-model:value="query.search"
            allow-clear
            :placeholder="t('role.searchPlaceholder')"
          />
        </a-form-item>
        <a-form-item :label="t('role.status')">
          <a-select
            v-model:value="query.status"
            allow-clear
            :placeholder="t('common.allStatuses')"
          >
            <a-select-option
              v-for="option in statusOptions"
              :key="option.value"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer-actions">
          <a-button @click="resetQuery(); filterOpen = false">
            {{ t("common.reset") }}
          </a-button>
          <a-button
            type="primary"
            @click="query.page = 1; filterOpen = false; load()"
          >
            {{ t("common.query") }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="editorOpen"
      :title="editorTitle"
      :width="720"
    >
      <a-form layout="vertical">
        <template v-if="editorMode !== 'permissions'">
          <a-form-item
            :label="t('role.code')"
            :required="editorMode === 'create'"
          >
            <a-input
              v-model:value="form.code"
              :disabled="editorMode !== 'create'"
              :placeholder="t('role.codePlaceholder')"
              autocomplete="off"
            />
            <div class="form-hint">
              {{ t("role.codeHint") }}
            </div>
          </a-form-item>
          <a-form-item
            :label="t('role.name')"
            required
          >
            <a-input
              v-model:value="form.name"
              :placeholder="t('role.namePlaceholder')"
              autocomplete="off"
            />
          </a-form-item>
          <a-form-item
            v-if="editorMode === 'basic'"
            :label="t('role.status')"
          >
            <a-radio-group v-model:value="form.status">
              <a-radio
                v-for="option in statusOptions"
                :key="option.value"
                :value="option.value"
                :disabled="option.disabled"
              >
                {{ option.label }}
              </a-radio>
            </a-radio-group>
          </a-form-item>
        </template>
        <a-form-item
          v-if="editorMode !== 'basic'"
          :label="t('role.permissions')"
        >
          <a-spin :spinning="permissionLoading">
            <div class="permission-editor">
              <a-tree
                v-if="permissionTree.length"
                v-model:checked-keys="checkedPermissionIds"
                checkable
                default-expand-all
                :tree-data="permissionTree"
                :field-names="{ title: 'name', key: 'permissionId', children: 'children' }"
              >
                <template #title="node">
                  <span class="permission-title"><span>{{ node.name }}</span><code>{{ node.code }}</code></span>
                </template>
              </a-tree>
              <a-empty
                v-else
                :description="t('role.noPermissionOptions')"
              />
            </div>
          </a-spin>
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer-actions">
          <a-button @click="editorOpen = false">
            {{ t("common.cancel") }}
          </a-button>
          <a-button
            type="primary"
            :loading="submitting"
            @click="save"
          >
            {{ t(editorMode === "create" ? "common.create" : "common.save") }}
          </a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.role-table-panel { padding: 0; overflow: hidden; }
.role-status-filter { width: 160px; }
.role-name-button { display: flex; min-height: 44px; align-items: center; padding: 4px; gap: 10px; color: var(--app-primary); background: transparent; border: 0; cursor: pointer; text-align: left; }
.role-name-button > span { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.role-name-button strong { color: var(--app-text); }
.role-name-button code, .mobile-record code { color: var(--app-text-secondary); font-size: 12px; }
.role-name-button:focus-visible { outline: 2px solid var(--app-primary); outline-offset: 2px; border-radius: var(--app-radius); }
.role-row-actions { display: flex; align-items: center; justify-content: flex-end; }
.role-detail-permissions { margin-top: 20px; }
.role-detail-permissions h2 { margin: 0 0 12px; font-size: 15px; }
.role-detail-permissions .ant-list-item { display: flex; min-height: 44px; justify-content: space-between; gap: 12px; }
.role-detail-permissions code { color: var(--app-text-secondary); overflow-wrap: anywhere; }
.permission-editor { max-height: 420px; padding: 12px; overflow: auto; background: var(--app-surface-muted); border: 1px solid var(--app-border); border-radius: var(--app-radius); }
.permission-title { display: inline-flex; flex-wrap: wrap; gap: 8px; }
.permission-title code { color: var(--app-text-secondary); font-size: 11px; }
.form-hint { margin-top: 6px; color: var(--app-text-secondary); font-size: 12px; }
.drawer-footer-actions { display: flex; justify-content: flex-end; gap: 8px; }
@media (max-width: 900px) {
  .drawer-footer-actions { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); }
  .drawer-footer-actions .ant-btn { min-height: 44px; width: 100%; }
}
</style>
