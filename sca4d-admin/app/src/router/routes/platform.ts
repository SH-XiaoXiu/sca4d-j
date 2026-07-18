import type { RouteRecordRaw } from "vue-router";

export const platformRoutes: RouteRecordRaw[] = [
  {
    path: "platform/accounts",
    name: "platform-accounts",
    component: () => import("../../features/accounts/PlatformAccountsPage.vue"),
    meta: {
      title: "平台账号",
      titleKey: "route.platformAccounts",
      icon: "KeyRound",
      routeCode: "platform.accounts",
      order: 20,
      permission: "platform:accounts:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "PlatformAccountsPage"
    }
  },
  {
    path: "platform/accounts/:accountId",
    name: "platform-account-detail",
    component: () => import("../../features/accounts/PlatformAccountsPage.vue"),
    meta: {
      title: "账号安全",
      titleKey: "route.platformAccountDetail",
      hidden: true,
      permission: "platform:accounts:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "PlatformAccountsPage"
    }
  },
  {
    path: "platform/operators",
    name: "platform-operators",
    component: () => import("../../features/operators/PlatformOperatorsPage.vue"),
    meta: {
      title: "平台操作员",
      titleKey: "route.platformOperators",
      icon: "UsersRound",
      routeCode: "platform.operators",
      order: 21,
      permission: "platform:operators:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "PlatformOperatorsPage"
    }
  },
  {
    path: "platform/operators/:principalId",
    name: "platform-operator-detail",
    component: () => import("../../features/operators/PlatformOperatorsPage.vue"),
    meta: {
      title: "操作员详情",
      titleKey: "route.platformOperatorDetail",
      hidden: true,
      permission: "platform:operators:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "PlatformOperatorsPage"
    }
  },
  {
    path: "platform/roles",
    name: "platform-roles",
    component: () => import("../../features/roles/RoleManagementPage.vue"),
    meta: {
      title: "平台角色",
      titleKey: "route.platformRoles",
      icon: "ShieldCheck",
      routeCode: "platform.roles",
      order: 25,
      permission: "platform:roles:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "RoleManagementPage"
    }
  },
  {
    path: "platform/roles/:roleId",
    name: "platform-role-detail",
    component: () => import("../../features/roles/RoleManagementPage.vue"),
    meta: {
      title: "角色详情",
      titleKey: "route.roleDetail",
      hidden: true,
      permission: "platform:roles:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "RoleManagementPage"
    }
  },
  {
    path: "platform/tenants",
    name: "platform-tenants",
    component: () => import("../../features/tenants/PlatformTenantsPage.vue"),
    meta: {
      title: "平台租户",
      titleKey: "route.platformTenants",
      icon: "Building2",
      routeCode: "platform.tenants",
      order: 30,
      permission: "platform:tenants:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "PlatformTenantsPage"
    }
  },
  {
    path: "platform/schedules",
    name: "platform-schedules",
    component: () => import("../../features/schedules/PlatformSchedulesPage.vue"),
    meta: {
      title: "业务计划",
      titleKey: "route.platformSchedules",
      icon: "CalendarClock",
      routeCode: "platform.schedules",
      order: 29,
      permission: "platform:schedules:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "PlatformSchedulesPage"
    }
  },
  {
    path: "platform/schedules/:scheduleId",
    name: "platform-schedule-detail",
    component: () => import("../../features/schedules/PlatformSchedulesPage.vue"),
    meta: {
      title: "计划详情",
      titleKey: "route.platformScheduleDetail",
      hidden: true,
      permission: "platform:schedules:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "PlatformSchedulesPage"
    }
  },
  {
    path: "platform/tenant-access",
    name: "platform-tenant-access",
    component: () => import("../../features/tenants/PlatformTenantAccessPage.vue"),
    meta: {
      title: "租户访问控制",
      titleKey: "route.platformTenantAccess",
      icon: "SlidersHorizontal",
      routeCode: "platform.tenant-access",
      order: 28,
      permission: "platform:tenant-access:read",
      principalType: "PLATFORM",
      workspace: true,
      cacheName: "PlatformTenantAccessPage"
    }
  },
  {
    path: "platform/tenants/:tenantId",
    name: "platform-tenant-detail",
    component: () => import("../../features/tenants/PlatformTenantsPage.vue"),
    meta: {
      title: "租户详情",
      titleKey: "route.platformTenantDetail",
      hidden: true,
      permission: "platform:tenants:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "PlatformTenantsPage"
    }
  },
  {
    path: "platform/tenants/:tenantId/members",
    name: "platform-tenant-members",
    component: () => import("../../features/tenants/TenantMembersPage.vue"),
    meta: {
      title: "租户成员",
      titleKey: "route.tenantMembers",
      hidden: true,
      permission: "platform:tenants:members:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "TenantMembersPage"
    }
  },
  {
    path: "platform/tenants/:tenantId/accounts",
    name: "platform-tenant-accounts",
    component: () => import("../../features/accounts/PlatformAccountsPage.vue"),
    meta: {
      title: "租户账号",
      titleKey: "route.tenantAccounts",
      hidden: true,
      permission: "account.security.read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "PlatformAccountsPage"
    }
  },
  {
    path: "platform/tenants/:tenantId/accounts/:accountId",
    name: "platform-tenant-account-detail",
    component: () => import("../../features/accounts/PlatformAccountsPage.vue"),
    meta: {
      title: "账号详情",
      titleKey: "route.platformAccountDetail",
      hidden: true,
      permission: "account.security.read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "PlatformAccountsPage"
    }
  },
  {
    path: "platform/tenants/:tenantId/members/:principalId",
    name: "platform-tenant-member-detail",
    component: () => import("../../features/tenants/TenantMembersPage.vue"),
    meta: {
      title: "成员详情",
      titleKey: "route.tenantMemberDetail",
      hidden: true,
      permission: "platform:tenants:members:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "TenantMembersPage"
    }
  },
  {
    path: "platform/tenants/:tenantId/roles",
    name: "platform-tenant-roles",
    component: () => import("../../features/roles/RoleManagementPage.vue"),
    meta: {
      title: "租户角色",
      titleKey: "route.tenantRoles",
      hidden: true,
      permission: "platform:tenants:roles:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "RoleManagementPage"
    }
  },
  {
    path: "platform/tenants/:tenantId/roles/:roleId",
    name: "platform-tenant-role-detail",
    component: () => import("../../features/roles/RoleManagementPage.vue"),
    meta: {
      title: "角色详情",
      titleKey: "route.roleDetail",
      hidden: true,
      permission: "platform:tenants:roles:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "RoleManagementPage"
    }
  },
  {
    path: "platform/tenants/:tenantId/dictionaries",
    name: "platform-tenant-dictionaries",
    component: () => import("../../features/dictionaries/DictionaryManagementPage.vue"),
    meta: {
      title: "租户字典",
      titleKey: "route.tenantDictionaries",
      hidden: true,
      permission: "platform:tenants:dictionaries:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "DictionaryManagementPage"
    }
  },
  {
    path: "platform/tenants/:tenantId/dictionaries/:dictionaryId",
    name: "platform-tenant-dictionary-detail",
    component: () => import("../../features/dictionaries/DictionaryManagementPage.vue"),
    meta: {
      title: "租户字典详情",
      titleKey: "route.dictionaryDetail",
      hidden: true,
      permission: "platform:tenants:dictionaries:read",
      principalType: "PLATFORM",
      workspace: true,
      tabMode: "full-path",
      cacheName: "DictionaryManagementPage"
    }
  }
];
