<script setup lang="ts">
defineOptions({ name: "PlatformTenantAccessPage" });

import {
  createIdempotencyKey,
  isApiError,
  normalizePageData,
  type AccessAction,
  type DictionaryOptionView,
  type Id,
  type PageResult
} from "@sca4d/admin-common";
import { RotateCcw, Save, ShieldCheck, Trash2 } from "lucide-vue-next";
import { message } from "ant-design-vue";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  platformTenantAccessApi,
  platformTenantApi,
  platformOperatorApi,
  systemDictionaryApi
} from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import RemoteResourceSelect, { type RemoteResourceOption } from "@/components/data-entry/RemoteResourceSelect.vue";
import ResourceLink from "@/components/data-display/ResourceLink.vue";
import { useSessionStore } from "@/stores/session";

interface ResourceSnapshot {
  label: string | null;
  path: string | null;
  navigationMessage: string | null;
}

interface TenantRule extends ResourceSnapshot {
  accessAction: AccessAction;
}

const { t } = useI18n();
const auth = useSessionStore();
const loading = ref(false);
const submitting = ref(false);
const selectedPrincipalId = ref<Id | undefined>(
  auth.me?.principal?.principalType === "PLATFORM" ? auth.me.principal.principalId : undefined
);
const operatorSnapshots = ref<Map<Id, ResourceSnapshot>>(new Map());
const tenantSnapshots = ref<Map<Id, ResourceSnapshot>>(new Map());
const rules = ref<Map<Id, TenantRule>>(new Map());
const defaultAction = ref<AccessAction | null>(null);
const actionOptions = ref<DictionaryOptionView[]>([]);
const version = ref<Id>("");
const submittingKey = ref<string | null>(null);
let accessController: AbortController | null = null;

const canManage = computed(() => auth.hasPermission("platform:tenant-access:manage"));
const selectedOperatorOption = computed<RemoteResourceOption | undefined>(() => {
  if (!selectedPrincipalId.value) return undefined;
  const operator = operatorSnapshots.value.get(selectedPrincipalId.value);
  return operator?.label ? { id: selectedPrincipalId.value, label: operator.label } : undefined;
});
const selectedTenantIds = computed(() => [...rules.value.keys()]);
const selectedTenantOptions = computed<RemoteResourceOption[]>(() => [...rules.value.entries()].map(([id, rule]) => ({
  id,
  label: rule.label ?? rule.navigationMessage ?? t("common.notProvided"),
  description: rule.navigationMessage
})));
const actionSelectOptions = computed(() => actionOptions.value.map((option) => ({
  value: option.value as AccessAction,
  label: option.label
})));

function errorMessage(error: unknown, fallback: string): string {
  return isApiError(error) ? error.message : fallback;
}

function actionLabel(action: AccessAction): string {
  return actionOptions.value.find((option) => option.value === action)?.label ?? action;
}

async function loadActions(): Promise<void> {
  try {
    const selections = await systemDictionaryApi.options(["common.access-action"]);
    actionOptions.value = selections.find((selection) => selection.code === "common.access-action")?.options ?? [];
  } catch (error) {
    message.error(errorMessage(error, t("tenantAccess.actionLoadFailed")));
  }
}

async function load(): Promise<void> {
  accessController?.abort();
  accessController = null;
  const principalId = selectedPrincipalId.value;
  if (!principalId) {
    loading.value = false;
    return;
  }
  const requestController = new AbortController();
  accessController = requestController;
  loading.value = true;
  try {
    const access = await platformTenantAccessApi.current(principalId, requestController.signal);
    if (requestController.signal.aborted || accessController !== requestController) return;
    defaultAction.value = access.defaultAction;
    version.value = access.version;
    const nextRules = new Map<Id, TenantRule>();
    const snapshots = new Map(tenantSnapshots.value);
    for (const grant of access.grants) {
      const tenant = grant.tenant;
      const snapshot = {
        label: tenant.label,
        path: tenant.path,
        navigationMessage: tenant.navigationMessage
      };
      snapshots.set(tenant.resourceId, snapshot);
      nextRules.set(tenant.resourceId, { ...snapshot, accessAction: grant.accessAction });
    }
    tenantSnapshots.value = snapshots;
    rules.value = nextRules;
  } catch (error) {
    if (!requestController.signal.aborted && accessController === requestController) {
      message.error(errorMessage(error, t("tenantAccess.loadFailed")));
    }
  } finally {
    if (accessController === requestController) loading.value = false;
  }
}

