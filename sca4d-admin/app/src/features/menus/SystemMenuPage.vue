<script setup lang="ts">
defineOptions({ name: "SystemMenuPage" });

import {
  createIdempotencyKey,
  isApiError,
  type Id,
  type ManagedMenuView,
  type MenuIcon,
  type NavigationRouteView
} from "@sca4d/admin-common";
import {
  ArrowUpDown,
  Pencil,
  Plus,
  RotateCcw,
  Trash2
} from "lucide-vue-next";
import { message, Modal, type FormInstance } from "ant-design-vue";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import { systemMenuApi } from "@/api/client";
import DynamicMenuIcon from "@/components/navigation/DynamicMenuIcon.vue";
import IconPicker from "@/components/data-entry/IconPicker.vue";
import LocalizedTextEditor from "@/components/data-entry/LocalizedTextEditor.vue";
import PageHeader from "@/components/page/PageHeader.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import SortableOrderList, { type SortableOrderItem } from "@/components/data-entry/SortableOrderList.vue";
import { useSessionStore } from "@/stores/session";
import { useLanguageStore } from "@/stores/languages";

interface FlatMenu extends ManagedMenuView {
  depth: number;
}

const route = useRoute();
const router = useRouter();
const auth = useSessionStore();
const languageStore = useLanguageStore();
const { t, locale } = useI18n();
const loading = ref(false);
const submitting = ref(false);
const editorOpen = ref(false);
const formRef = ref<FormInstance>();
const editing = ref<ManagedMenuView | null>(null);
const menus = ref<ManagedMenuView[]>([]);
const expandedMenuIds = ref<Id[]>([]);
const expansionInitialized = ref(false);
const routeOptions = ref<NavigationRouteView[]>([]);
const createKey = ref<string | null>(null);
const orderOpen = ref(false);
const orderParentId = ref<Id | null>(null);
const orderItems = ref<Array<SortableOrderItem & { version: Id }>>([]);
const form = reactive({
  parentId: undefined as Id | undefined,
  routeCode: undefined as string | undefined,
  titles: {} as Record<string, string>,
  icon: "FolderTree" as MenuIcon,
  visible: true
});

const canManage = computed(() => auth.hasPermission("system:menus:manage"));
const flatMenus = computed<FlatMenu[]>(() => {
  const result: FlatMenu[] = [];
  const append = (items: ManagedMenuView[], depth: number): void => {
    for (const item of items) {
      result.push({ ...item, depth });
      append(item.children, depth + 1);
    }
  };
  append(menus.value, 0);
  return result;
});
const excludedParentIds = computed(() => {
  const excluded = new Set<Id>();
  if (!editing.value) return excluded;
  const collect = (item: ManagedMenuView): void => {
    excluded.add(item.menuId);
    item.children.forEach(collect);
  };
  collect(editing.value);
  return excluded;
});
const parentOptions = computed(() =>
  flatMenus.value.filter((item) => !item.routeCode && !excludedParentIds.value.has(item.menuId))
);
const selectedRoute = computed(() =>
  routeOptions.value.find((item) => item.code === form.routeCode)
);
const editorTitle = computed(() =>
  t(editing.value ? (canManage.value ? "menu.edit" : "menu.details") : "menu.create")
);
const columns = computed(() => [
  { title: t("menu.title"), key: "title", width: 260 },
  { title: t("menu.route"), key: "route", width: 260 },
  { title: t("menu.permission"), key: "permission", width: 240 },
  { title: t("menu.visibility"), key: "visible", width: 100 },
  { title: t("common.actions"), key: "actions", width: 148, fixed: "right" as const }
]);

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function validateTitles(): Promise<void> {
  return languageStore.loaded
    && languageStore.requiredLanguages.every((language) => form.titles[language.code]?.trim())
    ? Promise.resolve()
    : Promise.reject(new Error(t("menu.titlesRequired")));
}

function currentTitle(menu: ManagedMenuView): string {
  return menu.titles[locale.value]
    || menu.titles[languageStore.defaultLanguage?.code ?? ""]
    || t("common.unavailable");
}

function routeTitle(option: NavigationRouteView): string {
  return option.defaultTitles[locale.value]
    || option.defaultTitles[languageStore.defaultLanguage?.code ?? ""]
    || option.code;
}

function routeOccupied(routeCode: string): boolean {
  return flatMenus.value.some(
    (item) => item.routeCode === routeCode && item.menuId !== editing.value?.menuId
  );
}

