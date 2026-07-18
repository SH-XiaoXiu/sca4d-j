import type { RouteRecordRaw } from "vue-router";

export const personalRoutes: RouteRecordRaw[] = [
  {
    path: "dashboard",
    name: "dashboard",
    component: () => import("../../features/dashboard/DashboardPage.vue"),
    meta: {
      title: "工作台",
      titleKey: "route.dashboard",
      icon: "LayoutDashboard",
      routeCode: "workspace.dashboard",
      order: 10,
      workspace: true,
      cacheName: "DashboardPage",
      pinned: true
    }
  },
  {
    path: "profile",
    name: "profile",
    component: () => import("../../features/account/ProfilePage.vue"),
    meta: {
      title: "账号信息",
      titleKey: "route.profile",
      hidden: true,
      workspace: true,
      cacheName: "ProfilePage"
    }
  },
  {
    path: "account-security",
    name: "account-security",
    component: () => import("../../features/account/AccountSecurityPage.vue"),
    meta: {
      title: "账号与安全",
      titleKey: "route.accountSecurity",
      hidden: true,
      workspace: true,
      cacheName: "AccountSecurityPage"
    }
  }
];
