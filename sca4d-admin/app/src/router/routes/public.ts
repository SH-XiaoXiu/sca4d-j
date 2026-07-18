import type { RouteRecordRaw } from "vue-router";

export const publicRoutes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "login",
    component: () => import("../../features/authentication/LoginPage.vue"),
    meta: { title: "登录", titleKey: "route.login", public: true, workspace: false }
  },
  {
    path: "/403",
    name: "forbidden",
    component: () => import("../../features/errors/ForbiddenPage.vue"),
    meta: { title: "无权访问", titleKey: "route.forbidden", public: true, workspace: false }
  },
  {
    path: "/:pathMatch(.*)*",
    name: "not-found",
    component: () => import("../../features/errors/NotFoundPage.vue"),
    meta: { title: "页面不存在", titleKey: "route.notFound", public: true, workspace: false }
  }
];