function findMenu(menuId: Id): ManagedMenuView | null {
  return flatMenus.value.find((item) => item.menuId === menuId) ?? null;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [menuTree, routes] = await Promise.all([
      systemMenuApi.menus(),
      systemMenuApi.routeOptions()
    ]);
    menus.value = menuTree;
    routeOptions.value = routes;
    const currentIds = new Set<Id>();
    const collectIds = (items: ManagedMenuView[]): void => {
      for (const item of items) {
        currentIds.add(item.menuId);
        collectIds(item.children);
      }
    };
    collectIds(menuTree);
    expandedMenuIds.value = expansionInitialized.value
      ? expandedMenuIds.value.filter((menuId) => currentIds.has(menuId))
      : [...currentIds];
    expansionInitialized.value = true;
  } catch (error) {
    message.error(errorMessage(error, "menu.loadFailed"));
  } finally {
    loading.value = false;
  }
}

function siblings(parentId: Id | null): ManagedMenuView[] {
  return parentId === null ? menus.value : findMenu(parentId)?.children ?? [];
}

function openOrder(parentId: Id | null): void {
  orderParentId.value = parentId;
  orderItems.value = siblings(parentId).map((menu) => ({
    id: menu.menuId,
    label: currentTitle(menu),
    description: menu.routeCode ?? t("menu.group"),
    version: menu.version
  }));
  orderOpen.value = true;
}

async function saveOrder(): Promise<void> {
  submitting.value = true;
  const draft = [...orderItems.value];
  try {
    await systemMenuApi.reorder(
      orderParentId.value,
      draft.map((item) => ({ menuId: item.id, version: item.version }))
    );
    orderOpen.value = false;
    message.success(t("menu.reorderSuccess"));
    await load();
  } catch (error) {
    if (isApiError(error) && error.httpStatus === 409) {
      await load();
      const latest = siblings(orderParentId.value);
      const draftIds = new Set(draft.map((item) => item.id));
      const sameMembers = latest.length === draft.length
        && latest.every((item) => draftIds.has(item.menuId));
      if (!sameMembers) {
        orderItems.value = latest.map((menu) => ({
          id: menu.menuId,
          label: currentTitle(menu),
          description: menu.routeCode ?? t("menu.group"),
          version: menu.version
        }));
        message.warning(t("menu.reorderMembersChanged"));
        return;
      }
      const latestById = new Map(latest.map((menu) => [menu.menuId, menu]));
      orderItems.value = draft.map((item) => {
        const menu = latestById.get(item.id)!;
        return {
          id: menu.menuId,
          label: currentTitle(menu),
          description: menu.routeCode ?? t("menu.group"),
          version: menu.version
        };
      });
      message.warning(t("menu.reorderVersionsChanged"));
      return;
    }
    message.error(errorMessage(error, "menu.reorderConflict"));
  } finally {
    submitting.value = false;
  }
}

function openCreate(parentId?: Id): void {
  editing.value = null;
  createKey.value = createIdempotencyKey();
  Object.assign(form, {
    parentId,
    routeCode: undefined,
    titles: Object.fromEntries(languageStore.languages.map((language) => [language.code, ""])),
    icon: "FolderTree" as MenuIcon,
    visible: true
  });
  editorOpen.value = true;
}

function openEdit(menu: ManagedMenuView): void {
  editing.value = menu;
  createKey.value = null;
  Object.assign(form, {
    parentId: menu.parentId ?? undefined,
    routeCode: menu.routeCode ?? undefined,
    titles: { ...menu.titles },
    icon: menu.icon,
    visible: menu.visible
  });
  editorOpen.value = true;
}

function selectRoute(routeCode: string | undefined): void {
  form.routeCode = routeCode;
  if (!routeCode) {
    form.icon = "FolderTree";
    return;
  }
  const option = routeOptions.value.find((item) => item.code === routeCode);
  if (!option || editing.value) return;
  form.titles = { ...option.defaultTitles };
  form.icon = option.defaultIcon;
}

function closeEditor(): void {
  if (route.params.menuId) void router.push("/system/menus");
}

