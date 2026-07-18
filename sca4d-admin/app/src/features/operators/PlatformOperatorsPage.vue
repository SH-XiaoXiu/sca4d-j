<script setup lang="ts">
defineOptions({ name: "PlatformOperatorsPage" });

import {
  createIdempotencyKey,
  isApiError,
  normalizePageData,
  type Id,
  type ManagedAccountView,
  type PlatformOperatorView,
  type PlatformRoleView
} from "@sca4d/admin-common";
import { CirclePlus, Power, ShieldCheck, SlidersHorizontal, UsersRound } from "lucide-vue-next";
import { message, Modal } from "ant-design-vue";
import { computed, onBeforeUnmount, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter, type LocationQuery } from "vue-router";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import PageHeader from "@/components/page/PageHeader.vue";
import QueryToolbar from "@/components/page/QueryToolbar.vue";
import { platformAccountApi, platformOperatorApi } from "@/api/client";
import { routePositiveInteger, useRouteQueryState } from "@/composables/useRouteQueryState";
import { useSessionStore } from "@/stores/session";

interface OperatorListQuery {
  page: number;
  size: number;
}

const auth = useSessionStore();
const router = useRouter();
const { t } = useI18n();
const loading = ref(false);
const submitting = ref(false);
const createOpen = ref(false);
const rolesOpen = ref(false);
const records = ref<PlatformOperatorView[]>([]);
const total = ref(0);
const globalAccounts = ref<ManagedAccountView[]>([]);
const editing = ref<PlatformOperatorView | null>(null);
const roleOptions = ref<PlatformRoleView[]>([]);
const selectedRoleIds = ref<Id[]>([]);
const createForm = reactive({ accountId: "", displayName: "" });
let listController: AbortController | null = null;

const columns = computed(() => [
  { title: t("operatorManagement.title"), key: "operator", width: 240 },
  { title: t("operatorManagement.roles"), key: "roles" },
  { title: t("common.status"), key: "status", width: 100 },
  { title: t("common.actions"), key: "actions", width: 156, fixed: "right" as const }
]);
const canManage = computed(() => auth.hasPermission("platform:operators:manage"));
const canReadRoles = computed(() => auth.hasPermission("platform:roles:read"));
const canManageTenantAccess = computed(() => auth.hasPermission("platform:tenant-access:manage"));
const queryState = useRouteQueryState<OperatorListQuery>({
  decode(value: LocationQuery) {
    return {
      page: routePositiveInteger(value, "page", 1),
      size: routePositiveInteger(value, "size", 20)
    };
  },
  encode(value) {
    return {
      ...(value.page === 1 ? {} : { page: String(value.page) }),
      ...(value.size === 20 ? {} : { size: String(value.size) })
    };
  }
}, load);
const query = queryState.draft;

function errorMessage(error: unknown, fallback: string): string {
  return isApiError(error) ? error.message : t(fallback);
}

function statusColor(status: PlatformOperatorView["status"]): "success" | "default" {
  return status === "ACTIVE" ? "success" : "default";
}

function statusLabel(status: PlatformOperatorView["status"]): string {
  return status === "ACTIVE" ? t("common.active") : t("common.disabled");
}

async function load(value: Readonly<OperatorListQuery>): Promise<void> {
  listController?.abort();
  const requestController = new AbortController();
  listController = requestController;
  loading.value = true;
  try {
    const page = normalizePageData(await platformOperatorApi.page({ page: value.page, size: value.size }, requestController.signal));
    if (requestController.signal.aborted || listController !== requestController) return;
    records.value = page.records;
    total.value = page.total;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "operatorManagement.loadFailed"));
    }
  } finally {
    if (listController === requestController) loading.value = false;
  }
}

async function loadGlobalAccounts(): Promise<void> {
  try {
    const page = normalizePageData(await platformAccountApi.page({ page: 1, size: 200, status: "ACTIVE" }));
    globalAccounts.value = page.records;
  } catch (error) {
    message.error(errorMessage(error, "operatorManagement.loadFailed"));
  }
}

async function openCreate(): Promise<void> {
  Object.assign(createForm, { accountId: "", displayName: "" });
  if (globalAccounts.value.length === 0) await loadGlobalAccounts();
  createOpen.value = true;
}

async function create(): Promise<void> {
  if (!createForm.accountId || !createForm.displayName.trim()) {
    message.warning(t("operatorManagement.accountRequired"));
    return;
  }
  submitting.value = true;
  try {
    await platformOperatorApi.create({
      accountId: createForm.accountId,
      displayName: createForm.displayName.trim(),
      idempotencyKey: createIdempotencyKey()
    });
    createOpen.value = false;
    message.success(t("operatorManagement.created"));
    await queryState.reload();
  } catch (error) {
    message.error(errorMessage(error, "operatorManagement.createFailed"));
  } finally {
    submitting.value = false;
  }
}

