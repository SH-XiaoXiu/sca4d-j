<script setup lang="ts">
defineOptions({ name: "PlatformSchedulesPage" });

import {
  createIdempotencyKey,
  formatDateTime,
  isApiError,
  normalizePageData,
  type DictionaryOptionView,
  type Id,
  type PageResult,
  type ScheduleExecutionStatus,
  type ScheduleExecutionView,
  type ScheduleStatus,
  type ScheduleTaskTypeView,
  type ScheduleTriggerSource,
  type ScheduleType,
  type ScheduleView,
  type ManagedAccountView
} from "@sca4d/admin-common";
import { validate as validateQuartzCron } from "@joshoy/quartz-cron-parser";
import dayjs, { type Dayjs } from "dayjs";
import { CalendarClock, CirclePlus, Filter, History, Pencil, Play, RotateCcw, Search, Trash2 } from "lucide-vue-next";
import { message, Modal, type FormInstance, type TablePaginationConfig } from "ant-design-vue";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter, type LocationQuery } from "vue-router";
import { platformAccountApi, platformScheduleApi, systemDictionaryApi } from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import QueryToolbar from "@/components/page/QueryToolbar.vue";
import RemoteResourceSelect, { type RemoteResourceOption } from "@/components/data-entry/RemoteResourceSelect.vue";
import ResourceLink from "@/components/data-display/ResourceLink.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import { routePositiveInteger, routeText, useRouteQueryState } from "@/composables/useRouteQueryState";
import { useSessionStore } from "@/stores/session";

interface ScheduleForm {
  name: string;
  scheduleType: ScheduleType;
  executeAt: Dayjs | null;
  cronExpression: string;
  timeZone: string;
  status: ScheduleStatus;
  taskType: string;
  title: string;
  content: string;
  category: "SYSTEM";
  recipientIds: Id[];
}

interface SchedulePageQuery {
  page: number;
  size: number;
  keyword: string;
  scheduleType: ScheduleType | undefined;
  status: ScheduleStatus | undefined;
  taskType: string | undefined;
}

const { t } = useI18n();
const auth = useSessionStore();
const route = useRoute();
const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);
const submitting = ref(false);
const records = ref<ScheduleView[]>([]);
const total = ref(0);
const taskTypes = ref<ScheduleTaskTypeView[]>([]);
const categoryOptions = ref<DictionaryOptionView[]>([]);
const filterOpen = ref(false);
const editorOpen = ref(false);
const detailOpen = computed(() => Boolean(route.params.scheduleId) && typeof route.query.executionId !== "string");
const executionListOpen = ref(false);
const executionsOpen = computed(() => executionListOpen.value
  || (Boolean(route.params.scheduleId) && typeof route.query.executionId === "string"));
const editing = ref<ScheduleView | null>(null);
const editingEtag = ref<string | null>(null);
const detail = ref<ScheduleView | null>(null);
const executions = ref<ScheduleExecutionView[]>([]);
const executionsLoading = ref(false);
const executionPage = ref(1);
const executionTotal = ref(0);
let executionRequest = 0;
let listController: AbortController | null = null;
let detailController: AbortController | null = null;
let executionsController: AbortController | null = null;
let executionController: AbortController | null = null;
const createKey = ref<string | null>(null);
const executeKeys = ref(new Map<Id, string>());
const selectedRecipients = ref(new Map<Id, ManagedAccountView>());
const queryState = useRouteQueryState<SchedulePageQuery>({
  decode(value: LocationQuery) {
    const scheduleType = routeText(value, "scheduleType");
    const status = routeText(value, "status");
    return {
      page: routePositiveInteger(value, "page", 1),
      size: routePositiveInteger(value, "size", 20),
      keyword: routeText(value, "keyword") ?? "",
      scheduleType: scheduleType === "ONCE" || scheduleType === "CRON" ? scheduleType : undefined,
      status: status === "ACTIVE" || status === "PAUSED" || status === "COMPLETED"
        || status === "FAILED" || status === "CANCELLED" ? status : undefined,
      taskType: routeText(value, "taskType")
    };
  },
  encode(value) {
    return {
      ...(value.page === 1 ? {} : { page: String(value.page) }),
      ...(value.size === 20 ? {} : { size: String(value.size) }),
      ...(value.keyword ? { keyword: value.keyword } : {}),
      ...(value.scheduleType ? { scheduleType: value.scheduleType } : {}),
      ...(value.status ? { status: value.status } : {}),
      ...(value.taskType ? { taskType: value.taskType } : {})
    };
  }
}, load);
const query = queryState.draft;
const form = reactive<ScheduleForm>({ name: "", scheduleType: "ONCE", executeAt: null, cronExpression: "", timeZone: "Asia/Shanghai", status: "ACTIVE", taskType: "notification.sse", title: "", content: "", category: "SYSTEM", recipientIds: [] });

