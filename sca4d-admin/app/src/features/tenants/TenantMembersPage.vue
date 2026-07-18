<script setup lang="ts">
defineOptions({ name: "TenantMembersPage" });

import {
  createIdempotencyKey,
  isApiError,
  normalizePageData,
  type DictionaryOptionView,
  type Id,
  type ManagedAccountView,
  type MembershipStatus,
  type TenantMemberView,
  type TenantRoleView
} from "@sca4d/admin-common";
import { CirclePlus, Filter, KeyRound, Power, RotateCcw, Search, ShieldCheck } from "lucide-vue-next";
import { message, Modal, type TablePaginationConfig } from "ant-design-vue";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter, type LocationQuery } from "vue-router";
import {
  platformTenantAccountApi,
  platformTenantApi,
  platformTenantMemberApi,
  systemDictionaryApi,
  tenantAccountManagementApi,
  tenantMemberManagementApi
} from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import QueryToolbar from "@/components/page/QueryToolbar.vue";
import ResourceLink from "@/components/data-display/ResourceLink.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import { routePositiveInteger, routeText, useRouteQueryState } from "@/composables/useRouteQueryState";
import { useSessionStore } from "@/stores/session";

interface MemberPageQuery {
  page: number;
  size: number;
  search: string;
  status: MembershipStatus | undefined;
}

const auth = useSessionStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const tenantId = computed(() => String(route.params.tenantId));
const tenantScope = computed(() => route.name === "tenant-members" || route.name === "tenant-member-detail");
const memberApi = computed(() => (tenantScope.value ? tenantMemberManagementApi : platformTenantMemberApi));
const createPermission = computed(() =>
  tenantScope.value ? "tenant:members:create" : "platform:tenants:members:create"
);
const updatePermission = computed(() =>
  tenantScope.value ? "tenant:members:update" : "platform:tenants:members:update"
);
const roleReadPermission = computed(() =>
  tenantScope.value ? "tenant:roles:read" : "platform:tenants:roles:read"
);
const tenantName = ref(t("route.tenantMembers"));
const loading = ref(false);
const optionLoading = ref(false);
const submitting = ref(false);
const records = ref<TenantMemberView[]>([]);
const assignableRoles = ref<TenantRoleView[]>([]);
const statusOptions = ref<DictionaryOptionView[]>([]);
const total = ref(0);
const createOpen = ref(false);
const memberCreationMode = ref<"new" | "existing">("new");
const rolesOpen = ref(false);
const detailMember = ref<TenantMemberView | null>(null);
const filterOpen = ref(false);
const editing = ref<TenantMemberView | null>(null);
const queryState = useRouteQueryState<MemberPageQuery>({
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
const createForm = reactive({ identifier: "", password: "", displayName: "", phone: "", email: "", roleIds: [] as Id[] });
const existingAccountId = ref("");
const existingAccounts = ref<ManagedAccountView[]>([]);
const selectedRoleIds = ref<Id[]>([]);
let controller: AbortController | null = null;
let optionController: AbortController | null = null;
let detailController: AbortController | null = null;

const columns = computed(() => [
  { title: t("member.member"), key: "member", width: 230 },
  { title: t("member.roles"), key: "roles" },
  { title: t("common.status"), key: "status", width: 92 },
  { title: t("common.actions"), key: "actions", width: 160, fixed: "right" as const }
]);

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function statusLabel(status: MembershipStatus): string {
  return statusOptions.value.find((option) => option.value === status)?.label ?? t("common.unavailable");
}

function statusColor(status: MembershipStatus): string {
  const tone = statusOptions.value.find((option) => option.value === status)?.tone;
  if (tone === "SUCCESS") return "success";
  if (tone === "WARNING") return "warning";
  if (tone === "DANGER") return "error";
  if (tone === "INFO") return "processing";
  return "default";
}

async function loadStatusOptions(): Promise<void> {
  optionController?.abort();
  const requestController = new AbortController();
  optionController = requestController;
  optionLoading.value = true;
  try {
    const selections = await systemDictionaryApi.options(["iam.membership-status"], requestController.signal);
    if (requestController.signal.aborted || optionController !== requestController) return;
    statusOptions.value = selections.find((selection) => selection.code === "iam.membership-status")?.options ?? [];
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "member.optionsLoadFailed"));
    }
  } finally {
    if (optionController === requestController) optionLoading.value = false;
  }
}

