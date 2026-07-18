<script setup lang="ts">
defineOptions({ name: "PlatformTenantsPage" });

import {
  createIdempotencyKey,
  formatDateTime,
  isApiError,
  normalizePageData,
  type DictionaryOptionView,
  type TenantStatus,
  type TenantType,
  type TenantView
} from "@sca4d/admin-common";
import { BookOpenText, Building2, CirclePlus, Filter, Pencil, Power, RotateCcw, Search, ShieldCheck, Trash2, Users } from "lucide-vue-next";
import { message, Modal, type TablePaginationConfig } from "ant-design-vue";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter, type LocationQuery } from "vue-router";
import { platformTenantApi, systemDictionaryApi } from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import QueryToolbar from "@/components/page/QueryToolbar.vue";
import ResourceLink from "@/components/data-display/ResourceLink.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import { routePositiveInteger, routeText, useRouteQueryState } from "@/composables/useRouteQueryState";
import { useSessionStore } from "@/stores/session";

interface TenantPageQuery {
  page: number;
  size: number;
  search: string;
  status: TenantStatus | undefined;
}

const auth = useSessionStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loading = ref(false);
const optionLoading = ref(false);
const submitting = ref(false);
const records = ref<TenantView[]>([]);
const total = ref(0);
const editorOpen = ref(false);
const filterOpen = ref(false);
const detailTenant = ref<TenantView | null>(null);
const editing = ref<TenantView | null>(null);
const queryState = useRouteQueryState<TenantPageQuery>({
  decode(value: LocationQuery) {
    const status = routeText(value, "status");
    return {
      page: routePositiveInteger(value, "page", 1),
      size: routePositiveInteger(value, "size", 20),
      search: routeText(value, "search") ?? "",
      status: status === "ACTIVE" || status === "DISABLED" ? status : undefined
    };
  },
  encode(value) {
    return {
      ...(value.page === 1 ? {} : { page: String(value.page) }),
      ...(value.size === 20 ? {} : { size: String(value.size) }),
      ...(value.search ? { search: value.search } : {}),
      ...(value.status ? { status: value.status } : {})
    };
  }
}, load);
const query = queryState.draft;
const form = reactive({ code: "", name: "" });
const statusOptions = ref<DictionaryOptionView[]>([]);
const tenantTypeOptions = ref<DictionaryOptionView[]>([]);
let controller: AbortController | null = null;
let optionController: AbortController | null = null;
let detailController: AbortController | null = null;

const columns = computed(() => [
  { title: t("tenant.tenant"), key: "tenant", width: 260 },
  { title: t("tenant.type"), dataIndex: "tenantType", key: "tenantType", width: 110 },
  { title: t("tenant.status"), key: "status", width: 92 },
  { title: t("common.updatedAt"), dataIndex: "updatedAt", key: "updatedAt", width: 172 },
  { title: t("common.actions"), key: "actions", width: 390, fixed: "right" as const }
]);

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function statusLabel(status: TenantStatus): string {
  return statusOptions.value.find((option) => option.value === status)?.label ?? t("common.unavailable");
}

function tenantTypeLabel(tenantType: TenantType): string {
  return tenantTypeOptions.value.find((option) => option.value === tenantType)?.label ?? t("common.unavailable");
}

function statusColor(status: TenantStatus): string {
  const tone = statusOptions.value.find((option) => option.value === status)?.tone;
  if (tone === "SUCCESS") return "success";
  if (tone === "WARNING") return "warning";
  if (tone === "DANGER") return "error";
  if (tone === "INFO") return "processing";
  return "default";
}

async function loadOptions(): Promise<void> {
  optionController?.abort();
  const requestController = new AbortController();
  optionController = requestController;
  optionLoading.value = true;
  try {
    const selections = await systemDictionaryApi.options(
      ["iam.tenant-status", "iam.tenant-type"],
      requestController.signal
    );
    if (requestController.signal.aborted || optionController !== requestController) return;
    statusOptions.value = selections.find((selection) => selection.code === "iam.tenant-status")?.options ?? [];
    tenantTypeOptions.value = selections.find((selection) => selection.code === "iam.tenant-type")?.options ?? [];
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "tenant.optionsLoadFailed"));
    }
  } finally {
    if (optionController === requestController) optionLoading.value = false;
  }
}