const canManage = computed(() => auth.hasPermission("platform:schedules:manage"));
const cronInvalid = computed(() => form.scheduleType === "CRON" && form.cronExpression.trim().length > 0 && !validateQuartzCron(form.cronExpression.trim()));
const timeZones = typeof Intl.supportedValuesOf === "function"
  ? Intl.supportedValuesOf("timeZone")
  : ["UTC", "Asia/Shanghai"];
const recipientOptions = computed<RemoteResourceOption[]>(() => form.recipientIds.map((id) => {
  const recipient = selectedRecipients.value.get(id);
  return { id, label: recipient?.displayName ?? t("common.unavailable"), description: recipient?.accountId ?? null };
}));
const columns = computed(() => [
  { title: t("schedule.name"), key: "name", width: 250 },
  { title: t("schedule.type"), key: "scheduleType", width: 120 },
  { title: t("schedule.status"), key: "status", width: 110 },
  { title: t("schedule.executeAt"), key: "nextTime", width: 190 },
  { title: t("common.updatedAt"), key: "updatedAt", width: 180 },
  { title: t("common.actions"), key: "actions", width: 300, fixed: "right" as const }
]);
const executionColumns = computed(() => [
  { title: t("schedule.triggerSource"), key: "source", width: 120 },
  { title: t("schedule.status"), key: "status", width: 110 },
  { title: t("schedule.fireTime"), key: "fireTime", width: 180 },
  { title: t("schedule.result"), key: "result" }
]);

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function statusLabel(status: ScheduleStatus | ScheduleExecutionStatus): string {
  return t(`schedule.statuses.${status}`);
}

function statusColor(status: ScheduleStatus | ScheduleExecutionStatus): string {
  if (status === "ACTIVE" || status === "SUCCESS") return "green";
  if (status === "FAILED" || status === "CANCELLED") return "red";
  if (status === "RUNNING" || status === "PENDING") return "blue";
  return "default";
}

function scheduleTypeLabel(type: ScheduleType): string {
  return t(type === "ONCE" ? "schedule.once" : "schedule.cron");
}

function triggerSourceLabel(source: ScheduleTriggerSource): string {
  if (source === "MANUAL") return t("schedule.manual");
  return t(source === "RECOVERY" ? "schedule.recovery" : "schedule.scheduled");
}

function categoryLabel(category: string): string {
  return categoryOptions.value.find((option) => option.value === category)?.label ?? category;
}

function plannedTime(schedule: ScheduleView): string {
  return schedule.scheduleType === "ONCE"
    ? formatDateTime(schedule.executeAt)
    : `${schedule.cronExpression ?? "--"} · ${schedule.timeZone ?? "--"}`;
}

async function load(value: Readonly<SchedulePageQuery>): Promise<void> {
  listController?.abort();
  const requestController = new AbortController();
  listController = requestController;
  loading.value = true;
  try {
    const page = normalizePageData(await platformScheduleApi.page({
      page: value.page,
      size: value.size,
      ...(value.keyword ? { keyword: value.keyword } : {}),
      ...(value.scheduleType ? { scheduleType: value.scheduleType } : {}),
      ...(value.status ? { status: value.status } : {}),
      ...(value.taskType ? { taskType: value.taskType } : {})
    }, requestController.signal));
    if (requestController.signal.aborted || listController !== requestController) return;
    records.value = page.records;
    total.value = page.total;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "schedule.loadFailed"));
    }
  } finally {
    if (listController === requestController) loading.value = false;
  }
}