async function loadOperatorOptions(
  search: string,
  page: number,
  size: number,
  signal: AbortSignal
): Promise<PageResult<RemoteResourceOption>> {
  void search;
  const result = normalizePageData(await platformOperatorApi.page({ page, size }, signal));
  const snapshots = new Map(operatorSnapshots.value);
  for (const operator of result.records) {
    snapshots.set(operator.principalId, {
      label: operator.displayName,
      path: `/platform/operators/${operator.principalId}`,
      navigationMessage: null
    });
  }
  operatorSnapshots.value = snapshots;
  return {
    ...result,
    records: result.records.map((operator) => ({
      id: operator.principalId,
      label: operator.displayName,
      description: operator.status
    }))
  };
}

async function loadTenantOptions(
  search: string,
  page: number,
  size: number,
  signal: AbortSignal
): Promise<PageResult<RemoteResourceOption>> {
  const result = normalizePageData(await platformTenantApi.page({ page, size, search }, signal));
  const snapshots = new Map(tenantSnapshots.value);
  for (const tenant of result.records) {
    snapshots.set(tenant.tenantId, {
      label: tenant.name,
      path: `/platform/tenants/${tenant.tenantId}`,
      navigationMessage: null
    });
  }
  tenantSnapshots.value = snapshots;
  return {
    ...result,
    records: result.records.map((tenant) => ({
      id: tenant.tenantId,
      label: tenant.name,
      description: tenant.code
    }))
  };
}

function selectOperator(value: Id | Id[] | undefined): void {
  selectedPrincipalId.value = Array.isArray(value) ? value[0] : value;
  submittingKey.value = null;
  defaultAction.value = null;
  rules.value = new Map();
  void load();
}

function selectTenants(value: Id | Id[] | undefined): void {
  const ids = new Set(Array.isArray(value) ? value : value == null ? [] : [value]);
  const next = new Map<Id, TenantRule>();
  for (const tenantId of ids) {
    const current = rules.value.get(tenantId);
    if (current) {
      next.set(tenantId, current);
      continue;
    }
    const snapshot = tenantSnapshots.value.get(tenantId);
    if (snapshot) {
      next.set(tenantId, { ...snapshot, accessAction: "READ" });
    }
  }
  rules.value = next;
}

function changeRule(tenantId: Id, accessAction: AccessAction): void {
  const current = rules.value.get(tenantId);
  if (!current) return;
  rules.value = new Map(rules.value).set(tenantId, { ...current, accessAction });
}

function removeRule(tenantId: Id): void {
  const next = new Map(rules.value);
  next.delete(tenantId);
  rules.value = next;
}

async function submit(): Promise<void> {
  if (!selectedPrincipalId.value) {
    message.warning(t("tenantAccess.operatorRequired"));
    return;
  }
  submitting.value = true;
  try {
    submittingKey.value ??= createIdempotencyKey();
    await platformTenantAccessApi.replace(
      selectedPrincipalId.value,
      defaultAction.value,
      [...rules.value.entries()].map(([tenantId, rule]) => ({
        tenantId,
        accessAction: rule.accessAction
      })),
      version.value,
      submittingKey.value
    );
    submittingKey.value = null;
    message.success(t("tenantAccess.saved"));
    await load();
  } catch (error) {
    if (isApiError(error) && error.httpStatus === 409) {
      const draftDefault = defaultAction.value;
      const draftRules = new Map(rules.value);
      await load();
      defaultAction.value = draftDefault;
      rules.value = draftRules;
      submittingKey.value = null;
      message.warning(t("tenantAccess.conflict"));
      return;
    }
    message.error(errorMessage(error, t("tenantAccess.saveFailed")));
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  const principal = auth.me?.principal;
  const displayName = auth.me?.subject?.displayName;
  if (principal?.principalType === "PLATFORM" && displayName) {
    operatorSnapshots.value = new Map([[principal.principalId, {
      label: displayName,
      path: `/platform/operators/${principal.principalId}`,
      navigationMessage: null
    }]]);
  }
  void loadActions();
  if (selectedPrincipalId.value) void load();
});
onBeforeUnmount(() => accessController?.abort());
</script>

