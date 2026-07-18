import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { RouteMeta, RouteRecordName } from "vue-router";

const STORAGE_PREFIX = "sca4d-admin.workspace.v1";
const DASHBOARD_KEY = "route:dashboard";

/** 工作区中一个可切换页面标签的标题、路由和固定标签属性。 */
export interface WorkspaceTab {
  key: string;
  path: string;
  title: string;
  titleKey?: string | undefined;
  cacheName: string | null;
  pinned: boolean;
  lastVisitedAt: number;
  dirty: boolean;
}

/** 注册到工作区标签系统的页面路由信息。 */
export interface WorkspaceRoute {
  fullPath: string;
  path: string;
  name: RouteRecordName | null | undefined;
  meta: RouteMeta;
}

/** 拖动标签时插入目标标签的前方或后方。 */
export type WorkspaceDropPosition = "before" | "after";

interface PersistedWorkspace {
  activeKey: string;
  tabs: Array<Omit<WorkspaceTab, "dirty">>;
}

function dashboardTab(lastVisitedAt = 0): WorkspaceTab {
  return {
    key: DASHBOARD_KEY,
    path: "/dashboard",
    title: "工作台",
    titleKey: "route.dashboard",
    cacheName: "DashboardPage",
    pinned: true,
    lastVisitedAt,
    dirty: false
  };
}

function storageKey(subjectId: string): string {
  return `${STORAGE_PREFIX}.${encodeURIComponent(subjectId)}`;
}

function isPersistedTab(value: unknown): value is Omit<WorkspaceTab, "dirty"> {
  if (!value || typeof value !== "object") {
    return false;
  }
  const tab = value as Record<string, unknown>;
  return (
    typeof tab.key === "string" &&
    typeof tab.path === "string" &&
    tab.path.startsWith("/") &&
    typeof tab.title === "string" &&
    (typeof tab.titleKey === "string" || tab.titleKey === undefined) &&
    (typeof tab.cacheName === "string" || tab.cacheName === null) &&
    typeof tab.pinned === "boolean" &&
    typeof tab.lastVisitedAt === "number" &&
    Number.isFinite(tab.lastVisitedAt)
  );
}

