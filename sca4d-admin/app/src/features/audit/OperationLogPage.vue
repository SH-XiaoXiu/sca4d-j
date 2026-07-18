<script setup lang="ts">
defineOptions({ name: "OperationLogPage" });

import {
  formatDateTime,
  isApiError,
  toApiDateTime,
  type DictionaryOptionView,
  type Id
} from "@sca4d/admin-common";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import { Eye, Filter, RotateCcw, Search, SlidersHorizontal } from "lucide-vue-next";
import { message, type TablePaginationConfig } from "ant-design-vue";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { LocationQuery } from "vue-router";
import {
  type AuditResult,
  operationLogApi,
  type OperationLogDetailView,
  type OperationLogModuleOption,
  type OperationLogQuery,
  type OperationLogView,
  type ResourceReferenceView
} from "@/api/operation-log";
import { systemDictionaryApi } from "@/api/client";
import JsonPreview from "@/components/data-display/JsonPreview.vue";
import PageHeader from "@/components/page/PageHeader.vue";
import QueryToolbar from "@/components/page/QueryToolbar.vue";
import RemoteResourceSelect from "@/components/data-entry/RemoteResourceSelect.vue";
import ResourceLink from "@/components/data-display/ResourceLink.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import { routePositiveInteger, routeText, useRouteQueryState } from "@/composables/useRouteQueryState";

interface OperationLogPageQuery {
  page: number;
  size: number;
  moduleCode: string;
  actionCode: string;
  result: AuditResult | undefined;
  subjectId: Id | undefined;
  operatorName: string;
  targetType: string;
  targetId: Id | undefined;
  traceId: string;
  dateRange: [Dayjs, Dayjs] | null;
}

const { t } = useI18n();
const loading = ref(false);
const records = ref<OperationLogView[]>([]);
const total = ref(0);
const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<OperationLogDetailView | null>(null);
const filterOpen = ref(false);
const catalog = ref<OperationLogModuleOption[]>([]);
const resultOptions = ref<DictionaryOptionView[]>([]);
const queryState = useRouteQueryState<OperationLogPageQuery>({
  decode(value: LocationQuery) {
    const start = routeText(value, "createdAtStart");
    const end = routeText(value, "createdAtEnd");
    const result = routeText(value, "result");
    return {
      page: routePositiveInteger(value, "page", 1),
      size: routePositiveInteger(value, "size", 20),
      moduleCode: routeText(value, "moduleCode") ?? "",
      actionCode: routeText(value, "actionCode") ?? "",
      result: result === "SUCCESS" || result === "FAILED" ? result : undefined,
      subjectId: routeText(value, "subjectId"),
      operatorName: routeText(value, "operatorName") ?? "",
      targetType: routeText(value, "targetType") ?? "",
      targetId: routeText(value, "targetId"),
      traceId: routeText(value, "traceId") ?? "",
      dateRange: start && end && dayjs(start).isValid() && dayjs(end).isValid()
        ? [dayjs(start), dayjs(end)]
        : null
    };
  },
  encode(value) {
    return {
      ...(value.page === 1 ? {} : { page: String(value.page) }),
      ...(value.size === 20 ? {} : { size: String(value.size) }),
      ...(value.moduleCode ? { moduleCode: value.moduleCode } : {}),
      ...(value.actionCode ? { actionCode: value.actionCode } : {}),
      ...(value.result ? { result: value.result } : {}),
      ...(value.subjectId ? { subjectId: value.subjectId } : {}),
      ...(value.subjectId && value.operatorName ? { operatorName: value.operatorName } : {}),
      ...(value.targetType ? { targetType: value.targetType } : {}),
      ...(value.targetId ? { targetId: value.targetId } : {}),
      ...(value.traceId ? { traceId: value.traceId } : {}),
      ...(value.dateRange?.[0] ? { createdAtStart: toApiDateTime(value.dateRange[0]) } : {}),
      ...(value.dateRange?.[1] ? { createdAtEnd: toApiDateTime(value.dateRange[1]) } : {})
    };
  }
}, load);
const query = queryState.draft;
let listController: AbortController | null = null;
let catalogController: AbortController | null = null;
let detailController: AbortController | null = null;