async function loadCatalog(): Promise<void> {
  try {
    const [types, selections] = await Promise.all([
      platformScheduleApi.taskTypes(),
      systemDictionaryApi.options(["notification.category"])
    ]);
    taskTypes.value = types;
    categoryOptions.value = selections.find((selection) => selection.code === "notification.category")?.options ?? [];
  } catch (error) {
    message.error(errorMessage(error, "schedule.catalogLoadFailed"));
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

function emptyForm(): void {
  Object.assign(form, { name: "", scheduleType: "ONCE", executeAt: null, cronExpression: "", timeZone: "Asia/Shanghai", status: "ACTIVE", taskType: taskTypes.value[0]?.taskType ?? "notification.sse", title: "", content: "", category: "SYSTEM", recipientIds: [] });
  selectedRecipients.value = new Map();
}

function applySchedule(schedule: ScheduleView): void {
  const payload = schedule.taskPayload;
  const snapshots = new Map(selectedRecipients.value);
  payload.recipients.forEach((recipient) => {
    if (!snapshots.has(recipient.principalId)) {
      snapshots.set(recipient.principalId, {
        accountId: recipient.principalId,
        subjectId: recipient.subjectId,
        defaultPrincipalId: recipient.principalId,
        displayName: recipient.principalId,
        primaryIdentifier: null,
        avatar: null,
        subjectVersion: "0",
        profileVersion: "0",
        scopeType: "GLOBAL",
        scopeId: null,
        status: "ACTIVE",
        secondFactorRequired: false,
        version: "0",
        createdAt: "",
        updatedAt: ""
      });
    }
  });
  selectedRecipients.value = snapshots;
  Object.assign(form, {
    name: schedule.name,
    scheduleType: schedule.scheduleType,
    executeAt: schedule.executeAt ? dayjs(schedule.executeAt) : null,
    cronExpression: schedule.cronExpression ?? "",
    timeZone: schedule.timeZone ?? "Asia/Shanghai",
    status: schedule.status,
    taskType: schedule.taskType,
    title: payload.title,
    content: payload.content,
    category: payload.category,
    recipientIds: payload.recipients.map((recipient) => recipient.principalId)
  });
}

function openCreate(): void {
  editing.value = null;
  createKey.value = createIdempotencyKey();
  emptyForm();
  editorOpen.value = true;
}

async function openEdit(schedule: ScheduleView): Promise<void> {
  try {
    const resource = await platformScheduleApi.detail(schedule.scheduleId);
    editing.value = resource.data;
    editingEtag.value = resource.etag;
    if (!editingEtag.value) throw new Error("Schedule detail response is missing ETag");
    applySchedule(editing.value);
    editorOpen.value = true;
  } catch (error) {
    message.error(errorMessage(error, "schedule.detailLoadFailed"));
  }
}

async function openDetail(scheduleId: Id): Promise<void> {
  detailController?.abort();
  const requestController = new AbortController();
  detailController = requestController;
  try {
    const schedule = await platformScheduleApi.detail(scheduleId, requestController.signal);
    if (requestController.signal.aborted
      || detailController !== requestController
      || String(route.params.scheduleId) !== String(scheduleId)) return;
    detail.value = schedule.data;
  } catch (error) {
    if (requestController.signal.aborted || detailController !== requestController) return;
    message.error(errorMessage(error, "schedule.detailLoadFailed"));
    if (route.params.scheduleId) await router.replace({ path: "/platform/schedules", query: scheduleListQuery() });
  }
}

function scheduleListQuery() {
  const query = { ...route.query };
  delete query.executionId;
  return query;
}

async function loadRecipientOptions(search: string, page: number, size: number, signal: AbortSignal): Promise<PageResult<RemoteResourceOption>> {
  void search;
  const result = normalizePageData(await platformAccountApi.page({ page, size, status: "ACTIVE" }, signal));
  const snapshots = new Map(selectedRecipients.value);
  result.records.forEach((account) => snapshots.set(account.defaultPrincipalId, account));
  selectedRecipients.value = snapshots;
  return {
    ...result,
    records: result.records.map((account) => ({
      id: account.defaultPrincipalId,
      label: account.displayName,
      description: account.accountId
    }))
  };
}

function selectRecipients(value: Id | Id[] | undefined): void {
  form.recipientIds = Array.isArray(value) ? value : value == null ? [] : [value];
}

function valid(): boolean {
  if (!form.name.trim() || !form.taskType || !form.title.trim() || !form.content.trim()) {
    message.warning(t("schedule.required"));
    return false;
  }
  if (form.scheduleType === "ONCE" && !form.executeAt) {
    message.warning(t("schedule.executeAtRequired"));
    return false;
  }
  if (form.scheduleType === "CRON" && (!form.cronExpression.trim() || !form.timeZone.trim())) {
    message.warning(t("schedule.cronRequired"));
    return false;
  }
  if (form.scheduleType === "CRON" && cronInvalid.value) {
    message.warning(t("schedule.cronInvalid"));
    return false;
  }
  if (form.recipientIds.length === 0) {
    message.warning(t("schedule.recipientRequired"));
    return false;
  }
  return true;
}

function writeInput() {
  return {
    name: form.name.trim(),
    scheduleType: form.scheduleType,
    ...(form.scheduleType === "ONCE" && form.executeAt ? { executeAt: form.executeAt.toISOString() } : {}),
    ...(form.scheduleType === "CRON" ? { cronExpression: form.cronExpression.trim(), timeZone: form.timeZone.trim() } : {}),
    taskType: form.taskType,
    taskPayload: {
      title: form.title.trim(),
      content: form.content.trim(),
      category: form.category,
      recipients: form.recipientIds.map((principalId) => {
        const account = selectedRecipients.value.get(principalId);
        if (!account) throw new Error("Schedule recipient reference is unavailable");
        return { subjectId: account.subjectId, principalType: "DEFAULT" as const, principalId: account.defaultPrincipalId };
      })
    }
  };
}

async function save(): Promise<void> {
  try { await formRef.value?.validate(); } catch { return; }
  if (!valid()) return;
  submitting.value = true;
  try {
    if (editing.value) {
      if (!editingEtag.value) throw new Error("Schedule edit is missing ETag");
      await platformScheduleApi.update(editing.value.scheduleId, { ...writeInput(), status: form.status }, editingEtag.value);
      message.success(t("schedule.updated"));
    } else {
      createKey.value ??= createIdempotencyKey();
      await platformScheduleApi.create(writeInput(), createKey.value);
      createKey.value = null;
      message.success(t("schedule.created"));
    }
    editorOpen.value = false;
    await queryState.reload();
  } catch (error) {
    if (editing.value && isApiError(error) && (error.httpStatus === 409 || error.httpStatus === 412)) {
      const draft = { ...form, recipientIds: [...form.recipientIds] };
      const latest = await platformScheduleApi.detail(editing.value.scheduleId);
      editing.value = latest.data;
      editingEtag.value = latest.etag;
      Object.assign(form, draft);
      message.warning(t("schedule.conflict"));
      return;
    }
    message.error(errorMessage(error, "schedule.saveFailed"));
  } finally {
    submitting.value = false;
  }
}

function confirmDelete(schedule: ScheduleView): void {
  Modal.confirm({
    title: t("schedule.deleteTitle", { name: schedule.name }),
    content: t("schedule.deleteWarning"),
    okText: t("schedule.delete"),
    cancelText: t("common.cancel"),
    okType: "danger",
    async onOk() {
      try {
        await platformScheduleApi.delete(schedule.scheduleId, schedule.version);
        message.success(t("schedule.deleted"));
        await queryState.reload();
      } catch (error) {
        message.error(errorMessage(error, "schedule.deleteFailed"));
        throw error;
      }
    }
  });
}

function confirmExecute(schedule: ScheduleView): void {
  Modal.confirm({
    title: t("schedule.executeTitle", { name: schedule.name }),
    content: t("schedule.executeWarning"),
    okText: t("schedule.manualExecute"),
    cancelText: t("common.cancel"),
    async onOk() {
      try {
        const keys = new Map(executeKeys.value);
        const key = keys.get(schedule.scheduleId) ?? createIdempotencyKey();
        keys.set(schedule.scheduleId, key);
        executeKeys.value = keys;
        await platformScheduleApi.execute(schedule.scheduleId, key);
        keys.delete(schedule.scheduleId);
        executeKeys.value = new Map(keys);
        message.success(t("schedule.executionCreated"));
      } catch (error) {
        message.error(errorMessage(error, "schedule.executeFailed"));
        throw error;
      }
    }
  });
}

async function openExecutions(schedule: ScheduleView): Promise<void> {
  detail.value = schedule;
  executionPage.value = 1;
  executionListOpen.value = true;
  await loadExecutions();
}

async function openExecution(scheduleId: Id, executionId: Id): Promise<void> {
  const requestId = ++executionRequest;
  executionController?.abort();
  const requestController = new AbortController();
  executionController = requestController;
  executionsLoading.value = true;
  try {
    const [schedule, execution] = await Promise.all([
      platformScheduleApi.detail(scheduleId, requestController.signal),
      platformScheduleApi.execution(scheduleId, executionId, requestController.signal)
    ]);
    if (requestController.signal.aborted
      || executionController !== requestController
      || requestId !== executionRequest
      || String(route.params.scheduleId) !== String(scheduleId)
      || route.query.executionId !== String(executionId)) return;
    detail.value = schedule.data;
    executions.value = [execution];
    executionPage.value = 1;
    executionTotal.value = 1;
  } catch (error) {
    if (requestController.signal.aborted
      || executionController !== requestController
      || requestId !== executionRequest
      || String(route.params.scheduleId) !== String(scheduleId)
      || route.query.executionId !== String(executionId)) return;
    message.error(errorMessage(error, "schedule.executionLoadFailed"));
  } finally {
    if (!requestController.signal.aborted
      && executionController === requestController
      && requestId === executionRequest
      && String(route.params.scheduleId) === String(scheduleId)
      && route.query.executionId === String(executionId)) executionsLoading.value = false;
  }
}

async function loadExecutions(): Promise<void> {
  if (!detail.value) return;
  executionsController?.abort();
  const requestController = new AbortController();
  executionsController = requestController;
  const scheduleId = detail.value.scheduleId;
  const pageNumber = executionPage.value;
  executionsLoading.value = true;
  try {
    const page = normalizePageData(await platformScheduleApi.executions(
      scheduleId,
      { page: pageNumber, size: 20 },
      requestController.signal
    ));
    if (requestController.signal.aborted
      || executionsController !== requestController
      || detail.value?.scheduleId !== scheduleId
      || executionPage.value !== pageNumber) return;
    executions.value = page.records;
    executionTotal.value = page.total;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "schedule.executionLoadFailed"));
    }
  } finally {
    if (executionsController === requestController) executionsLoading.value = false;
  }
}