/** 打开、关闭、激活和拖动管理端工作区标签的 Pinia Store。 */
export const useWorkspaceStore = defineStore("workspace", () => {
  const tabs = ref<WorkspaceTab[]>([dashboardTab()]);
  const activeKey = ref(DASHBOARD_KEY);
  const subjectId = ref<string | null>(null);
  const scrollPositions = new Map<string, number>();

  const activeTab = computed(
    () => tabs.value.find((tab) => tab.key === activeKey.value) ?? tabs.value[0] ?? null
  );
  const activePath = computed(() => activeTab.value?.path ?? "/dashboard");
  const cacheNames = computed(() => [
    ...new Set(
      tabs.value
        .map((tab) => tab.cacheName)
        .filter((cacheName): cacheName is string => Boolean(cacheName))
    )
  ]);
  const hasDirtyTabs = computed(() => tabs.value.some((tab) => tab.dirty));

  function nextVisitedAt(): number {
    const latest = tabs.value.reduce((value, tab) => Math.max(value, tab.lastVisitedAt), 0);
    return Math.max(Date.now(), latest + 1);
  }

  function persist(): void {
    if (!subjectId.value) {
      return;
    }
    const workspace: PersistedWorkspace = {
      activeKey: activeKey.value,
      tabs: tabs.value.map((tab) => ({
        key: tab.key,
        path: tab.path,
        title: tab.title,
        titleKey: tab.titleKey,
        cacheName: tab.cacheName,
        pinned: tab.pinned,
        lastVisitedAt: tab.lastVisitedAt
      }))
    };
    localStorage.setItem(storageKey(subjectId.value), JSON.stringify(workspace));
  }

  function restore(nextSubjectId: string | null | undefined): void {
    if (!nextSubjectId) {
      detach();
      return;
    }
    if (subjectId.value === nextSubjectId) {
      return;
    }

    subjectId.value = nextSubjectId;
    scrollPositions.clear();
    const raw = localStorage.getItem(storageKey(nextSubjectId));
    if (!raw) {
      tabs.value = [dashboardTab()];
      activeKey.value = DASHBOARD_KEY;
      persist();
      return;
    }

    try {
      const stored = JSON.parse(raw) as Partial<PersistedWorkspace>;
      const restored: WorkspaceTab[] = [];
      const seen = new Set<string>();
      for (const value of Array.isArray(stored.tabs) ? stored.tabs : []) {
        if (!isPersistedTab(value) || seen.has(value.key)) {
          continue;
        }
        seen.add(value.key);
        restored.push({ ...value, pinned: value.key === DASHBOARD_KEY, dirty: false });
      }

      const existingDashboard = restored.find((tab) => tab.key === DASHBOARD_KEY);
      tabs.value = [
        dashboardTab(existingDashboard?.lastVisitedAt ?? 0),
        ...restored.filter((tab) => tab.key !== DASHBOARD_KEY)
      ];
      activeKey.value =
        typeof stored.activeKey === "string" && tabs.value.some((tab) => tab.key === stored.activeKey)
          ? stored.activeKey
          : DASHBOARD_KEY;
      persist();
    } catch {
      localStorage.removeItem(storageKey(nextSubjectId));
      tabs.value = [dashboardTab()];
      activeKey.value = DASHBOARD_KEY;
      persist();
    }
  }

  function detach(): void {
    subjectId.value = null;
    tabs.value = [dashboardTab()];
    activeKey.value = DASHBOARD_KEY;
    scrollPositions.clear();
  }

  function routeKey(route: WorkspaceRoute): string {
    if (route.meta.tabMode === "full-path") {
      return `full:${route.fullPath}`;
    }
    return `route:${String(route.name ?? route.path)}`;
  }

  function visitRoute(route: WorkspaceRoute): void {
    if (route.meta.workspace !== true) {
      return;
    }
    const key = routeKey(route);
    const visitedAt = nextVisitedAt();
    const existing = tabs.value.find((tab) => tab.key === key);
    const title = typeof route.meta.title === "string" ? route.meta.title : "未命名页面";
    const titleKey = typeof route.meta.titleKey === "string" ? route.meta.titleKey : undefined;
    const cacheName = typeof route.meta.cacheName === "string" ? route.meta.cacheName : null;
    const pinned = key === DASHBOARD_KEY || route.meta.pinned === true;

    if (existing) {
      existing.path = route.fullPath;
      existing.title = title;
      existing.titleKey = titleKey;
      existing.cacheName = cacheName;
      existing.pinned = pinned;
      existing.lastVisitedAt = visitedAt;
    } else {
      tabs.value.push({
        key,
        path: route.fullPath,
        title,
        titleKey,
        cacheName,
        pinned,
        lastVisitedAt: visitedAt,
        dirty: false
      });
    }
    activeKey.value = key;
    persist();
  }

  function activate(key: string): WorkspaceTab | null {
    const tab = tabs.value.find((item) => item.key === key);
    if (!tab) {
      return activeTab.value;
    }
    tab.lastVisitedAt = nextVisitedAt();
    activeKey.value = tab.key;
    persist();
    return tab;
  }

  function close(key: string): WorkspaceTab | null {
    const closing = tabs.value.find((tab) => tab.key === key);
    if (!closing || closing.pinned) {
      return activeTab.value;
    }
    const wasActive = activeKey.value === key;
    tabs.value = tabs.value.filter((tab) => tab.key !== key);
    scrollPositions.delete(key);
    if (wasActive) {
      const next = [...tabs.value].sort((left, right) => right.lastVisitedAt - left.lastVisitedAt)[0];
      activeKey.value = next?.key ?? DASHBOARD_KEY;
    }
    persist();
    return activeTab.value;
  }

  function closeOthers(key: string): WorkspaceTab | null {
    const target = tabs.value.find((tab) => tab.key === key) ?? tabs.value.find((tab) => tab.pinned);
    if (!target) {
      return null;
    }
    for (const tab of tabs.value) {
      if (!tab.pinned && tab.key !== target.key) {
        scrollPositions.delete(tab.key);
      }
    }
    tabs.value = tabs.value.filter((tab) => tab.pinned || tab.key === target.key);
    activeKey.value = target.key;
    target.lastVisitedAt = nextVisitedAt();
    persist();
    return target;
  }

  function closeAll(): WorkspaceTab | null {
    const dashboard = tabs.value.find((tab) => tab.key === DASHBOARD_KEY) ?? dashboardTab();
    for (const tab of tabs.value) {
      if (!tab.pinned) {
        scrollPositions.delete(tab.key);
      }
    }
    tabs.value = [dashboard];
    activeKey.value = dashboard.key;
    dashboard.lastVisitedAt = nextVisitedAt();
    persist();
    return dashboard;
  }

  function move(key: string, targetKey: string, position: WorkspaceDropPosition): void {
    const source = tabs.value.find((tab) => tab.key === key);
    const target = tabs.value.find((tab) => tab.key === targetKey);
    if (!source || !target || source.pinned || source.key === target.key) {
      return;
    }

    const reordered = tabs.value.filter((tab) => tab.key !== source.key);
    const targetIndex = reordered.findIndex((tab) => tab.key === target.key);
    const firstMovableIndex = reordered.findIndex((tab) => !tab.pinned);
    const minimumIndex = firstMovableIndex === -1 ? reordered.length : firstMovableIndex;
    const requestedIndex = targetIndex + (position === "after" ? 1 : 0);
    reordered.splice(Math.max(minimumIndex, requestedIndex), 0, source);
    tabs.value = reordered;
    persist();
  }

  function moveBy(key: string, direction: -1 | 1): void {
    const sourceIndex = tabs.value.findIndex((tab) => tab.key === key);
    const targetIndex = sourceIndex + direction;
    const source = tabs.value[sourceIndex];
    const target = tabs.value[targetIndex];
    if (!source || !target || source.pinned || target.pinned) {
      return;
    }
    const reordered = [...tabs.value];
    reordered[sourceIndex] = target;
    reordered[targetIndex] = source;
    tabs.value = reordered;
    persist();
  }

  function renameCurrent(title: string): void {
    const value = title.trim();
    if (!activeTab.value || !value) {
      return;
    }
    activeTab.value.title = value;
    persist();
  }

  function markCurrentDirty(dirty: boolean): void {
    if (activeTab.value) {
      activeTab.value.dirty = dirty;
    }
  }

  function retainTabs(predicate: (tab: WorkspaceTab) => boolean): void {
    const retained = tabs.value.filter((tab) => tab.key === DASHBOARD_KEY || predicate(tab));
    if (retained.length === tabs.value.length) {
      return;
    }
    const retainedKeys = new Set(retained.map((tab) => tab.key));
    for (const key of scrollPositions.keys()) {
      if (!retainedKeys.has(key)) {
        scrollPositions.delete(key);
      }
    }
    tabs.value = retained;
    if (!tabs.value.some((tab) => tab.key === activeKey.value)) {
      const next = [...tabs.value].sort((left, right) => right.lastVisitedAt - left.lastVisitedAt)[0];
      activeKey.value = next?.key ?? DASHBOARD_KEY;
    }
    persist();
  }

  function rememberScroll(route: WorkspaceRoute, top: number): void {
    if (route.meta.workspace === true) {
      const key = routeKey(route);
      if (tabs.value.some((tab) => tab.key === key)) {
        scrollPositions.set(key, top);
      }
    }
  }

  function scrollTop(route: WorkspaceRoute): number {
    return route.meta.workspace === true ? (scrollPositions.get(routeKey(route)) ?? 0) : 0;
  }

  return {
    tabs,
    activeKey,
    subjectId,
    activeTab,
    activePath,
    cacheNames,
    hasDirtyTabs,
    restore,
    detach,
    routeKey,
    visitRoute,
    activate,
    close,
    closeOthers,
    closeAll,
    move,
    moveBy,
    renameCurrent,
    markCurrentDirty,
    retainTabs,
    rememberScroll,
    scrollTop
  };
});