const columns = computed(() => [
  { title: t("operationLog.createdAt"), dataIndex: "createdAt", key: "createdAt", width: 172 },
  { title: t("operationLog.summary"), dataIndex: "summary", key: "summary", ellipsis: true },
  { title: t("operationLog.result"), dataIndex: "result", key: "result", width: 96 },
  { title: t("operationLog.operation"), key: "operation", width: 210 },
  { title: t("operationLog.operator"), key: "operator", width: 180 },
  { title: t("operationLog.target"), key: "target", width: 190 },
  { title: t("common.actions"), key: "actions", width: 84, fixed: "right" as const }
]);
const actionOptions = computed(
  () => catalog.value.find((module) => module.code === query.moduleCode)?.actions ?? []
);
const selectedOperator = computed(() => {
  const id = query.subjectId;
  const label = query.operatorName;
  return id && label ? { id, label } : undefined;
});

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function resultOption(result: AuditResult): DictionaryOptionView | undefined {
  return resultOptions.value.find((option) => option.value === result);
}

function resultColor(result: AuditResult): string {
  const tone = resultOption(result)?.tone;
  if (tone === "SUCCESS") return "success";
  if (tone === "WARNING") return "warning";
  if (tone === "DANGER") return "error";
  if (tone === "INFO") return "processing";
  return "default";
}

function resultLabel(result: AuditResult): string {
  return resultOption(result)?.label ?? result;
}

function operatorName(record: OperationLogView): string {
  if (!record.subjectId && record.moduleCode === "authentication.login") {
    return t("operationLog.unauthenticatedActor");
  }
  return record.subjectDisplayName || record.principalDisplayName || t("common.unavailable");
}

function targetName(record: OperationLogView): string {
  if (record.moduleCode !== "authentication.login") return t("operationLog.notApplicable");
  if (record.actionCode === "passkey-assertion") return t("operationLog.notApplicable");
  return t("operationLog.unidentifiedAccount");
}

function referencePath(reference: ResourceReferenceView | null): string | undefined {
  return reference?.navigationState === "NAVIGABLE" && reference.path ? reference.path : undefined;
}

async function loadCatalog(): Promise<void> {
  catalogController?.abort();
  const requestController = new AbortController();
  catalogController = requestController;
  try {
    const [operations, selections] = await Promise.all([
      operationLogApi.catalog(requestController.signal),
      systemDictionaryApi.options(["system.audit-result"], requestController.signal)
    ]);
    if (requestController.signal.aborted || catalogController !== requestController) return;
    catalog.value = operations;
    resultOptions.value = selections[0]?.options ?? [];
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "operationLog.catalogLoadFailed"));
    }
  }
}

async function load(value: Readonly<OperationLogPageQuery>): Promise<void> {
  listController?.abort();
  const requestController = new AbortController();
  listController = requestController;
  loading.value = true;
  try {
    const request: OperationLogQuery = { page: value.page, size: value.size };
    if (value.moduleCode) request.moduleCode = value.moduleCode;
    if (value.actionCode) request.actionCode = value.actionCode;
    if (value.result) request.result = value.result;
    if (value.subjectId) request.subjectId = value.subjectId;
    if (value.targetType) request.targetType = value.targetType;
    if (value.targetId) request.targetId = value.targetId;
    if (value.traceId) request.traceId = value.traceId;
    const createdAtStart = value.dateRange?.[0] ? toApiDateTime(value.dateRange[0]) : null;
    const createdAtEnd = value.dateRange?.[1] ? toApiDateTime(value.dateRange[1]) : null;
    if (createdAtStart) request.createdAtStart = createdAtStart;
    if (createdAtEnd) request.createdAtEnd = createdAtEnd;
    const page = await operationLogApi.page(request, requestController.signal);
    if (requestController.signal.aborted || listController !== requestController) return;
    records.value = page.records;
    total.value = page.total;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "operationLog.loadFailed"));
    }
  } finally {
    if (listController === requestController) loading.value = false;
  }
}

