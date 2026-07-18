import type { PrincipalType } from "@sca4d/admin-common";
import { watch } from "vue";
import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import AdminLayout from "../layouts/AdminLayout.vue";
import { i18n } from "../i18n";
import { useSessionStore } from "../stores/session";
import { useWorkspaceStore } from "../stores/workspace";
import { personalRoutes } from "./routes/personal";
import { platformRoutes } from "./routes/platform";
import { publicRoutes } from "./routes/public";
import { systemRoutes } from "./routes/system";
import { tenantRoutes } from "./routes/tenant";

declare module "vue-router" {
  interface RouteMeta {
    title?: string;
    titleKey?: string;
    icon?: string;
    routeCode?: string;
    public?: boolean;
    permission?: string;
    order?: number;
    hidden?: boolean;
    workspace?: boolean;
    tabMode?: "route" | "full-path";
    cacheName?: string;
    pinned?: boolean;
    principalType?: Exclude<PrincipalType, "SYSTEM">;
    tenantParam?: string;
  }
}

/** 管理端所有可访问页面的路由定义。 */
export const applicationRoutes: RouteRecordRaw[] = [
  {
    path: "/",
    component: AdminLayout,
    redirect: "/dashboard",
    children: [
      ...personalRoutes,
      ...platformRoutes,
      ...tenantRoutes,
      ...systemRoutes
    ]
  },
  ...publicRoutes
];

/** 管理端共享 Vue Router 实例。 */
export const router = createRouter({
  history: createWebHistory(),
  routes: applicationRoutes,
  scrollBehavior: (to, from, savedPosition) => {
    const workspace = useWorkspaceStore();
    workspace.rememberScroll(from, window.scrollY);
    return savedPosition ?? { left: 0, top: workspace.scrollTop(to) };
  }
});

function updateDocumentTitle(route: { meta: RouteRecordRaw["meta"] }): void {
  const routeTitle = route.meta?.titleKey
    ? i18n.global.t(route.meta.titleKey)
    : typeof route.meta?.title === "string"
      ? route.meta.title
      : null;
  document.title = routeTitle ? `${routeTitle} - ${i18n.global.t("app.name")}` : i18n.global.t("app.name");
}

watch(i18n.global.locale, () => updateDocumentTitle(router.currentRoute.value));

function prepareWorkspace(): ReturnType<typeof useWorkspaceStore> {
  const auth = useSessionStore();
  const workspace = useWorkspaceStore();
  workspace.restore(auth.subjectId);
  workspace.retainTabs((tab) => {
    const resolved = router.resolve(tab.path);
    if (resolved.name === "not-found" || resolved.meta.workspace !== true) return false;
    if (!resolved.meta.principalType) return auth.hasPermission(resolved.meta.permission);
    const tenantId = resolved.meta.tenantParam
      ? String(resolved.params[resolved.meta.tenantParam] ?? "")
      : undefined;
    return (
      auth.hasPrincipal(resolved.meta.principalType, tenantId) && auth.hasPermission(resolved.meta.permission)
    );
  });
  return workspace;
}

/** 按最新 Principal 和权限快照清理工作区，并离开当前已经不可访问的页面。 */
export async function reconcileWorkspaceAccess(): Promise<void> {
  const auth = useSessionStore();
  const workspace = prepareWorkspace();
  const current = router.currentRoute.value;
  const tenantId = current.meta.tenantParam
    ? String(current.params[current.meta.tenantParam] ?? "")
    : undefined;
  const principalAvailable =
    !current.meta.principalType || auth.hasPrincipal(current.meta.principalType, tenantId);
  if (
    !current.meta.public &&
    (!principalAvailable || !auth.hasPermission(current.meta.permission)) &&
    current.fullPath !== workspace.activePath
  ) {
    await router.replace(workspace.activePath);
  }
}

router.beforeEach(async (to) => {
  updateDocumentTitle(to);
  if (to.meta.public) {
    const auth = useSessionStore();
    if (to.name === "login" && auth.authenticated && !auth.leavingSession) {
      const externalIdentityCallback = [
        "oidcFailed",
        "oauth2Failed",
        "protocol",
        "authenticationId",
        "activityId"
      ].some((name) => name in to.query);
      if (externalIdentityCallback) return true;
      try {
        await auth.bootstrap();
        return prepareWorkspace().activePath;
      } catch {
        return true;
      }
    }
    return true;
  }

  const auth = useSessionStore();
  if (!auth.authenticated) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  try {
    await auth.bootstrap();
    if (to.meta.principalType) {
      const tenantId = to.meta.tenantParam ? String(to.params[to.meta.tenantParam] ?? "") : undefined;
      if (!auth.hasPrincipal(to.meta.principalType, tenantId)) {
        return { name: "forbidden", query: { from: to.fullPath } };
      }
      await auth.selectPrincipal(to.meta.principalType, tenantId);
    }
  } catch {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  const workspace = prepareWorkspace();
  if (!auth.hasPermission(to.meta.permission)) {
    return { name: "forbidden", query: { from: to.fullPath } };
  }
  if (to.path === "/dashboard" && to.redirectedFrom?.path === "/" && workspace.activePath !== to.fullPath) {
    return workspace.activePath;
  }
  return true;
});

router.afterEach((to, _from, failure) => {
  if (!failure && to.meta.workspace === true) {
    useWorkspaceStore().visitRoute(to);
  }
});
