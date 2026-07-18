<script setup lang="ts">
defineOptions({ name: "DictionaryManagementPage" });

import {
  createIdempotencyKey,
  formatDateTime,
  isApiError,
  normalizePageData,
  type DictionaryApi,
  type DictionaryKind,
  type DictionaryOptionView,
  type DictionaryOrderItemView,
  type DictionaryStatus,
  type DictionaryTone,
  type ManagedDictionaryItemView,
  type ManagedDictionaryView
} from "@sca4d/admin-common";
import {
  ArrowUpDown,
  BookOpenText,
  CirclePlus,
  Filter,
  ListTree,
  Pencil,
  RotateCcw,
  Search,
  Trash2
} from "lucide-vue-next";
import { message, Modal, type TablePaginationConfig } from "ant-design-vue";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter, type LocationQuery } from "vue-router";
import {
  platformTenantDictionaryApi,
  systemDictionaryApi,
  tenantDictionaryApi
} from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import QueryToolbar from "@/components/page/QueryToolbar.vue";
import LocalizedTextEditor from "@/components/data-entry/LocalizedTextEditor.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import SortableOrderList from "@/components/data-entry/SortableOrderList.vue";
import { routePositiveInteger, routeText, useRouteQueryState } from "@/composables/useRouteQueryState";
import { useSessionStore } from "@/stores/session";
import { useLocaleStore } from "@/stores/locale";
import { useLanguageStore } from "@/stores/languages";

interface DictionaryPageQuery {
  page: number;
  size: number;
  search: string;
  kind: DictionaryKind | undefined;
  status: DictionaryStatus | undefined;
}

const auth = useSessionStore();
const locale = useLocaleStore();
const languageStore = useLanguageStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const loading = ref(false);
const submitting = ref(false);
const records = ref<ManagedDictionaryView[]>([]);
const total = ref(0);
const filterOpen = ref(false);
const editorOpen = ref(false);
const editing = ref<ManagedDictionaryView | null>(null);
const detailOpen = computed(() => typeof route.params.dictionaryId === "string" && Boolean(route.params.dictionaryId));
const detail = ref<ManagedDictionaryView | null>(null);
const itemsLoading = ref(false);
const items = ref<ManagedDictionaryItemView[]>([]);
const itemTotal = ref(0);
const itemEditorOpen = ref(false);
const editingItem = ref<ManagedDictionaryItemView | null>(null);
const orderOpen = ref(false);
const orderLoading = ref(false);
const orderKind = ref<"types" | "items">("types");
const orderDictionaryId = ref<string | null>(null);
const orderItems = ref<DictionaryOrderItemView[]>([]);
const optionCatalog = ref(new Map<string, DictionaryOptionView[]>());
const queryState = useRouteQueryState<DictionaryPageQuery>({
  decode(value: LocationQuery) {
    const kind = routeText(value, "kind");
    const status = routeText(value, "status");
    return {
      page: routePositiveInteger(value, "page", 1),
      size: routePositiveInteger(value, "size", 20),
      search: routeText(value, "search") ?? "",
      kind: kind === "CODE_ENUM" || kind === "BUSINESS_CODE" || kind === "INPUT_SUGGESTION"
        ? kind
        : undefined,
      status: status === "ACTIVE" || status === "DISABLED" ? status : undefined
    };
  },
  encode(value) {
    return {
      ...(value.page === 1 ? {} : { page: String(value.page) }),
      ...(value.size === 20 ? {} : { size: String(value.size) }),
      ...(value.search ? { search: value.search } : {}),
      ...(value.kind ? { kind: value.kind } : {}),
      ...(value.status ? { status: value.status } : {})
    };
  }
}, load);
const query = queryState.draft;
const itemQuery = reactive({ page: 1, size: 20, search: "", status: undefined as DictionaryStatus | undefined });
const form = reactive({
  code: "",
  kind: "BUSINESS_CODE" as Exclude<DictionaryKind, "CODE_ENUM">,
  labels: { "zh-CN": "" } as Record<string, string>,
  status: "ACTIVE" as DictionaryStatus
});
const itemForm = reactive({
  value: "",
  labels: { "zh-CN": "" } as Record<string, string>,
  tone: "DEFAULT" as DictionaryTone,
  selectable: true,
  status: "ACTIVE" as DictionaryStatus
});
let listController: AbortController | null = null;
let itemController: AbortController | null = null;
let optionController: AbortController | null = null;
let detailController: AbortController | null = null;