async function load(value: Readonly<MemberPageQuery>): Promise<void> {
  controller?.abort();
  const requestController = new AbortController();
  controller = requestController;
  loading.value = true;
  try {
    const base = { page: value.page, size: value.size, search: value.search };
    const page = normalizePageData(
      await memberApi.value.page(
        tenantId.value,
        value.status ? { ...base, status: value.status } : base,
        requestController.signal
      )
    );
    if (requestController.signal.aborted || controller !== requestController) return;
    records.value = page.records;
    total.value = page.total;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "member.loadFailed"));
    }
  } finally {
    if (controller === requestController) loading.value = false;
  }
}

async function loadRoleOptions(): Promise<void> {
  try {
    assignableRoles.value = await memberApi.value.assignableRoles(tenantId.value);
  } catch (error) {
    message.error(errorMessage(error, "member.roleOptionsLoadFailed"));
  }
}

async function loadTenant(): Promise<void> {
  if (tenantScope.value) {
    tenantName.value =
      auth.me?.tenants.find((tenant) => tenant.tenantId === tenantId.value)?.tenantName ?? t("route.memberManagement");
    return;
  }
  try {
    tenantName.value = (await platformTenantApi.detail(tenantId.value)).name;
  } catch {
    tenantName.value = t("route.tenantMembers");
  }
}

async function createAccount(): Promise<void> {
  if (!createForm.identifier.trim() || !createForm.password || !createForm.displayName.trim()) {
    message.warning(t("member.accountRequired"));
    return;
  }
  submitting.value = true;
  try {
    const input = {
      identifier: createForm.identifier.trim(),
      password: createForm.password,
      displayName: createForm.displayName.trim(),
      roleIds: [...createForm.roleIds],
      idempotencyKey: createIdempotencyKey()
    };
    if (createForm.phone.trim()) Object.assign(input, { phone: createForm.phone.trim() });
    if (createForm.email.trim()) Object.assign(input, { email: createForm.email.trim() });
    await memberApi.value.createAccount(tenantId.value, input);
    createOpen.value = false;
    message.success(t("member.accountCreated"));
    await queryState.reload();
  } catch (error) {
    message.error(errorMessage(error, "member.createAccountFailed"));
  } finally {
    submitting.value = false;
  }
}

async function loadExistingAccounts(): Promise<void> {
  try {
    const accountApi = tenantScope.value ? tenantAccountManagementApi : platformTenantAccountApi;
    existingAccounts.value = normalizePageData(await accountApi.page(tenantId.value, {
      page: 1,
      size: 200,
      status: "ACTIVE"
    })).records;
  } catch (error) {
    message.error(errorMessage(error, "member.existingAccountsLoadFailed"));
  }
}

async function openCreate(): Promise<void> {
  Object.assign(createForm, { identifier: "", password: "", displayName: "", phone: "", email: "", roleIds: [] });
  memberCreationMode.value = "new";
  existingAccountId.value = "";
  createOpen.value = true;
  if (existingAccounts.value.length === 0) await loadExistingAccounts();
}

async function addExistingAccount(): Promise<void> {
  if (!existingAccountId.value || !createForm.displayName.trim()) {
    message.warning(t("member.existingAccountRequired"));
    return;
  }
  submitting.value = true;
  try {
    await memberApi.value.addExistingAccount(tenantId.value, {
      accountId: existingAccountId.value,
      displayName: createForm.displayName.trim(),
      roleIds: [...createForm.roleIds],
      idempotencyKey: createIdempotencyKey()
    });
    createOpen.value = false;
    message.success(t("member.existingAccountAdded"));
    await queryState.reload();
  } catch (error) {
    message.error(errorMessage(error, "member.addExistingAccountFailed"));
  } finally {
    submitting.value = false;
  }
}

function changeStatus(member: TenantMemberView): void {
  const next = member.membershipStatus === "ACTIVE" ? "DISABLED" : "ACTIVE";
  const action = t(next === "ACTIVE" ? "common.active" : "common.disabled");
  Modal.confirm({
    title: t("member.statusConfirm", { action, name: member.displayName }),
    content: t(next === "DISABLED" ? "member.disableWarning" : "member.enableWarning"),
    okText: action,
    okType: next === "DISABLED" ? "danger" : "primary",
    cancelText: t("common.cancel"),
    async onOk() {
      try {
        await memberApi.value.changeStatus(
          tenantId.value,
          member.principalId,
          next,
          member.membershipVersion
        );
        message.success(t("member.statusChanged", { action }));
        await queryState.reload();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 409) {
          await queryState.reload();
          message.warning(t("member.statusConflict", { action }));
          return;
        }
        message.error(errorMessage(error, "member.statusFailed"));
        throw error;
      }
    }
  });
}

