import type { RouteRecordRaw } from "vue-router";

export const systemRoutes: RouteRecordRaw[] = [
  {
    path: "system/dictionaries",
    name: "system-dictionaries",
    component: () => import("../../features/dictionaries/DictionaryManagementPage.vue"),
    meta: {
      title: "字典管理",
      titleKey: "route.dictionaries",
      icon: "BookOpenText",
      routeCode: "system.dictionaries",
      order: 80,
      permission: "system:dictionaries:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "DictionaryManagementPage"
    }
  },
  {
    path: "system/identity-providers",
    name: "system-identity-providers",
    component: () => import("../../features/identity-providers/IdentityProvidersPage.vue"),
    meta: {
      title: "身份源",
      titleKey: "route.systemIdentityProviders",
      icon: "Network",
      routeCode: "system.identity-providers",
      order: 79,
      permission: "system:identity-providers:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "IdentityProvidersPage"
    }
  },
  {
    path: "system/dictionaries/:dictionaryId",
    name: "system-dictionary-detail",
    component: () => import("../../features/dictionaries/DictionaryManagementPage.vue"),
    meta: {
      title: "字典详情",
      titleKey: "route.dictionaryDetail",
      hidden: true,
      permission: "system:dictionaries:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "DictionaryManagementPage"
    }
  },
  {
    path: "system/menus",
    name: "system-menus",
    component: () => import("../../features/menus/SystemMenuPage.vue"),
    meta: {
      title: "菜单管理",
      titleKey: "route.menus",
      icon: "PanelLeft",
      routeCode: "system.menus",
      order: 85,
      permission: "system:menus:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "SystemMenuPage"
    }
  },
  {
    path: "system/menus/:menuId",
    name: "system-menu-detail",
    component: () => import("../../features/menus/SystemMenuPage.vue"),
    meta: {
      title: "菜单详情",
      titleKey: "route.menuDetail",
      hidden: true,
      permission: "system:menus:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "SystemMenuPage"
    }
  },
  {
    path: "system/mail-senders",
    name: "mail-senders",
    component: () => import("../../features/mail/MailSendersPage.vue"),
    meta: {
      title: "邮件发送配置",
      titleKey: "route.mailSenders",
      icon: "MailCheck",
      routeCode: "system.mail-senders",
      order: 87,
      permission: "system:mail-senders:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "MailSendersPage"
    }
  },
  {
    path: "system/mail-senders/:senderId",
    name: "mail-sender-detail",
    component: () => import("../../features/mail/MailSendersPage.vue"),
    meta: {
      title: "邮件发送配置详情",
      titleKey: "route.mailSenderDetail",
      hidden: true,
      permission: "system:mail-senders:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "MailSendersPage"
    }
  },
  {
    path: "system/mail-templates",
    name: "mail-templates",
    component: () => import("../../features/mail/MailTemplatesPage.vue"),
    meta: {
      title: "邮件模板",
      titleKey: "route.mailTemplates",
      icon: "FileText",
      routeCode: "system.mail-templates",
      order: 88,
      permission: "system:mail-templates:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "MailTemplatesPage"
    }
  },
  {
    path: "system/mail-templates/:templateId",
    name: "mail-template-detail",
    component: () => import("../../features/mail/MailTemplatesPage.vue"),
    meta: {
      title: "邮件模板详情",
      titleKey: "route.mailTemplateDetail",
      hidden: true,
      permission: "system:mail-templates:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "MailTemplatesPage"
    }
  },
  {
    path: "system/operation-logs",
    name: "operation-logs",
    component: () => import("../../features/audit/OperationLogPage.vue"),
    meta: {
      title: "操作日志",
      titleKey: "route.operationLogs",
      icon: "ScrollText",
      routeCode: "system.operation-logs",
      order: 90,
      permission: "system:operation-logs:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "OperationLogPage"
    }
  },
  {
    path: "system/scheduled-jobs",
    name: "system-scheduled-jobs",
    component: () => import("../../features/schedules/SystemScheduledJobsPage.vue"),
    meta: {
      title: "系统任务",
      titleKey: "route.systemScheduledJobs",
      icon: "ServerCog",
      routeCode: "system.scheduled-jobs",
      order: 88,
      permission: "system:scheduled-jobs:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "SystemScheduledJobsPage"
    }
  }
];