const tenantId = computed(() => String(route.params.tenantId ?? ""));
const scope = computed<"system" | "platform-tenant" | "tenant">(() => {
  if (String(route.name).startsWith("platform-tenant-dictionar")) return "platform-tenant";
  if (String(route.name).startsWith("tenant-dictionar")) return "tenant";
  return "system";
});
const api = computed<DictionaryApi>(() => {
  if (scope.value === "platform-tenant") return platformTenantDictionaryApi(tenantId.value);
  if (scope.value === "tenant") return tenantDictionaryApi(tenantId.value);
  return systemDictionaryApi;
});
const managePermission = computed(() => {
  if (scope.value === "platform-tenant") return "platform:tenants:dictionaries:manage";
  if (scope.value === "tenant") return "tenant:dictionaries:manage";
  return "system:dictionaries:manage";
});
const canManage = computed(() => auth.hasPermission(managePermission.value));
const title = computed(() => t(scope.value === "system" ? "dictionary.title" : "dictionary.tenantTitle"));
const kindOptions = computed(() => optionCatalog.value.get("system.dictionary-kind") ?? []);
const configurableKindOptions = computed(() => kindOptions.value.filter((option) => option.value !== "CODE_ENUM"));
const statusOptions = computed(() => optionCatalog.value.get("system.dictionary-status") ?? []);
const toneOptions = computed(() => optionCatalog.value.get("system.dictionary-tone") ?? []);

const columns = computed(() => [
  { title: t("dictionary.label"), key: "dictionary", width: 280 },
  { title: t("dictionary.kind"), key: "kind", width: 150 },
  { title: t("dictionary.status"), key: "status", width: 100 },
  { title: t("common.updatedAt"), key: "updatedAt", width: 180 },
  { title: t("common.actions"), key: "actions", width: 180, fixed: "right" as const }
]);
const itemColumns = computed(() => [
  { title: t("dictionary.itemLabel"), key: "item", width: 260 },
  { title: t("dictionary.tone"), dataIndex: "tone", key: "tone", width: 120 },
  { title: t("dictionary.status"), key: "status", width: 100 },
  { title: t("common.actions"), key: "actions", width: 120, fixed: "right" as const }
]);

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function kindLabel(kind: DictionaryKind): string {
  return kindOptions.value.find((option) => option.value === kind)?.label ?? "—";
}

function statusLabel(status: DictionaryStatus): string {
  return statusOptions.value.find((option) => option.value === status)?.label ?? "—";
}

function toneLabel(tone: DictionaryTone): string {
  return toneOptions.value.find((option) => option.value === tone)?.label ?? "—";
}

function optionColor(code: string, value: string): string {
  const tone = optionCatalog.value.get(code)?.find((option) => option.value === value)?.tone;
  if (tone === "SUCCESS") return "success";
  if (tone === "WARNING") return "warning";
  if (tone === "DANGER") return "error";
  if (tone === "INFO") return "processing";
  return "default";
}

async function loadOptionCatalog(): Promise<void> {
  optionController?.abort();
  const requestController = new AbortController();
  optionController = requestController;
  try {
    const selections = await api.value.options(
      ["system.dictionary-kind", "system.dictionary-status", "system.dictionary-tone"],
      requestController.signal
    );
    if (requestController.signal.aborted || optionController !== requestController) return;
    optionCatalog.value = new Map(selections.map((selection) => [selection.code, selection.options]));
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "dictionary.loadFailed"));
    }
  }
}

function labels(input: Record<string, string>): Record<string, string> {
  const values: Record<string, string> = {};
  for (const [language, value] of Object.entries(input)) {
    if (value.trim()) values[language] = value.trim();
  }
  return values;
}

function emptyLabels(): Record<string, string> {
  return Object.fromEntries(languageStore.languages.map((language) => [language.code, ""]));
}

function hasRequiredLabels(input: Record<string, string>): boolean {
  return languageStore.loaded
    && languageStore.requiredLanguages.every((language) => input[language.code]?.trim());
}