function openRoles(member: TenantMemberView): void {
  editing.value = member;
  selectedRoleIds.value = member.roles.map((role) => role.roleId);
  rolesOpen.value = true;
}

async function replaceRoles(): Promise<void> {
  if (!editing.value) return;
  submitting.value = true;
  try {
    await memberApi.value.replaceRoles(
      tenantId.value,
      editing.value.principalId,
      selectedRoleIds.value,
      editing.value.principalVersion
    );
    rolesOpen.value = false;
    message.success(t("member.rolesUpdated"));
    await queryState.reload();
  } catch (error) {
    if (isApiError(error) && error.httpStatus === 409) {
      const principalId = editing.value.principalId;
      await queryState.reload();
      const latest = records.value.find((record) => record.principalId === principalId);
      if (latest) editing.value = latest;
      message.warning(t("member.rolesConflict"));
      return;
    }
    message.error(errorMessage(error, "member.rolesUpdateFailed"));
  } finally {
    submitting.value = false;
  }
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

function memberDetailRoute(member: TenantMemberView) {
  return {
    name: tenantScope.value ? "tenant-member-detail" : "platform-tenant-member-detail",
    params: { tenantId: tenantId.value, principalId: member.principalId },
    query: route.query
  };
}

async function syncRoutedMember(principalId: string | undefined): Promise<void> {
  detailController?.abort();
  if (!principalId) {
    detailMember.value = null;
    return;
  }
  const requestController = new AbortController();
  detailController = requestController;
  const requestedTenantId = tenantId.value;
  try {
    const member = await memberApi.value.detail(requestedTenantId, principalId, requestController.signal);
    if (requestController.signal.aborted || detailController !== requestController) return;
    detailMember.value = member;
  } catch (error) {
    if (requestController.signal.aborted || detailController !== requestController) return;
    message.error(errorMessage(error, "member.detailLoadFailed"));
    await router.replace({
      name: tenantScope.value ? "tenant-members" : "platform-tenant-members",
      params: { tenantId: requestedTenantId },
      query: route.query
    });
    return;
  }
}

function closeDetail(): void {
  if (route.name === "tenant-member-detail" || route.name === "platform-tenant-member-detail") {
    void router.push({
      name: tenantScope.value ? "tenant-members" : "platform-tenant-members",
      params: { tenantId: tenantId.value },
      query: route.query
    });
  }
}

watch(
  () => (typeof route.params.principalId === "string" ? route.params.principalId : undefined),
  (principalId) => void syncRoutedMember(principalId),
  { immediate: true }
);

onMounted(() => {
  void Promise.all([loadTenant(), loadRoleOptions(), loadStatusOptions()]);
});
onBeforeUnmount(() => {
  controller?.abort();
  optionController?.abort();
  detailController?.abort();
});
</script>

<template>
  <div>
    <PageHeader :title="tenantName">
      <template #actions>
        <a-button
          class="mobile-only"
          @click="filterOpen = true"
        >
          <Filter :size="16" />{{ t("common.filter") }}
        </a-button>
        <a-button
          v-if="auth.hasPermission(roleReadPermission)"
          @click="router.push(tenantScope ? `/tenant/tenants/${tenantId}/roles` : `/platform/tenants/${tenantId}/roles`)"
        >
          <ShieldCheck :size="16" />{{ t("member.roleManagement") }}
        </a-button>
        <a-button
          v-if="auth.hasPermission('account.security.read')"
          @click="router.push(tenantScope ? `/tenant/tenants/${tenantId}/accounts` : `/platform/tenants/${tenantId}/accounts`)"
        >
          <KeyRound :size="16" />{{ t("route.tenantAccounts") }}
        </a-button>
        <a-button
          v-if="auth.hasPermission(createPermission)"
          type="primary"
          @click="openCreate"
        >
          <CirclePlus :size="16" />{{ t("member.createAccount") }}
        </a-button>
      </template>
    </PageHeader>

    <section class="page-panel management-panel">
      <QueryToolbar class="desktop-only">
        <template #search>
          <a-input
            v-model:value="query.search"
            allow-clear
            :placeholder="t('member.searchPlaceholder')"
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
        row-key="principalId"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="{ current: query.page, pageSize: query.size, total, showSizeChanger: true, showTotal: (value: number) => t('common.total', { total: value }) }"
        :scroll="{ x: 820 }"
        @change="(pagination: TablePaginationConfig) => changePage(pagination.current ?? query.page, pagination.pageSize ?? query.size)"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'member'">
            <div class="member-summary">
              <div class="primary-cell">
                <ResourceLink
                  :label="record.displayName"
                  :to="memberDetailRoute(record)"
                />
                <span>{{ record.principalId }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'roles'">
            <a-space wrap>
              <a-tag
                v-for="role in record.roles"
                :key="role.roleId"
              >
                {{ role.roleName }} · {{ role.roleCode }}
              </a-tag>
              <span
                v-if="record.roles.length === 0"
                class="empty-text"
              >{{ t("member.noRoles") }}</span>
            </a-space>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.membershipStatus)">
              {{ statusLabel(record.membershipStatus) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space
              v-if="auth.hasPermission(updatePermission)"
              :size="4"
            >
              <RowActionButton
                :label="t('member.roles')"
                @click="openRoles(record)"
              >
                <ShieldCheck :size="17" />
              </RowActionButton>
              <RowActionButton
                :label="t(record.membershipStatus === 'ACTIVE' ? 'common.disabled' : 'common.active')"
                @click="changeStatus(record)"
              >
                <Power :size="17" />
              </RowActionButton>
            </a-space>
          </template>
        </template>
      </a-table>

      <div class="mobile-only">
        <a-spin :spinning="loading">
          <a-empty
            v-if="!loading && records.length === 0"
            :description="t('member.empty')"
          />
          <div
            v-else
            class="mobile-card-list"
          >
            <article
              v-for="record in records"
              :key="record.principalId"
              class="mobile-record"
            >
              <div class="mobile-record__header">
                <div class="member-summary">
                  <div class="mobile-record__title">
                    <ResourceLink
                      :label="record.displayName"
                      :to="memberDetailRoute(record)"
                    />
                  </div>
                </div>
                <a-tag :color="statusColor(record.membershipStatus)">
                  {{ statusLabel(record.membershipStatus) }}
                </a-tag>
              </div>
              <div class="mobile-record__meta">
                <span>{{ t("member.principal") }}</span><strong>{{ record.principalId }}</strong>
                <span>{{ t("member.roles") }}</span><strong>{{ record.roles.map((role) => role.roleName).join(t("common.listSeparator")) || t("member.noRoles") }}</strong>
              </div>
              <div
                v-if="auth.hasPermission(updatePermission)"
                class="mobile-record__actions"
              >
                <RowActionButton
                  :label="t('member.updateRoles')"
                  @click="openRoles(record)"
                >
                  <ShieldCheck :size="17" />
                </RowActionButton>
                <RowActionButton
                  :label="t(record.membershipStatus === 'ACTIVE' ? 'common.disabled' : 'common.active')"
                  @click="changeStatus(record)"
                >
                  <Power :size="17" />
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
      :open="route.name === 'tenant-member-detail' || route.name === 'platform-tenant-member-detail'"
      :title="detailMember ? t('member.detailTitle', { name: detailMember.displayName }) : t('route.tenantMemberDetail')"
      :width="620"
      class="responsive-drawer"
      @close="closeDetail"
    >
      <template v-if="detailMember">
        <a-descriptions
          :column="1"
          bordered
          size="small"
        >
          <a-descriptions-item :label="t('member.member')">
            {{ detailMember.displayName }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('common.status')">
            {{ statusLabel(detailMember.membershipStatus) }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('member.principal')">
            {{ detailMember.principalId }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('member.roles')">
            <a-space wrap>
              <a-tag
                v-for="role in detailMember.roles"
                :key="role.roleId"
              >
                {{ role.roleName }} · {{ role.roleCode }}
              </a-tag>
              <span v-if="detailMember.roles.length === 0">{{ t("member.noRoles") }}</span>
            </a-space>
          </a-descriptions-item>
        </a-descriptions>
        <div
          v-if="auth.hasPermission(updatePermission)"
          class="member-detail-actions"
        >
          <a-button @click="openRoles(detailMember)">
            {{ t("member.updateRoles") }}
          </a-button>
          <a-button @click="changeStatus(detailMember)">
            {{ t(detailMember.membershipStatus === "ACTIVE" ? "member.disable" : "member.enable") }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="filterOpen"
      :title="t('member.filterTitle')"
      placement="right"
      width="100%"
      :root-style="{ width: '100dvw' }"
      :content-wrapper-style="{ maxWidth: '360px' }"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('member.keyword')">
          <a-input
            v-model:value="query.search"
            allow-clear
            :placeholder="t('member.searchPlaceholder')"
          />
        </a-form-item>
        <a-form-item :label="t('common.status')">
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
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="resetQuery">
            {{ t("common.reset") }}
          </a-button>
          <a-button
            type="primary"
            @click="applyQuery"
          >
            {{ t("member.applyFilter") }}
          </a-button>
        </a-space>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="createOpen"
      :title="memberCreationMode === 'new' ? t('member.createAccount') : t('member.addExistingAccount')"
      width="100%"
      :root-style="{ width: '100dvw' }"
      :content-wrapper-style="{ maxWidth: '500px' }"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('member.accountSource')">
          <a-radio-group v-model:value="memberCreationMode">
            <a-radio value="new">
              {{ t("member.createAccount") }}
            </a-radio>
            <a-radio value="existing">
              {{ t("member.addExistingAccount") }}
            </a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item
          v-if="memberCreationMode === 'existing'"
          :label="t('member.existingAccount')"
          required
        >
          <a-select
            v-model:value="existingAccountId"
            option-filter-prop="label"
            :placeholder="t('member.existingAccountPlaceholder')"
            :options="existingAccounts.map((account) => ({ value: account.accountId, label: `${account.displayName} · ${account.accountId}` }))"
          />
        </a-form-item>
        <a-form-item
          v-if="memberCreationMode === 'new'"
          :label="t('member.loginIdentifier')"
          required
        >
          <a-input
            v-model:value="createForm.identifier"
            autocomplete="username"
          />
        </a-form-item>
        <a-form-item
          v-if="memberCreationMode === 'new'"
          :label="t('member.initialPassword')"
          required
        >
          <a-input-password
            v-model:value="createForm.password"
            autocomplete="new-password"
          />
        </a-form-item>
        <a-form-item
          :label="t('member.displayName')"
          required
        >
          <a-input
            v-model:value="createForm.displayName"
            :aria-label="t('member.displayName')"
          />
        </a-form-item>
        <a-form-item :label="t('member.phone')">
          <a-input
            v-model:value="createForm.phone"
            inputmode="tel"
          />
        </a-form-item>
        <a-form-item :label="t('member.email')">
          <a-input
            v-model:value="createForm.email"
            type="email"
          />
        </a-form-item>
        <a-form-item :label="t('member.initialRoles')">
          <a-select
            v-model:value="createForm.roleIds"
            mode="multiple"
            option-filter-prop="label"
            :placeholder="t('member.rolePlaceholder')"
            :options="assignableRoles.map((role) => ({ value: role.roleId, label: `${role.name} · ${role.code}` }))"
          />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="createOpen = false">
            {{ t("common.cancel") }}
          </a-button><a-button
            type="primary"
            :loading="submitting"
            @click="memberCreationMode === 'new' ? createAccount() : addExistingAccount()"
          >
            {{ memberCreationMode === "new" ? t("member.createAndAdd") : t("member.addAndJoin") }}
          </a-button>
        </a-space>
      </template>
    </a-drawer>

    <a-modal
      v-model:open="rolesOpen"
      :title="t('member.updateRoles')"
      :confirm-loading="submitting"
      @ok="replaceRoles"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('member.roles')">
          <a-select
            v-model:value="selectedRoleIds"
            mode="multiple"
            option-filter-prop="label"
            :placeholder="t('member.rolesClearPlaceholder')"
            :options="assignableRoles.map((role) => ({ value: role.roleId, label: `${role.name} · ${role.code}` }))"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.primary-cell { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.member-summary { display: flex; min-width: 0; align-items: center; gap: 12px; }
.primary-cell strong { color: var(--app-text); }
.primary-cell span, .empty-text { color: var(--app-text-secondary); font-size: 12px; }
.member-detail-actions { display: flex; margin-top: 18px; justify-content: flex-end; gap: 8px; }
@media (max-width: 900px) { .member-detail-actions > * { min-height: 44px; flex: 1; } }
</style>