<template>
  <div>
    <PageHeader :title="t('route.platformTenantAccess')">
      <template #actions>
        <a-button
          :disabled="!selectedPrincipalId"
          :loading="loading"
          @click="load"
        >
          <RotateCcw :size="16" />{{ t('common.reset') }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          :disabled="!selectedPrincipalId"
          :loading="submitting"
          @click="submit"
        >
          <Save :size="16" />{{ t('common.save') }}
        </a-button>
      </template>
    </PageHeader>

    <section
      class="page-panel tenant-access-panel"
      :aria-busy="loading"
    >
      <a-form
        layout="vertical"
        class="tenant-access-form"
      >
        <a-form-item :label="t('tenantAccess.operator')">
          <RemoteResourceSelect
            :model-value="selectedPrincipalId"
            :selected-option="selectedOperatorOption"
            :load-options="loadOperatorOptions"
            :placeholder="t('tenantAccess.operatorPlaceholder')"
            :empty-text="t('tenantAccess.operatorEmpty')"
            :disabled="submitting"
            @update:model-value="selectOperator"
            @load-error="message.error(errorMessage($event, t('tenantAccess.operatorLoadFailed')))"
          />
        </a-form-item>

        <a-form-item :label="t('tenantAccess.defaultAction')">
          <a-select
            :value="defaultAction ?? undefined"
            :options="actionSelectOptions"
            allow-clear
            :placeholder="t('tenantAccess.noDefault')"
            :disabled="!selectedPrincipalId || !canManage || submitting"
            @update:value="defaultAction = $event ?? null"
          />
        </a-form-item>

        <a-form-item :label="t('tenantAccess.tenants')">
          <RemoteResourceSelect
            :model-value="selectedTenantIds"
            multiple
            :selected-options="selectedTenantOptions"
            :load-options="loadTenantOptions"
            :placeholder="t('tenantAccess.tenantPlaceholder')"
            :empty-text="t('tenantAccess.tenantEmpty')"
            :disabled="!selectedPrincipalId || !canManage || submitting"
            @update:model-value="selectTenants"
            @load-error="message.error(errorMessage($event, t('tenantAccess.tenantLoadFailed')))"
          />
        </a-form-item>
      </a-form>

      <a-divider />
      <div class="rules-heading">
        <strong><ShieldCheck :size="17" />{{ t('tenantAccess.rules') }}</strong>
        <a-tag>{{ t('tenantAccess.ruleCount', { count: rules.size }) }}</a-tag>
      </div>

      <a-empty
        v-if="rules.size === 0"
        :description="t('tenantAccess.noRules')"
      />
      <div
        v-else
        class="rule-list"
      >
        <div
          v-for="[tenantId, rule] in rules"
          :key="tenantId"
          class="rule-row"
        >
          <ResourceLink
            :label="rule.label"
            :to="rule.path ?? undefined"
            :navigation-message="rule.navigationMessage ?? undefined"
          />
          <a-select
            :value="rule.accessAction"
            :options="actionSelectOptions"
            :aria-label="t('tenantAccess.tenantAction', { tenant: rule.label ?? rule.navigationMessage ?? t('common.notProvided') })"
            :disabled="!canManage || submitting"
            @update:value="changeRule(tenantId, $event)"
          />
          <a-tag>{{ actionLabel(rule.accessAction) }}</a-tag>
          <a-button
            type="text"
            danger
            :aria-label="t('tenantAccess.removeRule', { tenant: rule.label ?? rule.navigationMessage ?? t('common.notProvided') })"
            :disabled="!canManage || submitting"
            @click="removeRule(tenantId)"
          >
            <Trash2 :size="16" />
          </a-button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tenant-access-panel { padding: 20px; }
.tenant-access-form { max-width: 720px; }
.rules-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.rules-heading strong { display: inline-flex; align-items: center; gap: 8px; }
.rule-list { display: grid; border-top: 1px solid var(--app-border); }
.rule-row { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(140px, 200px) auto 40px; align-items: center; gap: 12px; min-height: 58px; border-bottom: 1px solid var(--app-border); }
@media (max-width: 640px) {
  .tenant-access-panel { padding: 16px; }
  .tenant-access-form { max-width: none; }
  .rule-row { grid-template-columns: minmax(0, 1fr) 40px; padding: 12px 0; }
  .rule-row :deep(.ant-select) { grid-column: 1 / -1; width: 100%; }
  .rule-row :deep(.ant-tag) { justify-self: start; }
}
</style>