async function load(value: Readonly<DictionaryPageQuery>): Promise<void> {
  listController?.abort();
  const requestController = new AbortController();
  listController = requestController;
  loading.value = true;
  try {
    const page = normalizePageData(
      await api.value.page(
        {
          page: value.page,
          size: value.size,
          ...(value.search ? { search: value.search } : {}),
          ...(value.kind ? { kind: value.kind } : {}),
          ...(value.status ? { status: value.status } : {})
        },
        requestController.signal
      )
    );
    if (requestController.signal.aborted || listController !== requestController) return;
    records.value = page.records;
    total.value = page.total;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "dictionary.loadFailed"));
    }
  } finally {
    if (listController === requestController) loading.value = false;
  }
}

function openCreate(): void {
  editing.value = null;
  Object.assign(form, {
    code: "",
    kind: "BUSINESS_CODE",
    labels: emptyLabels(),
    status: "ACTIVE"
  });
  editorOpen.value = true;
}

function openEdit(dictionary: ManagedDictionaryView): void {
  editing.value = dictionary;
  Object.assign(form, {
    code: dictionary.code,
    kind: dictionary.kind === "INPUT_SUGGESTION" ? "INPUT_SUGGESTION" : "BUSINESS_CODE",
    labels: { ...dictionary.labels },
    status: dictionary.status
  });
  editorOpen.value = true;
}

async function saveDictionary(): Promise<void> {
  if (!hasRequiredLabels(form.labels) || (!editing.value && !form.code.trim())) {
    message.warning(t("dictionary.required"));
    return;
  }
  submitting.value = true;
  try {
    if (editing.value) {
      await api.value.update(editing.value.id, {
        labels: labels(form.labels),
        status: form.status,
        version: editing.value.version
      });
      message.success(t("dictionary.updateSuccess"));
    } else {
      await api.value.create({
        code: form.code.trim(),
        kind: form.kind,
        labels: labels(form.labels),
        idempotencyKey: createIdempotencyKey()
      });
      message.success(t("dictionary.createSuccess"));
    }
    editorOpen.value = false;
    await queryState.reload();
  } catch (error) {
    if (editing.value && isApiError(error) && error.httpStatus === 409) {
      const id = editing.value.id;
      await queryState.reload();
      editing.value = records.value.find((record) => record.id === id) ?? editing.value;
      message.warning(t("dictionary.conflict"));
      return;
    }
    message.error(errorMessage(error, "dictionary.saveFailed"));
  } finally {
    submitting.value = false;
  }
}

function detailRoute(dictionaryId: string): string {
  if (scope.value === "platform-tenant") return `/platform/tenants/${tenantId.value}/dictionaries/${dictionaryId}`;
  if (scope.value === "tenant") return `/tenant/tenants/${tenantId.value}/dictionaries/${dictionaryId}`;
  return `/system/dictionaries/${dictionaryId}`;
}

function listRoute(): string {
  if (scope.value === "platform-tenant") return `/platform/tenants/${tenantId.value}/dictionaries`;
  if (scope.value === "tenant") return `/tenant/tenants/${tenantId.value}/dictionaries`;
  return "/system/dictionaries";
}

async function openDetail(dictionary: ManagedDictionaryView): Promise<void> {
  await router.push({ path: detailRoute(dictionary.id), query: route.query });
}

async function loadDetail(dictionaryId: string): Promise<void> {
  detailController?.abort();
  itemController?.abort();
  const requestController = new AbortController();
  detailController = requestController;
  let itemLoadStarted = false;
  itemsLoading.value = true;
  try {
    const dictionary = await api.value.detail(dictionaryId, requestController.signal);
    if (requestController.signal.aborted || detailController !== requestController) return;
    detail.value = dictionary;
    itemLoadStarted = true;
    await loadItems();
  } catch (error) {
    if (requestController.signal.aborted || detailController !== requestController) return;
    message.error(errorMessage(error, "dictionary.loadFailed"));
    await router.replace({ path: listRoute(), query: route.query });
  } finally {
    if (detailController === requestController && !itemLoadStarted) itemsLoading.value = false;
  }
}

async function closeDetail(): Promise<void> {
  await router.push({ path: listRoute(), query: route.query });
}