function closeDetail(): void {
  if (route.params.scheduleId) void router.push({ path: "/platform/schedules", query: scheduleListQuery() });
}

function closeExecutions(): void {
  executionRequest += 1;
  executionController?.abort();
  executionsController?.abort();
  executionsLoading.value = false;
  if (route.params.scheduleId && route.query.executionId) {
    executionListOpen.value = false;
    void router.push({
      path: `/platform/schedules/${String(route.params.scheduleId)}`,
      query: scheduleListQuery()
    });
    return;
  }
  executionListOpen.value = false;
}

watch([() => route.params.scheduleId, () => route.query.executionId], ([scheduleId, executionId]) => {
  if (scheduleId && typeof executionId === "string") {
    detailController?.abort();
    executionsController?.abort();
    void openExecution(String(scheduleId), executionId);
  } else if (scheduleId) {
    executionRequest += 1;
    executionController?.abort();
    executionsController?.abort();
    executionsLoading.value = false;
    void openDetail(String(scheduleId));
  } else {
    executionRequest += 1;
    executionController?.abort();
    detailController?.abort();
    executionsController?.abort();
    executionsLoading.value = false;
  }
}, { immediate: true });

onMounted(() => { void loadCatalog(); });
onBeforeUnmount(() => {
  executionRequest += 1;
  listController?.abort();
  detailController?.abort();
  executionsController?.abort();
  executionController?.abort();
});
</script>