async function save(): Promise<void> {
  if (!canManage.value) return;
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  submitting.value = true;
  const input = {
    ...(form.parentId ? { parentId: form.parentId } : {}),
    ...(form.routeCode ? { routeCode: form.routeCode } : {}),
    titles: Object.fromEntries(
      languageStore.languages
        .map((language) => [language.code, (form.titles[language.code] ?? "").trim()] as const)
        .filter(([, title]) => title)
    ),
    icon: form.icon,
    visible: form.visible
  };
  try {
    if (editing.value) {
      await systemMenuApi.update(editing.value.menuId, input, editing.value.version);
      message.success(t("menu.updateSuccess"));
    } else {
      createKey.value ??= createIdempotencyKey();
      await systemMenuApi.create(input, createKey.value);
      message.success(t("menu.createSuccess"));
      await load();
      editorOpen.value = false;
      createKey.value = null;
      if (route.params.menuId) await router.push("/system/menus");
      return;
    }
    editorOpen.value = false;
    await load();
  } catch (error) {
    if (
      isApiError(error) &&
      error.httpStatus === 409 &&
      error.messageKey === "error.menu.changed"
    ) {
      const draft = { ...form };
      const menuId = editing.value?.menuId;
      await load();
      if (menuId) {
        const latest = findMenu(menuId);
        if (!latest) {
          editing.value = null;
          editorOpen.value = false;
          await router.replace("/system/menus");
          message.error(t("menu.notFound"));
          return;
        }
        editing.value = latest;
      }
      Object.assign(form, draft);
      createKey.value = editing.value ? null : createIdempotencyKey();
      message.warning(t("menu.conflict"));
      return;
    }
    message.error(errorMessage(error, "menu.saveFailed"));
  } finally {
    submitting.value = false;
  }
}

function remove(menu: ManagedMenuView): void {
  Modal.confirm({
    title: t("menu.deleteTitle", { name: currentTitle(menu) }),
    content: menu.children.length ? t("menu.deleteTreeWarning") : t("menu.deleteWarning"),
    okText: t("common.delete"),
    cancelText: t("common.cancel"),
    okType: "danger",
    onOk: async () => {
      try {
        await systemMenuApi.delete(menu.menuId, menu.version, createIdempotencyKey());
        message.success(t("menu.deleteSuccess"));
        if (String(route.params.menuId ?? "") === menu.menuId) await router.push("/system/menus");
        await load();
      } catch (error) {
        if (
          isApiError(error) &&
          error.httpStatus === 409 &&
          error.messageKey === "error.menu.changed"
        ) {
          await load();
          message.warning(t("menu.conflict"));
          return;
        }
        message.error(errorMessage(error, "menu.deleteFailed"));
      }
    }
  });
}

async function openRouteMenu(menuId: string): Promise<void> {
  if (!menuId) return;
  const menu = findMenu(menuId);
  if (!menu) {
    message.error(t("menu.notFound"));
    await router.replace("/system/menus");
    return;
  }
  openEdit(menu);
}

onMounted(async () => {
  await languageStore.load();
  await load();
  await openRouteMenu(String(route.params.menuId ?? ""));
});
watch(
  () => route.params.menuId,
  (menuId) => {
    if (menuId) {
      void openRouteMenu(String(menuId));
      return;
    }
    editing.value = null;
    editorOpen.value = false;
  }
);
watch(
  () => auth.menusChangedSequence,
  () => {
    void load();
  }
);
</script>