async function loadItems(): Promise<void> {
  if (!detail.value) return;
  const dictionaryId = detail.value.id;
  itemController?.abort();
  const requestController = new AbortController();
  itemController = requestController;
  itemsLoading.value = true;
  try {
    const page = normalizePageData(
      await api.value.pageItems(
        dictionaryId,
        {
          page: itemQuery.page,
          size: itemQuery.size,
          ...(itemQuery.search ? { search: itemQuery.search } : {}),
          ...(itemQuery.status ? { status: itemQuery.status } : {})
        },
        requestController.signal
      )
    );
    if (requestController.signal.aborted
      || itemController !== requestController
      || detail.value?.id !== dictionaryId) return;
    items.value = page.records;
    itemTotal.value = page.total;
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "dictionary.itemLoadFailed"));
    }
  } finally {
    if (itemController === requestController) itemsLoading.value = false;
  }
}

function openItemCreate(): void {
  editingItem.value = null;
  Object.assign(itemForm, {
    value: "",
    labels: emptyLabels(),
    tone: "DEFAULT",
    selectable: true,
    status: "ACTIVE"
  });
  itemEditorOpen.value = true;
}

function openItemEdit(item: ManagedDictionaryItemView): void {
  editingItem.value = item;
  Object.assign(itemForm, {
    value: item.value,
    labels: { ...item.labels },
    tone: item.tone,
    selectable: item.selectable,
    status: item.status
  });
  itemEditorOpen.value = true;
}

async function saveItem(): Promise<void> {
  if (!detail.value || !itemForm.value.trim() || !hasRequiredLabels(itemForm.labels)) {
    message.warning(t("dictionary.itemRequired"));
    return;
  }
  submitting.value = true;
  try {
    const input = {
      value: itemForm.value.trim(),
      labels: labels(itemForm.labels),
      tone: itemForm.tone,
      selectable: itemForm.selectable
    };
    if (editingItem.value) {
      await api.value.updateItem(detail.value.id, editingItem.value.id, {
        ...input,
        status: itemForm.status,
        version: editingItem.value.version
      });
    } else {
      await api.value.createItem(detail.value.id, {
        ...input,
        idempotencyKey: createIdempotencyKey()
      });
    }
    itemEditorOpen.value = false;
    message.success(t("dictionary.itemSaveSuccess"));
    await loadItems();
  } catch (error) {
    if (editingItem.value && isApiError(error) && error.httpStatus === 409) {
      await loadItems();
      message.warning(t("dictionary.conflict"));
      return;
    }
    message.error(errorMessage(error, "dictionary.itemSaveFailed"));
  } finally {
    submitting.value = false;
  }
}

function confirmDeleteDictionary(dictionary: ManagedDictionaryView): void {
  Modal.confirm({
    title: t("dictionary.deleteTitle", { label: dictionary.label }),
    content: t(
      dictionary.kind === "BUSINESS_CODE"
        ? "dictionary.deleteBusinessWarning"
        : "dictionary.deleteSuggestionWarning"
    ),
    okText: t("common.delete"),
    cancelText: t("common.cancel"),
    okType: "danger",
    async onOk() {
      try {
        await api.value.delete(dictionary.id, dictionary.version, createIdempotencyKey());
        message.success(t("dictionary.deleteSuccess"));
        await queryState.reload();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 409) await queryState.reload();
        message.error(errorMessage(error, "dictionary.deleteFailed"));
        throw error;
      }
    }
  });
}

function confirmDeleteItem(item: ManagedDictionaryItemView): void {
  if (!detail.value) return;
  Modal.confirm({
    title: t("dictionary.deleteTitle", { label: item.label }),
    content: t(
      detail.value.kind === "BUSINESS_CODE"
        ? "dictionary.deleteBusinessWarning"
        : "dictionary.deleteSuggestionWarning"
    ),
    okText: t("common.delete"),
    cancelText: t("common.cancel"),
    okType: "danger",
    async onOk() {
      try {
        await api.value.deleteItem(detail.value!.id, item.id, item.version, createIdempotencyKey());
        message.success(t("dictionary.itemDeleteSuccess"));
        await loadItems();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 409) await loadItems();
        message.error(errorMessage(error, "dictionary.itemDeleteFailed"));
        throw error;
      }
    }
  });
}