<template>
  <div>
    <PageHeader
      :title="t('route.platformSchedules')"
      :description="t('schedule.description')"
    >
      <template #actions>
        <a-button
          class="mobile-only"
          @click="filterOpen = true"
        >
          <Filter :size="16" />{{ t("common.filter") }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          @click="openCreate"
        >
          <CirclePlus :size="16" />{{ t("schedule.create") }}
        </a-button>
      </template>
    </PageHeader>
    <section class="page-panel management-panel">
      <QueryToolbar class="desktop-only">
        <template #search>
          <a-input
            v-model:value="query.keyword"
            allow-clear
            :placeholder="t('schedule.keywordPlaceholder')"
            @press-enter="applyQuery"
          >
            <template #prefix>
              <Search :size="15" />
            </template>
          </a-input>
        </template>
        <template #filters>
          <a-select
            v-model:value="query.scheduleType"
            allow-clear
            :placeholder="t('schedule.allTypes')"
          >
            <a-select-option value="ONCE">
              {{ t("schedule.once") }}
            </a-select-option>
            <a-select-option value="CRON">
              {{ t("schedule.cron") }}
            </a-select-option>
          </a-select>
          <a-select
            v-model:value="query.status"
            allow-clear
            :placeholder="t('common.allStatuses')"
          >
            <a-select-option
              v-for="status in ['ACTIVE','PAUSED','COMPLETED','FAILED']"
              :key="status"
              :value="status"
            >
              {{ statusLabel(status as ScheduleStatus) }}
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
        row-key="scheduleId"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="{ current: query.page, pageSize: query.size, total, showSizeChanger: true, showTotal: (value: number) => t('common.total', { total: value }) }"
        :scroll="{ x: 1050 }"
        @change="(pagination: TablePaginationConfig) => changePage(pagination.current ?? query.page, pagination.pageSize ?? query.size)"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div class="schedule-name">
              <CalendarClock :size="18" /><div>
                <ResourceLink
                  :label="record.name"
                  :to="{ name: 'platform-schedule-detail', params: { scheduleId: record.scheduleId }, query: route.query }"
                /><span>{{ record.taskType }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'scheduleType'">
            {{ scheduleTypeLabel(record.scheduleType) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'nextTime'">
            {{ plannedTime(record) }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space :size="2">
              <RowActionButton
                :label="t('schedule.executions')"
                @click="openExecutions(record)"
              >
                <History :size="17" />
              </RowActionButton><RowActionButton
                v-if="canManage && record.status === 'ACTIVE'"
                :label="t('schedule.manualExecute')"
                @click="confirmExecute(record)"
              >
                <Play :size="17" />
              </RowActionButton><RowActionButton
                v-if="canManage && !['COMPLETED','FAILED','CANCELLED'].includes(record.status)"
                :label="t('common.edit')"
                @click="openEdit(record)"
              >
                <Pencil :size="17" />
              </RowActionButton><RowActionButton
                v-if="canManage"
                :label="t('common.delete')"
                danger
                @click="confirmDelete(record)"
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
            :description="t('schedule.empty')"
          /><div
            v-else
            class="mobile-card-list"
          >
            <article
              v-for="record in records"
              :key="record.scheduleId"
              class="mobile-record"
            >
              <div class="mobile-record__header">
                <ResourceLink
                  class="mobile-record__title"
                  :label="record.name"
                  :to="{ name: 'platform-schedule-detail', params: { scheduleId: record.scheduleId }, query: route.query }"
                /><a-tag :color="statusColor(record.status)">
                  {{ statusLabel(record.status) }}
                </a-tag>
              </div><div class="mobile-record__meta">
                <span>{{ t("schedule.type") }}</span><strong>{{ scheduleTypeLabel(record.scheduleType) }}</strong><span>{{ t("schedule.executeAt") }}</span><strong>{{ plannedTime(record) }}</strong><span>{{ t("common.updatedAt") }}</span><strong>{{ formatDateTime(record.updatedAt) }}</strong>
              </div><div class="mobile-record__actions">
                <RowActionButton
                  :label="t('schedule.executions')"
                  @click="openExecutions(record)"
                >
                  <History :size="17" />
                </RowActionButton><RowActionButton
                  v-if="canManage && record.status === 'ACTIVE'"
                  :label="t('schedule.manualExecute')"
                  @click="confirmExecute(record)"
                >
                  <Play :size="17" />
                </RowActionButton><RowActionButton
                  v-if="canManage && !['COMPLETED','FAILED','CANCELLED'].includes(record.status)"
                  :label="t('common.edit')"
                  @click="openEdit(record)"
                >
                  <Pencil :size="17" />
                </RowActionButton><RowActionButton
                  v-if="canManage"
                  danger
                  :label="t('common.delete')"
                  @click="confirmDelete(record)"
                >
                  <Trash2 :size="17" />
                </RowActionButton>
              </div>
            </article>
          </div>
        </a-spin><a-pagination
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
      :title="t('schedule.filterTitle')"
      class="responsive-drawer"
      placement="bottom"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('schedule.name')">
          <a-input
            v-model:value="query.keyword"
            allow-clear
          />
        </a-form-item><a-form-item :label="t('schedule.type')">
          <a-select
            v-model:value="query.scheduleType"
            allow-clear
          >
            <a-select-option value="ONCE">
              {{ t("schedule.once") }}
            </a-select-option><a-select-option value="CRON">
              {{ t("schedule.cron") }}
            </a-select-option>
          </a-select>
        </a-form-item><a-form-item :label="t('schedule.status')">
          <a-select
            v-model:value="query.status"
            allow-clear
          >
            <a-select-option
              v-for="status in ['ACTIVE','PAUSED','COMPLETED','FAILED']"
              :key="status"
              :value="status"
            >
              {{ statusLabel(status as ScheduleStatus) }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form><template #footer>
        <div class="drawer-actions">
          <a-button @click="resetQuery">
            {{ t("common.reset") }}
          </a-button><a-button
            type="primary"
            @click="applyQuery"
          >
            {{ t("schedule.applyFilter") }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="editorOpen"
      :title="t(editing ? 'schedule.edit' : 'schedule.create')"
      :width="680"
      class="responsive-drawer"
    >
      <a-form
        ref="formRef"
        :model="form"
        layout="vertical"
      >
        <a-form-item
          :label="t('schedule.name')"
          name="name"
          :rules="[{ required: true, message: t('schedule.required') }]"
        >
          <a-input
            v-model:value="form.name"
            :maxlength="200"
            :placeholder="t('schedule.namePlaceholder')"
          />
        </a-form-item><div class="form-grid">
          <a-form-item :label="t('schedule.type')">
            <a-segmented
              v-model:value="form.scheduleType"
              :options="[{ label: t('schedule.once'), value: 'ONCE' }, { label: t('schedule.cron'), value: 'CRON' }]"
            />
          </a-form-item><a-form-item
            v-if="editing"
            :label="t('schedule.status')"
          >
            <a-select v-model:value="form.status">
              <a-select-option value="ACTIVE">
                {{ statusLabel('ACTIVE') }}
              </a-select-option><a-select-option value="PAUSED">
                {{ statusLabel('PAUSED') }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </div><a-form-item
          v-if="form.scheduleType === 'ONCE'"
          :label="t('schedule.executeAt')"
        >
          <a-date-picker
            v-model:value="form.executeAt"
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            :show-now="false"
          />
        </a-form-item><div
          v-else
          class="form-grid"
        >
          <a-form-item
            :label="t('schedule.cronExpression')"
            :validate-status="cronInvalid ? 'error' : undefined"
            :help="cronInvalid ? t('schedule.cronInvalid') : undefined"
          >
            <a-input
              v-model:value="form.cronExpression"
              :placeholder="t('schedule.cronPlaceholder')"
            />
          </a-form-item><a-form-item :label="t('schedule.timeZone')">
            <a-select
              v-model:value="form.timeZone"
              show-search
              option-filter-prop="label"
              :placeholder="t('schedule.timeZonePlaceholder')"
              :options="timeZones.map((zone) => ({ value: zone, label: zone }))"
            />
          </a-form-item>
        </div><a-divider /><a-form-item :label="t('schedule.taskType')">
          <a-select v-model:value="form.taskType">
            <a-select-option
              v-for="taskType in taskTypes"
              :key="taskType.taskType"
              :value="taskType.taskType"
            >
              {{ taskType.displayName }}
            </a-select-option>
          </a-select>
        </a-form-item><template v-if="form.taskType === 'notification.sse'">
          <a-form-item :label="t('schedule.notificationTitle')">
            <a-input
              v-model:value="form.title"
              :maxlength="200"
            />
          </a-form-item><a-form-item :label="t('schedule.notificationContent')">
            <a-textarea
              v-model:value="form.content"
              :rows="5"
              :maxlength="2000"
              show-count
            />
          </a-form-item><a-form-item :label="t('schedule.notificationCategory')">
            <a-select
              v-model:value="form.category"
              :options="categoryOptions.map((option) => ({ value: option.value, label: option.label, disabled: option.disabled }))"
            />
          </a-form-item><a-form-item :label="t('schedule.recipients')">
            <RemoteResourceSelect
              :model-value="form.recipientIds"
              multiple
              :selected-options="recipientOptions"
              :load-options="loadRecipientOptions"
              :placeholder="t('schedule.recipientPlaceholder')"
              :empty-text="t('schedule.recipientEmpty')"
              @update:model-value="selectRecipients"
              @load-error="message.error(errorMessage($event, 'schedule.recipientLoadFailed'))"
            />
          </a-form-item>
        </template>
      </a-form><template #footer>
        <div class="drawer-actions">
          <a-button @click="editorOpen = false">
            {{ t("common.cancel") }}
          </a-button><a-button
            type="primary"
            :loading="submitting"
            @click="save"
          >
            {{ t("common.save") }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer
      :open="detailOpen"
      :title="detail ? detail.name : t('schedule.details')"
      :width="680"
      class="responsive-drawer"
      @close="closeDetail"
    >
      <template v-if="detail">
        <a-alert
          v-if="detail.synchronizationError"
          type="error"
          show-icon
          :message="t('schedule.synchronizationError')"
          :description="detail.synchronizationError"
        /><a-descriptions
          :column="1"
          bordered
          size="small"
        >
          <a-descriptions-item :label="t('schedule.type')">
            {{ scheduleTypeLabel(detail.scheduleType) }}
          </a-descriptions-item><a-descriptions-item :label="t('schedule.status')">
            <a-tag :color="statusColor(detail.status)">
              {{ statusLabel(detail.status) }}
            </a-tag>
          </a-descriptions-item><a-descriptions-item :label="t('schedule.executeAt')">
            {{ plannedTime(detail) }}
          </a-descriptions-item><a-descriptions-item :label="t('schedule.taskType')">
            {{ detail.taskType }}
          </a-descriptions-item><a-descriptions-item :label="t('schedule.notificationTitle')">
            {{ detail.taskPayload.title }}
          </a-descriptions-item><a-descriptions-item :label="t('schedule.notificationContent')">
            <span class="preserve-lines">{{ detail.taskPayload.content }}</span>
          </a-descriptions-item><a-descriptions-item :label="t('schedule.notificationCategory')">
            {{ categoryLabel(detail.taskPayload.category) }}
          </a-descriptions-item><a-descriptions-item :label="t('schedule.recipients')">
            {{ detail.taskPayload.recipients.length }}
          </a-descriptions-item><a-descriptions-item :label="t('common.updatedAt')">
            {{ formatDateTime(detail.updatedAt) }}
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>

    <a-drawer
      :open="executionsOpen"
      :title="t('schedule.executions')"
      :width="760"
      class="responsive-drawer"
      @close="closeExecutions"
    >
      <a-table
        row-key="executionId"
        :columns="executionColumns"
        :data-source="executions"
        :loading="executionsLoading"
        :pagination="{ current: executionPage, pageSize: 20, total: executionTotal }"
        :scroll="{ x: 650 }"
        @change="(pagination: TablePaginationConfig) => { executionPage = pagination.current ?? executionPage; loadExecutions(); }"
      >
        <template #emptyText>
          {{ t("schedule.executionEmpty") }}
        </template><template #bodyCell="{ column, record }">
          <template v-if="column.key === 'source'">
            {{ triggerSourceLabel(record.triggerSource) }}
          </template><template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template><template v-else-if="column.key === 'fireTime'">
            {{ formatDateTime(record.actualFireTime ?? record.scheduledFireTime ?? record.createdAt) }}
          </template><template v-else-if="column.key === 'result'">
            {{ record.resultSummary ?? record.safeErrorMessage ?? '--' }}
          </template>
        </template>
      </a-table>
    </a-drawer>
  </div>
</template>

<style scoped>
.schedule-name { display: flex; align-items: center; gap: 10px; }
.schedule-name > svg { flex: 0 0 auto; color: var(--app-primary); }
.schedule-name > div { display: grid; min-width: 0; gap: 2px; }
.schedule-name span { color: var(--app-text-tertiary); font-size: 12px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.preserve-lines { white-space: pre-wrap; overflow-wrap: anywhere; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 8px; }
@media (max-width: 560px) { .form-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