function search(): void {
  filterOpen.value = false;
  query.page = 1;
  void queryState.apply();
}

function reset(): void {
  filterOpen.value = false;
  void queryState.reset();
}

async function openDetail(id: string): Promise<void> {
  detailController?.abort();
  const requestController = new AbortController();
  detailController = requestController;
  detailOpen.value = true;
  detailLoading.value = true;
  detail.value = null;
  try {
    const loadedDetail = await operationLogApi.detail(id, requestController.signal);
    if (requestController.signal.aborted || detailController !== requestController) return;
    detail.value = loadedDetail;
  } catch (error) {
    if (!requestController.signal.aborted && detailController === requestController) {
      message.error(errorMessage(error, "operationLog.detailLoadFailed"));
    }
  } finally {
    if (detailController === requestController) detailLoading.value = false;
  }
}

function changePage(page: number, size: number): void {
  void queryState.update({ page, size });
}

watch(
  () => query.moduleCode,
  () => {
    if (!actionOptions.value.some((action) => action.code === query.actionCode)) query.actionCode = "";
  }
);
watch(
  () => query.subjectId,
  (subjectId) => {
    if (subjectId !== queryState.applied.value.subjectId) query.operatorName = "";
  }
);
watch(detailOpen, (open) => {
  if (!open) {
    detailController?.abort();
    detailLoading.value = false;
  }
});
onMounted(() => void loadCatalog());
onBeforeUnmount(() => {
  listController?.abort();
  catalogController?.abort();
  detailController?.abort();
});
</script>