async function openTypeOrder(): Promise<void> {
  orderKind.value = "types";
  orderDictionaryId.value = null;
  orderItems.value = [];
  orderOpen.value = true;
  orderLoading.value = true;
  try {
    orderItems.value = await api.value.order();
  } catch (error) {
    orderOpen.value = false;
    message.error(errorMessage(error, "dictionary.orderLoadFailed"));
  } finally {
    orderLoading.value = false;
  }
}

async function openItemOrder(): Promise<void> {
  if (!detail.value) return;
  orderKind.value = "items";
  orderDictionaryId.value = detail.value.id;
  orderItems.value = [];
  orderOpen.value = true;
  orderLoading.value = true;
  try {
    orderItems.value = await api.value.itemOrder(detail.value.id);
  } catch (error) {
    orderOpen.value = false;
    message.error(errorMessage(error, "dictionary.orderLoadFailed"));
  } finally {
    orderLoading.value = false;
  }
}

async function reloadOrderAfterConflict(): Promise<void> {
  const latest = orderKind.value === "types"
    ? await api.value.order()
    : await api.value.itemOrder(orderDictionaryId.value!);
  const currentIds = new Set(orderItems.value.map((item) => item.id));
  const sameMembers = latest.length === orderItems.value.length
    && latest.every((item) => currentIds.has(item.id));
  if (!sameMembers) {
    orderItems.value = latest;
    message.warning(t("dictionary.orderMembersChanged"));
    return;
  }
  const latestById = new Map(latest.map((item) => [item.id, item]));
  orderItems.value = orderItems.value.map((item) => latestById.get(item.id)!);
  message.warning(t("dictionary.orderVersionsChanged"));
}