async function load(value: Readonly<TenantPageQuery>): Promise<void> {
  controller?.abort();
  const requestController = new AbortController();
  controller = requestController;
  loading.value = true;
  try {
    const base = { page: value.page, size: value.size, search: value.search };
    const page = normalizePageData(
      await platformTenantApi.page(value.status ? { ...base, status: value.status } : base, requestController.signal)
    );
    if (requestController.signal.aborted || controller !== requestController) return;
    records.value = page.records;
    total.value = page.total;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "tenant.loadFailed"));
    }
  } finally {
    if (controller === requestController) loading.value = false;
  }
}

function openCreate(): void {
  editing.value = null;
  Object.assign(form, { code: "", name: "" });
  editorOpen.value = true;
}

function openEdit(tenant: TenantView): void {
  editing.value = tenant;
  Object.assign(form, { code: tenant.code, name: tenant.name });
  editorOpen.value = true;
}

async function submit(): Promise<void> {
  if (!form.name.trim() || !form.code.trim()) {
    message.warning(t("tenant.required"));
    return;
  }
  submitting.value = true;
  try {
    if (editing.value) {
      await platformTenantApi.update(
        editing.value.tenantId,
        form.code.trim(),
        form.name.trim(),
        editing.value.version
      );
      message.success(t("tenant.updated"));
    } else {
      await platformTenantApi.create({
        code: form.code.trim(),
        name: form.name.trim(),
        tenantType: "DEFAULT",
        idempotencyKey: createIdempotencyKey()
      });
      message.success(t("tenant.created"));
    }
    editorOpen.value = false;
    await queryState.reload();
  } catch (error) {
    if (editing.value && isApiError(error) && error.httpStatus === 409) {
      const editingTenantId = editing.value.tenantId;
      const draft = { ...form };
      await queryState.reload();
      try {
        editing.value = await platformTenantApi.detail(editingTenantId);
        Object.assign(form, draft);
        message.warning(t("tenant.conflict"));
      } catch (detailError) {
        editorOpen.value = false;
        message.error(errorMessage(detailError, "tenant.detailLoadFailed"));
      }
      return;
    }
    message.error(errorMessage(error, editing.value ? "tenant.updateFailed" : "tenant.createFailed"));
  } finally {
    submitting.value = false;
  }
}

function changeStatus(tenant: TenantView): void {
  const next = tenant.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  const action = t(next === "ACTIVE" ? "tenant.enableAction" : "tenant.disableAction");
  Modal.confirm({
    title: t("tenant.statusConfirm", { action, name: tenant.name }),
    content:
      next === "DISABLED"
        ? t("tenant.disableWarning")
        : t("tenant.enableWarning"),
    okText: action,
    okType: next === "DISABLED" ? "danger" : "primary",
    cancelText: t("common.cancel"),
    async onOk() {
      try {
        await platformTenantApi.changeStatus(tenant.tenantId, next, tenant.version);
        message.success(t("tenant.statusChanged", { action }));
        await queryState.reload();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 409) {
          await queryState.reload();
          message.warning(t("tenant.statusConflict", { action }));
          return;
        }
        message.error(errorMessage(error, "tenant.statusFailed"));
        throw error;
      }
    }
  });
}

function deleteTenant(tenant: TenantView): void {
  Modal.confirm({
    title: t("tenant.deleteTitle", { name: tenant.name }),
    content: t("tenant.deleteWarning"),
    okText: t("tenant.delete"),
    okType: "danger",
    cancelText: t("common.cancel"),
    async onOk() {
      try {
        await platformTenantApi.delete(tenant.tenantId, tenant.version, createIdempotencyKey());
        if (detailTenant.value?.tenantId === tenant.tenantId) {
          detailTenant.value = null;
          await router.push({ name: "platform-tenants", query: route.query });
        }
        message.success(t("tenant.deleted"));
        await queryState.reload();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 409) {
          await queryState.reload();
          message.warning(t("tenant.deleteConflict"));
          return;
        }
        message.error(errorMessage(error, "tenant.deleteFailed"));
        throw error;
      }
    }
  });
}

function applyQuery(): void {
  query.page = 1;
  filterOpen.value = false;
  void queryState.apply();
}

function resetQuery(): void {
  filterOpen.value = false;
  void queryState.reset();
}

function changePage(page: number, size: number): void {
  void queryState.update({ page, size });
}