<template>
  <div>
    <PageHeader
      :title="t('operationLog.title')"
      :description="t('operationLog.description')"
    >
      <template #actions>
        <a-button
          class="mobile-only"
          @click="filterOpen = true"
        >
          <Filter :size="16" />{{ t("common.filter") }}
        </a-button>
      </template>
    </PageHeader>

    <section class="page-panel operation-workspace">
      <QueryToolbar class="desktop-only">
        <template #filters>
          <a-form layout="inline">
            <a-form-item :label="t('operationLog.result')">
              <a-select
                v-model:value="query.result"
                allow-clear
                :placeholder="t('operationLog.allResults')"
                style="width: 132px"
              >
                <a-select-option
                  v-for="option in resultOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item :label="t('operationLog.timeRange')">
              <a-range-picker
                v-model:value="query.dateRange"
                show-time
                style="width: 320px"
              />
            </a-form-item>
          </a-form>
        </template>
        <template #actions>
          <a-button @click="filterOpen = true">
            <SlidersHorizontal :size="16" />{{ t("operationLog.advancedFilter") }}
          </a-button>
          <a-button @click="reset">
            <RotateCcw :size="16" />{{ t("common.reset") }}
          </a-button>
          <a-button
            type="primary"
            @click="search"
          >
            <Search :size="16" />{{ t("common.query") }}
          </a-button>
        </template>
      </QueryToolbar>

      <a-table
        class="desktop-only operation-table"
        row-key="id"
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
        :scroll="{ x: 1040 }"
        @change="(pagination: TablePaginationConfig) => changePage(pagination.current ?? query.page, pagination.pageSize ?? query.size)"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'summary'">
            <span class="operation-summary">{{ record.summary }}</span>
          </template>
          <template v-else-if="column.key === 'result'">
            <a-tag :color="resultColor(record.result)">
              {{ resultLabel(record.result) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'operation'">
            <span class="operation-name">{{ record.moduleName }} / {{ record.actionName }}</span>
          </template>
          <template v-else-if="column.key === 'operator'">
            <ResourceLink
              v-if="record.actorReference"
              :label="record.actorReference.label"
              :to="referencePath(record.actorReference)"
              :navigation-message="record.actorReference.navigationMessage ?? undefined"
            />
            <span v-else>{{ operatorName(record) }}</span>
          </template>
          <template v-else-if="column.key === 'target'">
            <ResourceLink
              v-if="record.targetReference"
              :label="record.targetReference.label"
              :to="referencePath(record.targetReference)"
              :navigation-message="record.targetReference.navigationMessage ?? undefined"
            />
            <span
              v-else
              class="empty-text"
            >{{ targetName(record) }}</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <RowActionButton
              :label="t('common.details')"
              @click="openDetail(record.id)"
            >
              <Eye :size="17" />
            </RowActionButton>
          </template>
        </template>
      </a-table>

      <div class="mobile-only operation-mobile">
        <a-spin :spinning="loading">
          <a-empty
            v-if="!loading && records.length === 0"
            :description="t('operationLog.empty')"
          />
          <div
            v-else
            class="mobile-card-list"
          >
            <article
              v-for="record in records"
              :key="record.id"
              class="mobile-record"
            >
              <div class="mobile-record__header">
                <div class="mobile-record__title">
                  {{ record.summary }}
                </div>
                <a-tag :color="resultColor(record.result)">
                  {{ resultLabel(record.result) }}
                </a-tag>
              </div>
              <dl class="operation-mobile__meta">
                <dt>{{ t("operationLog.operation") }}</dt>
                <dd>{{ record.moduleName }} / {{ record.actionName }}</dd>
                <dt>{{ t("operationLog.operator") }}</dt>
                <dd>
                  <ResourceLink
                    v-if="record.actorReference"
                    :label="record.actorReference.label"
                    :to="referencePath(record.actorReference)"
                    :navigation-message="record.actorReference.navigationMessage ?? undefined"
                  />
                  <span v-else>{{ operatorName(record) }}</span>
                </dd>
                <dt>{{ t("operationLog.target") }}</dt>
                <dd>
                  <ResourceLink
                    v-if="record.targetReference"
                    :label="record.targetReference.label"
                    :to="referencePath(record.targetReference)"
                    :navigation-message="record.targetReference.navigationMessage ?? undefined"
                  />
                  <span v-else>{{ targetName(record) }}</span>
                </dd>
                <dt>{{ t("operationLog.createdAt") }}</dt>
                <dd>{{ formatDateTime(record.createdAt) }}</dd>
              </dl>
              <div class="mobile-record__actions">
                <RowActionButton
                  :label="t('operationLog.viewDetails')"
                  @click="openDetail(record.id)"
                >
                  <Eye :size="17" />
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
      v-model:open="filterOpen"
      :title="t('operationLog.advancedFilter')"
      placement="right"
      :width="420"
      class="responsive-drawer"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('operationLog.result')">
          <a-select
            v-model:value="query.result"
            allow-clear
            :placeholder="t('operationLog.allResults')"
          >
            <a-select-option
              v-for="option in resultOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('operationLog.timeRange')">
          <a-range-picker
            v-model:value="query.dateRange"
            show-time
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item :label="t('operationLog.module')">
          <a-select
            v-model:value="query.moduleCode"
            allow-clear
            :placeholder="t('operationLog.allModules')"
          >
            <a-select-option
              v-for="module in catalog"
              :key="module.code"
              :value="module.code"
            >
              {{ module.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('operationLog.action')">
          <a-select
            v-model:value="query.actionCode"
            allow-clear
            :disabled="!query.moduleCode"
            :placeholder="query.moduleCode ? t('operationLog.allActions') : t('operationLog.selectModuleFirst')"
          >
            <a-select-option
              v-for="action in actionOptions"
              :key="action.code"
              :value="action.code"
            >
              {{ action.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('operationLog.operator')">
          <RemoteResourceSelect
            v-model="query.subjectId"
            :load-options="operationLogApi.operatorOptions"
            :placeholder="t('operationLog.allOperators')"
            :empty-text="t('operationLog.operatorEmpty')"
            :selected-option="selectedOperator"
            @load-error="message.error(errorMessage($event, 'operationLog.operatorLoadFailed'))"
          />
        </a-form-item>
        <a-form-item :label="t('operationLog.traceId')">
          <a-input
            v-model:value="query.traceId"
            allow-clear
          />
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer-actions">
          <a-button @click="reset">
            {{ t("common.reset") }}
          </a-button>
          <a-button
            type="primary"
            @click="search"
          >
            {{ t("common.query") }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="detailOpen"
      :title="t('operationLog.detailsTitle')"
      placement="right"
      :width="680"
      class="responsive-drawer"
    >
      <a-skeleton
        v-if="detailLoading"
        active
      />
      <template v-else-if="detail">
        <a-descriptions
          :column="1"
          size="small"
        >
          <a-descriptions-item :label="t('operationLog.summary')">
            {{ detail.summary }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('operationLog.result')">
            <a-tag :color="resultColor(detail.result)">
              {{ resultLabel(detail.result) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item :label="t('operationLog.createdAt')">
            {{ formatDateTime(detail.createdAt) }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('operationLog.operation')">
            {{ detail.moduleName }} / {{ detail.actionName }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('operationLog.operator')">
            <ResourceLink
              v-if="detail.actorReference"
              :label="detail.actorReference.label"
              :to="referencePath(detail.actorReference)"
              :navigation-message="detail.actorReference.navigationMessage ?? undefined"
            />
            <span v-else>{{ operatorName(detail) }}</span>
          </a-descriptions-item>
          <a-descriptions-item :label="t('operationLog.target')">
            <ResourceLink
              v-if="detail.targetReference"
              :label="detail.targetReference.label"
              :to="referencePath(detail.targetReference)"
              :navigation-message="detail.targetReference.navigationMessage ?? undefined"
            />
            <span v-else>{{ t("common.unavailable") }}</span>
          </a-descriptions-item>
          <a-descriptions-item :label="t('operationLog.requestUri')">
            {{ detail.requestUri }}
          </a-descriptions-item>
        </a-descriptions>
        <a-tabs class="detail-tabs">
          <a-tab-pane
            key="payload"
            :tab="t('operationLog.payload')"
          >
            <JsonPreview :value="detail.payload" />
          </a-tab-pane>
          <a-tab-pane
            key="request"
            :tab="t('operationLog.request')"
          >
            <JsonPreview :value="detail.request" />
          </a-tab-pane>
          <a-tab-pane
            key="actor"
            :tab="t('operationLog.operator')"
          >
            <ResourceLink
              v-if="detail.actorReference"
              :label="detail.actorReference.label"
              :to="referencePath(detail.actorReference)"
              :navigation-message="detail.actorReference.navigationMessage ?? undefined"
            />
            <a-empty
              v-else
              :description="t('common.unavailable')"
            />
          </a-tab-pane>
          <a-tab-pane
            key="target"
            :tab="t('operationLog.target')"
          >
            <ResourceLink
              v-if="detail.targetReference"
              :label="detail.targetReference.label"
              :to="referencePath(detail.targetReference)"
              :navigation-message="detail.targetReference.navigationMessage ?? undefined"
            />
            <a-empty
              v-else
              :description="t('common.unavailable')"
            />
          </a-tab-pane>
          <a-tab-pane
            key="error"
            :tab="t('operationLog.error')"
          >
            <JsonPreview :value="detail.error" />
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.operation-workspace {
  overflow: hidden;
}

.operation-workspace > :deep(.query-toolbar) {
  margin-bottom: 0;
  padding: 12px 14px;
  border-bottom: 1px solid var(--app-border);
}

.drawer-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.operation-table :deep(.ant-table-container) {
  border-radius: 0;
}

.operation-summary {
  color: var(--app-text);
  font-weight: 500;
}

.operation-name {
  color: var(--app-text-secondary);
}

.operation-mobile {
  padding: 12px;
}

.operation-mobile__meta {
  display: grid;
  margin: 12px 0 0;
  grid-template-columns: minmax(88px, auto) minmax(0, 1fr);
  gap: 8px 12px;
}

.operation-mobile__meta dt {
  color: var(--app-text-tertiary);
}

.operation-mobile__meta dd {
  min-width: 0;
  margin: 0;
  color: var(--app-text-secondary);
  overflow-wrap: anywhere;
}

.detail-tabs {
  margin-top: 18px;
}


@media (max-width: 900px) {
  .operation-workspace {
    background: transparent;
    border: 0;
  }

  .operation-mobile {
    padding: 0;
  }

  .drawer-footer-actions > * {
    min-height: 44px;
    flex: 1;
  }
}
</style>