<template>
  <div>
    <PageHeader
      :title="t('menu.pageTitle')"
    >
      <template #actions>
        <a-button
          :loading="loading"
          @click="load"
        >
          <RotateCcw :size="16" />{{ t("common.refresh") }}
        </a-button>
        <a-button
          v-if="canManage && menus.length > 1"
          @click="openOrder(null)"
        >
          <ArrowUpDown :size="16" />{{ t("menu.reorder") }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          @click="openCreate()"
          @keydown.enter.prevent="openCreate()"
        >
          <Plus :size="16" />{{ t("menu.create") }}
        </a-button>
      </template>
    </PageHeader>

    <section class="page-panel desktop-only menu-table">
      <a-table
        v-model:expanded-row-keys="expandedMenuIds"
        row-key="menuId"
        :columns="columns"
        :data-source="menus"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1180 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <div class="menu-title-cell">
              <DynamicMenuIcon
                :name="record.icon"
                :size="17"
              />
              <div>
                <strong>{{ currentTitle(record) }}</strong>
                <span>{{ record.titles['en-US'] }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'route'">
            <code v-if="record.routeCode">{{ record.routeCode }}</code>
            <span v-else>{{ t("menu.group") }}</span>
            <small v-if="record.pathTemplate">{{ record.pathTemplate }}</small>
          </template>
          <template v-else-if="column.key === 'permission'">
            <code v-if="record.permissionCode">{{ record.permissionCode }}</code>
            <span v-else>{{ t("menu.noPermission") }}</span>
          </template>
          <template v-else-if="column.key === 'visible'">
            <a-tag :color="record.visible ? 'success' : 'default'">
              {{ t(record.visible ? "menu.visible" : "menu.hidden") }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space
              v-if="canManage"
              :size="4"
            >
              <RowActionButton
                v-if="!record.routeCode"
                :label="t('menu.addChild')"
                @click="openCreate(record.menuId)"
              >
                <Plus :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="!record.routeCode && record.children.length > 1"
                :label="t('menu.reorderChildren')"
                @click="openOrder(record.menuId)"
              >
                <ArrowUpDown :size="17" />
              </RowActionButton>
              <RowActionButton
                :label="t('common.edit')"
                @click="openEdit(record)"
              >
                <Pencil :size="17" />
              </RowActionButton>
              <RowActionButton
                danger
                :label="t('common.delete')"
                @click="remove(record)"
              >
                <Trash2 :size="17" />
              </RowActionButton>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          <a-empty :description="t('menu.empty')">
            <a-button
              v-if="canManage"
              type="primary"
              @click="openCreate()"
            >
              {{ t("menu.create") }}
            </a-button>
          </a-empty>
        </template>
      </a-table>
    </section>

    <div class="mobile-only menu-mobile-list">
      <a-skeleton
        v-if="loading"
        active
      />
      <article
        v-for="menu in flatMenus"
        v-else
        :key="menu.menuId"
        :data-row-key="menu.menuId"
        class="mobile-record"
        :style="{ marginLeft: `${Math.min(menu.depth, 2) * 12}px` }"
      >
        <div class="mobile-record__header">
          <div class="menu-title-cell">
            <DynamicMenuIcon
              :name="menu.icon"
              :size="17"
            />
            <div>
              <strong>{{ currentTitle(menu) }}</strong>
              <span>{{ menu.routeCode ?? t("menu.group") }}</span>
            </div>
          </div>
          <a-tag :color="menu.visible ? 'success' : 'default'">
            {{ t(menu.visible ? "menu.visible" : "menu.hidden") }}
          </a-tag>
        </div>
        <div class="mobile-record__meta">
          <span>{{ t("menu.path") }}</span><strong>{{ menu.pathTemplate ?? t("common.unavailable") }}</strong>
        </div>
        <div
          v-if="canManage"
          class="mobile-record__actions"
        >
          <a-space :size="4">
            <RowActionButton
              v-if="!menu.routeCode"
              :label="t('menu.addChild')"
              @click="openCreate(menu.menuId)"
            >
              <Plus :size="18" />
            </RowActionButton>
            <RowActionButton
              v-if="!menu.routeCode && menu.children.length > 1"
              :label="t('menu.reorderChildren')"
              @click="openOrder(menu.menuId)"
            >
              <ArrowUpDown :size="18" />
            </RowActionButton>
            <RowActionButton
              :label="t('common.edit')"
              @click="openEdit(menu)"
            >
              <Pencil :size="18" />
            </RowActionButton>
            <RowActionButton
              danger
              :label="t('common.delete')"
              @click="remove(menu)"
            >
              <Trash2 :size="18" />
            </RowActionButton>
          </a-space>
        </div>
      </article>
      <a-empty
        v-if="!loading && !flatMenus.length"
        :description="t('menu.empty')"
      />
    </div>

    <a-modal
      v-model:open="orderOpen"
      :title="t(orderParentId === null ? 'menu.reorderRootsTitle' : 'menu.reorderChildrenTitle')"
      :ok-text="t('common.save')"
      :cancel-text="t('common.cancel')"
      :confirm-loading="submitting"
      :ok-button-props="{ disabled: orderItems.length < 2 }"
      @ok="saveOrder"
    >
      <SortableOrderList
        v-model="orderItems"
        :drag-label="t('menu.dragToReorder')"
        :disabled="submitting"
      />
    </a-modal>

    <a-modal
      v-model:open="editorOpen"
      :title="editorTitle"
      :ok-text="editing ? t('common.save') : t('common.create')"
      :cancel-text="t('common.cancel')"
      :confirm-loading="submitting"
      :width="680"
      :footer="canManage ? undefined : null"
      wrap-class-name="system-menu-modal"
      @ok="save"
      @cancel="closeEditor"
    >
      <a-form
        ref="formRef"
        :model="form"
        layout="vertical"
        :scroll-to-first-error="true"
      >
        <div class="menu-form-grid">
          <a-form-item :label="t('menu.parent')">
            <a-select
              v-model:value="form.parentId"
              allow-clear
              :placeholder="t('menu.root')"
              :disabled="submitting || !canManage"
            >
              <a-select-option
                v-for="parent in parentOptions"
                :key="parent.menuId"
                :value="parent.menuId"
              >
                {{ `${'—'.repeat(parent.depth)} ${currentTitle(parent)}`.trim() }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="t('menu.route')">
            <a-select
              :value="form.routeCode"
              allow-clear
              show-search
              option-filter-prop="label"
              :popup-match-select-width="false"
              :placeholder="t('menu.groupPlaceholder')"
              :disabled="submitting || !canManage"
              popup-class-name="menu-route-popup"
              @update:value="selectRoute"
            >
              <a-select-option
                v-for="option in routeOptions"
                :key="option.code"
                :value="option.code"
                :label="`${routeTitle(option)} ${option.code}`"
                :disabled="routeOccupied(option.code)"
              >
                <div class="route-option">
                  <div class="route-option__identity">
                    <strong>{{ routeTitle(option) }}</strong>
                    <code>{{ option.code }}</code>
                  </div>
                  <span v-if="routeOccupied(option.code)">{{ t("menu.routeOccupied") }}</span>
                </div>
              </a-select-option>
            </a-select>
          </a-form-item>
        </div>
        <a-alert
          v-if="selectedRoute"
          type="info"
          show-icon
          :message="selectedRoute.pathTemplate"
          :description="selectedRoute.permissionCode ?? t('menu.noPermission')"
        />
        <a-form-item
          :label="t('menu.title')"
          name="titles"
          required
          :rules="[{ validator: validateTitles }]"
        >
          <LocalizedTextEditor
            v-model="form.titles"
            :disabled="submitting || !canManage"
          />
        </a-form-item>
        <div class="menu-form-grid compact-grid">
          <a-form-item :label="t('menu.icon')">
            <IconPicker
              v-model="form.icon"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
        </div>
        <a-form-item :label="t('menu.visibility')">
          <a-switch
            v-model:checked="form.visible"
            :disabled="submitting || !canManage"
          />
          <span class="visibility-label">{{ t(form.visible ? "menu.visible" : "menu.hidden") }}</span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-panel { overflow: hidden; }
.menu-title-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.menu-title-cell > div { display: grid; min-width: 0; }
.menu-title-cell strong, .menu-title-cell span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.menu-title-cell span, small { color: var(--app-text-secondary); font-size: 12px; }
.menu-mobile-list { display: grid; gap: 10px; }
.menu-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.compact-grid { grid-template-columns: minmax(0, 1fr) 160px; }
.route-option {
  display: grid;
  width: min(480px, calc(100vw - 64px));
  min-width: 0;
  align-items: center;
  grid-template-columns: minmax(0, 1fr) 88px;
  gap: 12px;
}
.route-option__identity {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr) minmax(112px, 0.7fr);
  gap: 12px;
}
.route-option__identity strong,
.route-option__identity code,
.route-option span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.route-option span { justify-self: end; color: var(--app-text-secondary); font-size: 12px; }
:global(.menu-route-popup) { max-width: calc(100vw - 24px); }
.visibility-label { margin-left: 10px; }
code, small { display: block; overflow-wrap: anywhere; }
@media (max-width: 640px) {
  .menu-form-grid, .compact-grid { grid-template-columns: 1fr; gap: 0; }
  :global(.system-menu-modal .ant-modal) { max-width: calc(100vw - 16px); margin: 8px auto; top: 0; }
  :global(.system-menu-modal .ant-modal-content) {
    display: flex;
    flex-direction: column;
    height: calc(100dvh - 16px);
    overflow: hidden;
  }
  :global(.system-menu-modal .ant-modal-body) { flex: 1; min-height: 0; overflow-y: auto; }
  :global(.system-menu-modal .ant-modal-footer) { flex: 0 0 auto; }
}
</style>