async function openRoles(operator: PlatformOperatorView): Promise<void> {
  editing.value = operator;
  selectedRoleIds.value = operator.roles.map((role) => role.roleId);
  rolesOpen.value = true;
  try {
    roleOptions.value = await platformOperatorApi.assignableRoles(operator.principalId);
  } catch (error) {
    message.error(errorMessage(error, "operatorManagement.roleOptionsLoadFailed"));
  }
}

async function replaceRoles(): Promise<void> {
  if (!editing.value) return;
  submitting.value = true;
  try {
    await platformOperatorApi.replaceRoles(editing.value.principalId, selectedRoleIds.value, editing.value.version);
    rolesOpen.value = false;
    message.success(t("operatorManagement.rolesUpdated"));
    await queryState.reload();
  } catch (error) {
    message.error(errorMessage(error, "operatorManagement.rolesUpdateFailed"));
  } finally {
    submitting.value = false;
  }
}

function changeStatus(operator: PlatformOperatorView): void {
  const next = operator.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  const action = statusLabel(next);
  Modal.confirm({
    title: t("operatorManagement.statusConfirm", { action, name: operator.displayName }),
    okText: action,
    okType: next === "DISABLED" ? "danger" : "primary",
    cancelText: t("common.cancel"),
    async onOk() {
      try {
        await platformOperatorApi.changeStatus(operator.principalId, next, operator.version);
        message.success(t("operatorManagement.statusChanged", { action }));
        await queryState.reload();
      } catch (error) {
        message.error(errorMessage(error, "operatorManagement.statusFailed"));
        throw error;
      }
    }
  });
}

function openTenantAccess(operator: PlatformOperatorView): void {
  void router.push({ name: "platform-tenant-access", query: { principalId: operator.principalId } });
}

function changePage(page: number, size: number): void {
  void queryState.update({ page, size });
}

onBeforeUnmount(() => listController?.abort());
</script>

<template>
  <div>
    <PageHeader
      :title="t('operatorManagement.title')"
      :description="t('operatorManagement.description')"
    >
      <template #actions>
        <a-button
          v-if="canManage"
          type="primary"
          @click="openCreate"
        >
          <CirclePlus :size="16" />{{ t("operatorManagement.create") }}
        </a-button>
      </template>
    </PageHeader>

    <section class="page-panel management-panel">
      <QueryToolbar>
        <template #actions>
          <a-button @click="queryState.reload">
            {{ t("common.refresh") }}
          </a-button>
        </template>
      </QueryToolbar>
      <a-table
        row-key="principalId"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 760 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'operator'">
            <div class="operator-cell">
              <UsersRound
                :size="16"
                aria-hidden="true"
              /><span>{{ record.displayName }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'roles'">
            <span v-if="record.roles.length">{{ record.roles.map((role: PlatformRoleView) => role.name).join(t("common.listSeparator")) }}</span>
            <span v-else>{{ t("common.unavailable") }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <div class="row-actions">
              <RowActionButton
                v-if="canManage && canReadRoles"
                :label="t('operatorManagement.roles')"
                @click="openRoles(record)"
              >
                <ShieldCheck :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManageTenantAccess"
                :label="t('operatorManagement.access')"
                @click="openTenantAccess(record)"
              >
                <SlidersHorizontal :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                :label="statusLabel(record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')"
                :danger="record.status === 'ACTIVE'"
                @click="changeStatus(record)"
              >
                <Power :size="17" />
              </RowActionButton>
            </div>
          </template>
        </template>
      </a-table>
      <a-pagination
        class="table-pagination"
        :current="query.page"
        :page-size="query.size"
        :total="total"
        show-size-changer
        :show-total="(count: number) => t('common.total', { total: count })"
        @change="changePage"
        @show-size-change="changePage"
      />
    </section>

    <a-modal
      v-model:open="createOpen"
      :title="t('operatorManagement.create')"
      :confirm-loading="submitting"
      @ok="create"
    >
      <a-form layout="vertical">
        <a-form-item
          :label="t('operatorManagement.account')"
          required
        >
          <a-select
            v-model:value="createForm.accountId"
            :options="globalAccounts.map((account) => ({ value: account.accountId, label: account.accountId }))"
          />
        </a-form-item>
        <a-form-item
          :label="t('operatorManagement.displayName')"
          required
        >
          <a-input v-model:value="createForm.displayName" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="rolesOpen"
      :title="t('operatorManagement.roles')"
      :confirm-loading="submitting"
      @ok="replaceRoles"
    >
      <a-select
        v-model:value="selectedRoleIds"
        mode="multiple"
        style="width: 100%"
        :options="roleOptions.map((role) => ({ value: role.roleId, label: role.name }))"
      />
    </a-modal>
  </div>
</template>

<style scoped>
.operator-cell,
.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