async function syncRoutedTenant(tenantId: string | undefined): Promise<void> {
  detailController?.abort();
  if (!tenantId) {
    detailTenant.value = null;
    return;
  }
  const requestController = new AbortController();
  detailController = requestController;
  try {
    const tenant = await platformTenantApi.detail(tenantId, requestController.signal);
    if (requestController.signal.aborted || detailController !== requestController) return;
    detailTenant.value = tenant;
  } catch (error) {
    if (requestController.signal.aborted || detailController !== requestController) return;
    message.error(errorMessage(error, "tenant.detailLoadFailed"));
    await router.replace({ name: "platform-tenants", query: route.query });
  }
}

function closeDetail(): void {
  if (route.name === "platform-tenant-detail") {
    void router.push({ name: "platform-tenants", query: route.query });
  }
}

watch(
  () => (typeof route.params.tenantId === "string" ? route.params.tenantId : undefined),
  (tenantId) => void syncRoutedTenant(tenantId),
  { immediate: true }
);
onMounted(() => {
  void loadOptions();
});
onBeforeUnmount(() => {
  controller?.abort();
  optionController?.abort();
  detailController?.abort();
});
</script>

<template>
  <div>
    <PageHeader :title="t('route.platformTenants')">
      <template #actions>
        <a-button
          class="mobile-only"
          @click="filterOpen = true"
        >
          <Filter :size="16" />{{ t("common.filter") }}
        </a-button>
        <a-button
          v-if="auth.hasPermission('platform:tenants:create')"
          type="primary"
          @click="openCreate"
        >
          <CirclePlus :size="16" />
          {{ t("tenant.create") }}
        </a-button>
      </template>
    </PageHeader>

    <section class="page-panel management-panel">
      <QueryToolbar class="desktop-only">
        <template #search>
          <a-input
            v-model:value="query.search"
            allow-clear
            :placeholder="t('tenant.searchPlaceholder')"
            @press-enter="applyQuery"
          >
            <template #prefix>
              <Search :size="15" />
            </template>
          </a-input>
        </template>
        <template #filters>
          <a-select
            v-model:value="query.status"
            allow-clear
            :placeholder="t('common.allStatuses')"
            :loading="optionLoading"
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
        </template>
        <template #actions>
          <a-button
            type="primary"
            @click="applyQuery"
          >
            <Search :size="16" />{{ t("common.query") }}
          </a-button>
          <a-button @click="resetQuery">
            <RotateCcw :size="16" />{{ t("common.reset") }}
          </a-button>
        </template>
      </QueryToolbar>

      <a-table
        class="desktop-only"
        row-key="tenantId"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="{ current: query.page, pageSize: query.size, total, showSizeChanger: true, showTotal: (value: number) => t('common.total', { total: value }) }"
        :scroll="{ x: 900 }"
        @change="(pagination: TablePaginationConfig) => changePage(pagination.current ?? query.page, pagination.pageSize ?? query.size)"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'tenant'">
            <div class="tenant-cell">
              <span class="tenant-icon"><Building2 :size="18" /></span>
              <div>
                <ResourceLink
                  :label="record.name"
                  :to="{ name: 'platform-tenant-detail', params: { tenantId: record.tenantId }, query: route.query }"
                />
                <span>{{ record.code }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'tenantType'">
            {{ tenantTypeLabel(record.tenantType) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space :size="4">
              <RowActionButton
                v-if="auth.hasPermission('platform:tenants:members:read')"
                :label="t('tenant.members')"
                @click="router.push(`/platform/tenants/${record.tenantId}/members`)"
              >
                <Users :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="auth.hasPermission('platform:tenants:roles:read')"
                :label="t('tenant.roles')"
                @click="router.push(`/platform/tenants/${record.tenantId}/roles`)"
              >
                <ShieldCheck :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="auth.hasPermission('platform:tenants:dictionaries:read')"
                :label="t('tenant.dictionaries')"
                @click="router.push(`/platform/tenants/${record.tenantId}/dictionaries`)"
              >
                <BookOpenText :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="auth.hasPermission('platform:tenants:update')"
                :label="t('common.edit')"
                @click="openEdit(record)"
              >
                <Pencil :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="auth.hasPermission('platform:tenants:update')"
                :label="t(record.status === 'ACTIVE' ? 'common.disabled' : 'common.active')"
                @click="changeStatus(record)"
              >
                <Power :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="auth.hasPermission('platform:tenants:delete')"
                :label="t('tenant.delete')"
                danger
                @click="deleteTenant(record)"
              >
                <Trash2 :size="17" />
              </RowActionButton>
            </a-space>
          </template>
        </template>
      </a-table>

      <div class="mobile-only">
        <a-spin :spinning="loading">
          <a-empty
            v-if="!loading && records.length === 0"
            :description="t('tenant.empty')"
          />
          <div
            v-else
            class="mobile-card-list"
          >
            <article
              v-for="record in records"
              :key="record.tenantId"
              class="mobile-record"
            >
              <div class="mobile-record__header">
                <div class="mobile-record__title">
                  <ResourceLink
                    :label="record.name"
                    :to="{ name: 'platform-tenant-detail', params: { tenantId: record.tenantId }, query: route.query }"
                  />
                </div>
                <a-tag :color="statusColor(record.status)">
                  {{ statusLabel(record.status) }}
                </a-tag>
              </div>
              <div class="mobile-record__meta">
                <span>{{ t("tenant.code") }}</span><strong>{{ record.code }}</strong>
                <span>{{ t("tenant.type") }}</span><strong>{{ tenantTypeLabel(record.tenantType) }}</strong>
                <span>{{ t("common.updatedAt") }}</span><strong>{{ formatDateTime(record.updatedAt) }}</strong>
              </div>
              <div class="mobile-record__actions">
                <RowActionButton
                  v-if="auth.hasPermission('platform:tenants:members:read')"
                  :label="t('tenant.members')"
                  @click="router.push(`/platform/tenants/${record.tenantId}/members`)"
                >
                  <Users :size="17" />
                </RowActionButton>
                <RowActionButton
                  v-if="auth.hasPermission('platform:tenants:roles:read')"
                  :label="t('tenant.roles')"
                  @click="router.push(`/platform/tenants/${record.tenantId}/roles`)"
                >
                  <ShieldCheck :size="17" />
                </RowActionButton>
                <RowActionButton
                  v-if="auth.hasPermission('platform:tenants:dictionaries:read')"
                  :label="t('tenant.dictionaries')"
                  @click="router.push(`/platform/tenants/${record.tenantId}/dictionaries`)"
                >
                  <BookOpenText :size="17" />
                </RowActionButton>
                <RowActionButton
                  v-if="auth.hasPermission('platform:tenants:update')"
                  :label="t('common.edit')"
                  @click="openEdit(record)"
                >
                  <Pencil :size="17" />
                </RowActionButton>
                <RowActionButton
                  v-if="auth.hasPermission('platform:tenants:update')"
                  :label="t(record.status === 'ACTIVE' ? 'common.disabled' : 'common.active')"
                  @click="changeStatus(record)"
                >
                  <Power :size="17" />
                </RowActionButton>
                <RowActionButton
                  v-if="auth.hasPermission('platform:tenants:delete')"
                  danger
                  :label="t('common.delete')"
                  @click="deleteTenant(record)"
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
          @change="(page: number) => changePage(page, query.size)"
        />
      </div>
    </section>

    <a-drawer
      :open="route.name === 'platform-tenant-detail'"
      :title="detailTenant ? t('tenant.detailTitle', { name: detailTenant.name }) : t('route.platformTenantDetail')"
      :width="620"
      class="responsive-drawer"
      @close="closeDetail"
    >
      <template v-if="detailTenant">
        <a-descriptions
          :column="1"
          bordered
          size="small"
        >
          <a-descriptions-item :label="t('tenant.name')">
            {{ detailTenant.name }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('tenant.code')">
            {{ detailTenant.code }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('tenant.type')">
            {{ tenantTypeLabel(detailTenant.tenantType) }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('tenant.status')">
            {{ statusLabel(detailTenant.status) }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('tenant.createdAt')">
            {{ formatDateTime(detailTenant.createdAt) }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('common.updatedAt')">
            {{ formatDateTime(detailTenant.updatedAt) }}
          </a-descriptions-item>
        </a-descriptions>
        <div class="tenant-detail-actions">
          <a-button
            v-if="auth.hasPermission('platform:tenants:members:read')"
            @click="router.push(`/platform/tenants/${detailTenant.tenantId}/members`)"
          >
            <Users :size="16" />{{ t("tenant.memberManagement") }}
          </a-button>
          <a-button
            v-if="auth.hasPermission('platform:tenants:roles:read')"
            @click="router.push(`/platform/tenants/${detailTenant.tenantId}/roles`)"
          >
            <ShieldCheck :size="16" />{{ t("tenant.roleManagement") }}
          </a-button>
          <a-button
            v-if="auth.hasPermission('platform:tenants:dictionaries:read')"
            @click="router.push(`/platform/tenants/${detailTenant.tenantId}/dictionaries`)"
          >
            <BookOpenText :size="16" />{{ t("tenant.dictionaryManagement") }}
          </a-button>
          <a-button
            v-if="auth.hasPermission('platform:tenants:delete')"
            danger
            @click="deleteTenant(detailTenant)"
          >
            <Trash2 :size="16" />{{ t("tenant.delete") }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="filterOpen"
      :title="t('tenant.filterTitle')"
      placement="right"
      width="100%"
      :root-style="{ width: '100dvw' }"
      :content-wrapper-style="{ maxWidth: '360px' }"
    >
      <a-form
        class="drawer-body-layout"
        layout="vertical"
      >
        <a-form-item :label="t('tenant.keyword')">
          <a-input
            v-model:value="query.search"
            allow-clear
            :placeholder="t('tenant.searchPlaceholder')"
          />
        </a-form-item>
        <a-form-item :label="t('tenant.status')">
          <a-select
            v-model:value="query.status"
            allow-clear
            :placeholder="t('common.allStatuses')"
            :loading="optionLoading"
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
        <div class="drawer-footer-actions drawer-body-actions">
          <a-button @click="resetQuery">
            {{ t("common.reset") }}
          </a-button>
          <a-button
            type="primary"
            @click="applyQuery"
          >
            {{ t("tenant.applyFilter") }}
          </a-button>
        </div>
      </a-form>
    </a-drawer>

    <a-drawer
      v-model:open="editorOpen"
      :title="t(editing ? 'tenant.edit' : 'tenant.create')"
      width="100%"
      :root-style="{ width: '100dvw' }"
      :content-wrapper-style="{ maxWidth: '480px' }"
    >
      <a-form
        class="drawer-body-layout"
        layout="vertical"
      >
        <a-form-item
          :label="t('tenant.code')"
          required
        >
          <a-input
            v-model:value="form.code"
            :placeholder="t('tenant.codePlaceholder')"
          />
          <div
            v-if="editing"
            class="tenant-code-warning"
          >
            {{ t("tenant.codeHistoryWarning") }}
          </div>
        </a-form-item>
        <a-form-item
          :label="t('tenant.name')"
          required
        >
          <a-input
            v-model:value="form.name"
            :placeholder="t('tenant.namePlaceholder')"
          />
        </a-form-item>
        <div class="drawer-footer-actions drawer-body-actions">
          <a-button @click="editorOpen = false">
            {{ t("common.cancel") }}
          </a-button>
          <a-button
            type="primary"
            :loading="submitting"
            @click="submit"
          >
            {{ t("common.save") }}
          </a-button>
        </div>
      </a-form>
    </a-drawer>
  </div>
</template>

<style scoped>
.tenant-cell { display: flex; min-width: 0; align-items: center; gap: 10px; }
.tenant-icon { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; color: var(--app-primary); background: var(--app-primary-soft); border-radius: var(--app-radius); }
.tenant-cell div { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.tenant-cell strong { overflow: hidden; color: var(--app-text); text-overflow: ellipsis; white-space: nowrap; }
.tenant-cell span { color: var(--app-text-secondary); font-size: 12px; }
.tenant-code-warning { margin-top: 6px; color: var(--app-warning); font-size: 12px; line-height: 1.5; }
.tenant-detail-actions { display: flex; margin-top: 18px; justify-content: flex-end; gap: 8px; }
.drawer-body-layout { display: flex; min-height: 100%; flex-direction: column; }
.drawer-footer-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.drawer-footer-actions :deep(.ant-btn) { min-height: 44px; width: 100%; }
.drawer-body-actions { position: sticky; bottom: -24px; z-index: 1; margin: auto -24px -24px; padding: 12px 24px calc(12px + env(safe-area-inset-bottom)); background: var(--app-surface); border-top: 1px solid var(--app-border); }
@media (max-width: 900px) { .tenant-detail-actions > * { min-height: 44px; flex: 1; } }
</style>