async function saveOrder(): Promise<void> {
  submitting.value = true;
  try {
    const versions = orderItems.value.map((item) => ({ id: item.id, version: item.version }));
    if (orderKind.value === "types") {
      await api.value.reorder(versions);
      await queryState.reload();
    } else if (orderDictionaryId.value) {
      await api.value.reorderItems(orderDictionaryId.value, versions);
      await loadItems();
    }
    orderOpen.value = false;
    message.success(t("dictionary.sortSuccess"));
  } catch (error) {
    if (isApiError(error) && error.httpStatus === 409) {
      await reloadOrderAfterConflict();
      return;
    }
    message.error(errorMessage(error, "dictionary.sortFailed"));
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

watch(
  () => route.params.dictionaryId,
  (value) => {
    if (typeof value === "string" && value) void loadDetail(value);
    else {
      detailController?.abort();
      itemController?.abort();
      detail.value = null;
      items.value = [];
      itemsLoading.value = false;
    }
  }
);
watch(
  () => [scope.value, tenantId.value],
  () => {
    void loadOptionCatalog();
  }
);
watch(
  () => locale.locale,
  () => {
    void loadOptionCatalog();
    void queryState.reload();
    if (detail.value) void loadDetail(detail.value.id);
  }
);

onMounted(async () => {
  await languageStore.load();
  await loadOptionCatalog();
  const dictionaryId = route.params.dictionaryId;
  if (typeof dictionaryId === "string" && dictionaryId) await loadDetail(dictionaryId);
});
onBeforeUnmount(() => {
  listController?.abort();
  itemController?.abort();
  optionController?.abort();
  detailController?.abort();
});
</script>

<template>
  <div>
    <PageHeader
      :title="title"
      :description="t('dictionary.description')"
    >
      <template #actions>
        <a-button
          class="mobile-only"
          @click="filterOpen = true"
        >
          <Filter :size="16" />{{ t("common.filter") }}
        </a-button>
        <a-button
          v-if="canManage && total > 1"
          @click="openTypeOrder"
        >
          <ArrowUpDown :size="16" />{{ t("dictionary.sort") }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          @click="openCreate"
        >
          <CirclePlus :size="16" />{{ t("dictionary.create") }}
        </a-button>
      </template>
    </PageHeader>

    <section class="page-panel management-panel">
      <QueryToolbar class="desktop-only">
        <template #search>
          <a-input
            v-model:value="query.search"
            allow-clear
            :placeholder="t('dictionary.code')"
            @press-enter="applyQuery"
          >
            <template #prefix>
              <Search :size="15" />
            </template>
          </a-input>
        </template>
        <template #filters>
          <a-select
            v-model:value="query.kind"
            allow-clear
            :placeholder="t('dictionary.kindAll')"
          >
            <a-select-option
              v-for="option in kindOptions"
              :key="option.value"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
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
        row-key="id"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="{ current: query.page, pageSize: query.size, total, showSizeChanger: true, showTotal: (value: number) => t('common.total', { total: value }) }"
        :scroll="{ x: 1100 }"
        @change="(pagination: TablePaginationConfig) => changePage(pagination.current ?? query.page, pagination.pageSize ?? query.size)"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'dictionary'">
            <button
              type="button"
              class="resource-title"
              @click="openDetail(record)"
            >
              <BookOpenText :size="18" />
              <span>
                <strong>{{ record.label }}</strong>
                <small>{{ record.code }} · {{ t(`dictionary.source.${record.source.toLowerCase()}`) }}</small>
              </span>
            </button>
          </template>
          <template v-else-if="column.key === 'kind'">
            {{ kindLabel(record.kind) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="optionColor('system.dictionary-status', record.status)">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space :size="4">
              <RowActionButton
                :label="t('dictionary.items')"
                @click="openDetail(record)"
              >
                <ListTree :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage && record.kind !== 'CODE_ENUM'"
                :label="t('common.edit')"
                @click="openEdit(record)"
              >
                <Pencil :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage && record.source === 'CONFIGURATION'"
                danger
                :label="t('common.delete')"
                @click="confirmDeleteDictionary(record)"
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
            :description="t('dictionary.empty')"
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
                <button
                  type="button"
                  class="resource-title"
                  @click="openDetail(record)"
                >
                  <BookOpenText :size="18" />
                  <span>
                    <strong>{{ record.label }}</strong>
                    <small>{{ record.code }} · {{ t(`dictionary.source.${record.source.toLowerCase()}`) }}</small>
                  </span>
                </button>
                <a-tag :color="optionColor('system.dictionary-status', record.status)">
                  {{ statusLabel(record.status) }}
                </a-tag>
              </div>
              <div class="mobile-record__meta">
                <span>{{ t("dictionary.kind") }}</span><strong>{{ kindLabel(record.kind) }}</strong>
                <span>{{ t("common.updatedAt") }}</span><strong>{{ formatDateTime(record.updatedAt) }}</strong>
              </div>
              <div class="mobile-record__actions">
                <a-space :size="4">
                  <RowActionButton
                    :label="t('dictionary.items')"
                    @click="openDetail(record)"
                  >
                    <ListTree :size="18" />
                  </RowActionButton>
                  <RowActionButton
                    v-if="canManage && record.kind !== 'CODE_ENUM'"
                    :label="t('common.edit')"
                    @click="openEdit(record)"
                  >
                    <Pencil :size="18" />
                  </RowActionButton>
                  <RowActionButton
                    v-if="canManage && record.source === 'CONFIGURATION'"
                    danger
                    :label="t('common.delete')"
                    @click="confirmDeleteDictionary(record)"
                  >
                    <Trash2 :size="18" />
                  </RowActionButton>
                </a-space>
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
      :title="t('common.filter')"
      width="min(360px, 100vw)"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('dictionary.code')">
          <a-input
            v-model:value="query.search"
            allow-clear
          />
        </a-form-item>
        <a-form-item :label="t('dictionary.kind')">
          <a-select
            v-model:value="query.kind"
            allow-clear
          >
            <a-select-option
              v-for="option in kindOptions"
              :key="option.value"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('dictionary.status')">
          <a-select
            v-model:value="query.status"
            allow-clear
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
          <a-button @click="resetQuery">
            {{ t("common.reset") }}
          </a-button>
          <a-button
            type="primary"
            @click="applyQuery"
          >
            {{ t("common.query") }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-modal
      v-model:open="editorOpen"
      :title="editing ? t('common.edit') : t('dictionary.create')"
      :confirm-loading="submitting"
      :ok-text="t('common.save')"
      :cancel-text="t('common.cancel')"
      @ok="saveDictionary"
    >
      <a-form layout="vertical">
        <a-form-item
          :label="t('dictionary.code')"
          required
        >
          <a-input
            v-model:value="form.code"
            :disabled="Boolean(editing)"
            :placeholder="t('dictionary.codePlaceholder')"
          />
        </a-form-item>
        <a-form-item
          :label="t('dictionary.kind')"
          required
        >
          <a-select
            v-model:value="form.kind"
            :disabled="Boolean(editing)"
          >
            <a-select-option
              v-for="option in configurableKindOptions"
              :key="option.value"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
          :label="t('dictionary.localizedLabels')"
          required
        >
          <LocalizedTextEditor v-model="form.labels" />
        </a-form-item>
        <a-form-item
          v-if="editing"
          :label="t('dictionary.status')"
        >
          <a-select v-model:value="form.status">
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
    </a-modal>

    <a-drawer
      :open="detailOpen"
      :title="detail?.label ?? t('dictionary.items')"
      width="min(960px, 100vw)"
      @close="closeDetail"
    >
      <a-alert
        v-if="detail?.kind === 'CODE_ENUM'"
        type="info"
        show-icon
        :message="t('dictionary.immutableHint')"
      />
      <a-alert
        v-else-if="detail?.source === 'CODE'"
        type="info"
        show-icon
        :message="t('dictionary.extensibleHint')"
      />
      <div class="detail-toolbar">
        <a-input
          v-model:value="itemQuery.search"
          allow-clear
          :placeholder="t('dictionary.itemValue')"
          @press-enter="itemQuery.page = 1; loadItems()"
        >
          <template #prefix>
            <Search :size="15" />
          </template>
        </a-input>
        <div class="toolbar-spacer" />
        <a-button
          type="primary"
          @click="itemQuery.page = 1; loadItems()"
        >
          <Search :size="16" />{{ t("common.query") }}
        </a-button>
        <a-button
          v-if="canManage && itemTotal > 1"
          @click="openItemOrder"
        >
          <ArrowUpDown :size="16" />{{ t("dictionary.sort") }}
        </a-button>
        <a-button
          v-if="canManage && detail?.kind !== 'CODE_ENUM'"
          type="primary"
          @click="openItemCreate"
        >
          <CirclePlus :size="16" />{{ t("dictionary.itemCreate") }}
        </a-button>
      </div>
      <a-table
        class="desktop-only"
        row-key="id"
        :columns="itemColumns"
        :data-source="items"
        :loading="itemsLoading"
        :pagination="{ current: itemQuery.page, pageSize: itemQuery.size, total: itemTotal, showSizeChanger: true }"
        :scroll="{ x: 860 }"
        @change="(pagination: TablePaginationConfig) => { itemQuery.page = pagination.current ?? itemQuery.page; itemQuery.size = pagination.pageSize ?? itemQuery.size; loadItems(); }"
      >
        <template #emptyText>
          {{ t("dictionary.itemEmpty") }}
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'item'">
            <strong>{{ record.label }}</strong><div class="secondary-code">
              {{ record.value }} · {{ t(`dictionary.source.${record.source.toLowerCase()}`) }}
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="optionColor('system.dictionary-status', record.status)">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'tone'">
            {{ toneLabel(record.tone) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space :size="4">
              <RowActionButton
                v-if="canManage && detail?.kind !== 'CODE_ENUM'"
                :label="t('common.edit')"
                @click="openItemEdit(record)"
              >
                <Pencil :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage && detail?.kind !== 'CODE_ENUM' && record.source === 'CONFIGURATION'"
                danger
                :label="t('common.delete')"
                @click="confirmDeleteItem(record)"
              >
                <Trash2 :size="17" />
              </RowActionButton>
            </a-space>
          </template>
        </template>
      </a-table>
      <div class="mobile-only">
        <a-spin :spinning="itemsLoading">
          <a-empty
            v-if="!itemsLoading && items.length === 0"
            :description="t('dictionary.itemEmpty')"
          />
          <div
            v-else
            class="mobile-card-list"
          >
            <article
              v-for="item in items"
              :key="item.id"
              class="mobile-record"
            >
              <div class="mobile-record__header">
                <div>
                  <strong>{{ item.label }}</strong>
                  <div class="secondary-code">
                    {{ item.value }} · {{ t(`dictionary.source.${item.source.toLowerCase()}`) }}
                  </div>
                </div>
                <a-tag :color="optionColor('system.dictionary-status', item.status)">
                  {{ statusLabel(item.status) }}
                </a-tag>
              </div>
              <div class="mobile-record__meta">
                <span>{{ t("dictionary.tone") }}</span><strong>{{ toneLabel(item.tone) }}</strong>
              </div>
              <div class="mobile-record__actions">
                <a-space :size="4">
                  <RowActionButton
                    v-if="canManage && detail?.kind !== 'CODE_ENUM'"
                    :label="t('common.edit')"
                    @click="openItemEdit(item)"
                  >
                    <Pencil :size="18" />
                  </RowActionButton>
                  <RowActionButton
                    v-if="canManage && detail?.kind !== 'CODE_ENUM' && item.source === 'CONFIGURATION'"
                    danger
                    :label="t('common.delete')"
                    @click="confirmDeleteItem(item)"
                  >
                    <Trash2 :size="18" />
                  </RowActionButton>
                </a-space>
              </div>
            </article>
          </div>
        </a-spin>
        <a-pagination
          v-if="itemTotal > itemQuery.size"
          simple
          :current="itemQuery.page"
          :page-size="itemQuery.size"
          :total="itemTotal"
          class="mobile-pagination"
          @change="(page: number) => { itemQuery.page = page; loadItems(); }"
        />
      </div>
    </a-drawer>

    <a-modal
      v-model:open="itemEditorOpen"
      :title="editingItem ? t('common.edit') : t('dictionary.itemCreate')"
      :confirm-loading="submitting"
      :ok-text="t('common.save')"
      :cancel-text="t('common.cancel')"
      @ok="saveItem"
    >
      <a-form layout="vertical">
        <a-form-item
          :label="t('dictionary.itemValue')"
          required
        >
          <a-input
            v-model:value="itemForm.value"
            :disabled="Boolean(editingItem && (detail?.kind === 'BUSINESS_CODE' || editingItem.source === 'CODE'))"
          />
        </a-form-item>
        <a-form-item
          :label="t('dictionary.localizedLabels')"
          required
        >
          <LocalizedTextEditor v-model="itemForm.labels" />
        </a-form-item>
        <a-form-item :label="t('dictionary.tone')">
          <a-select v-model:value="itemForm.tone">
            <a-select-option
              v-for="option in toneOptions"
              :key="option.value"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('dictionary.selectable')">
          <a-switch v-model:checked="itemForm.selectable" />
        </a-form-item>
        <a-form-item
          v-if="editingItem"
          :label="t('dictionary.status')"
        >
          <a-select v-model:value="itemForm.status">
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
    </a-modal>

    <a-modal
      v-model:open="orderOpen"
      :title="t(orderKind === 'types' ? 'dictionary.typeOrderTitle' : 'dictionary.itemOrderTitle')"
      :confirm-loading="submitting"
      :ok-button-props="{ disabled: orderLoading || orderItems.length < 2 }"
      :ok-text="t('common.save')"
      :cancel-text="t('common.cancel')"
      @ok="saveOrder"
    >
      <a-spin :spinning="orderLoading">
        <SortableOrderList
          v-model="orderItems"
          :drag-label="t('dictionary.dragLabel')"
          :disabled="orderLoading || submitting"
        />
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.detail-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-spacer {
  flex: 1;
}

.resource-title {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  gap: 10px;
  padding: 4px;
  color: var(--color-primary, #165dff);
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
}

.resource-title:focus-visible {
  outline: 2px solid var(--color-primary, #165dff);
  outline-offset: 2px;
}

.resource-title span {
  display: grid;
  gap: 2px;
}

.resource-title small,
.secondary-code {
  color: var(--text-secondary, #4e5969);
  font-size: 12px;
}

.detail-toolbar {
  margin: 16px 0;
}

.detail-toolbar :deep(.ant-input-affix-wrapper) {
  width: min(320px, 100%);
}

.drawer-footer-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.drawer-footer-actions :deep(.ant-btn),
.dictionary-mobile-actions :deep(.ant-btn),
.detail-toolbar :deep(.ant-btn) {
  min-height: 44px;
}

.dictionary-mobile-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

@media (max-width: 767px) {
  .detail-toolbar {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .detail-toolbar :deep(.ant-input-affix-wrapper) {
    grid-column: 1 / -1;
    width: 100%;
  }

  .detail-toolbar .toolbar-spacer {
    display: none;
  }
}

</style>
