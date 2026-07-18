import type { RouteRecordRaw } from "vue-router";

export const tenantRoutes: RouteRecordRaw[] = [
  {
    path: "tenant/tenants/:tenantId/accounts",
    name: "tenant-accounts",
    component: () => import("../../features/accounts/PlatformAccountsPage.vue"),
    meta: {
      title: "账号管理",
      titleKey: "route.tenantAccounts",
      hidden: true,
      permission: "account.security.read",
      principalType: "TENANT",
      tenantParam: "tenantId",
      workspace: true,
      tabMode: "full-path",
      cacheName: "PlatformAccountsPage"
    }
  },
  {
    path: "tenant/tenants/:tenantId/accounts/:accountId",
    name: "tenant-account-detail",
    component: () => import("../../features/accounts/PlatformAccountsPage.vue"),
    meta: {
      title: "账号详情",
      titleKey: "route.platformAccountDetail",
      hidden: true,
      permission: "account.security.read",
      principalType: "TENANT",
      tenantParam: "tenantId",
      workspace: true,
      tabMode: "full-path",
      cacheName: "PlatformAccountsPage"
    }
  },
  {
    path: "tenant/tenants/:tenantId/members",
    name: "tenant-members",
    component: () => import("../../features/tenants/TenantMembersPage.vue"),
    meta: {
      title: "成员管理",
      titleKey: "route.memberManagement",
      hidden: true,
      routeCode: "tenant.members",
      permission: "tenant:members:read",
      principalType: "TENANT",
      tenantParam: "tenantId",
      workspace: true,
      tabMode: "full-path",
      cacheName: "TenantMembersPage"
    }
  },
  {
    path: "tenant/tenants/:tenantId/members/:principalId",
    name: "tenant-member-detail",
    component: () => import("../../features/tenants/TenantMembersPage.vue"),
    meta: {
      title: "成员详情",
      titleKey: "route.tenantMemberDetail",
      hidden: true,
      permission: "tenant:members:read",
      principalType: "TENANT",
      tenantParam: "tenantId",
      workspace: true,
      tabMode: "full-path",
      cacheName: "TenantMembersPage"
    }
  },
  {
    path: "tenant/tenants/:tenantId/roles",
    name: "tenant-roles",
    component: () => import("../../features/roles/RoleManagementPage.vue"),
    meta: {
      title: "角色管理",
      titleKey: "route.tenantRoles",
      hidden: true,
      routeCode: "tenant.roles",
      permission: "tenant:roles:read",
      principalType: "TENANT",
      tenantParam: "tenantId",
      workspace: true,
      tabMode: "full-path",
      cacheName: "RoleManagementPage"
    }
  },
  {
    path: "tenant/tenants/:tenantId/roles/:roleId",
    name: "tenant-role-detail",
    component: () => import("../../features/roles/RoleManagementPage.vue"),
    meta: {
      title: "角色详情",
      titleKey: "route.roleDetail",
      hidden: true,
      permission: "tenant:roles:read",
      principalType: "TENANT",
      tenantParam: "tenantId",
      workspace: true,
      tabMode: "full-path",
      cacheName: "RoleManagementPage"
    }
  },
  {
    path: "tenant/tenants/:tenantId/identity-providers",
    name: "tenant-identity-providers",
    component: () => import("../../features/identity-providers/IdentityProvidersPage.vue"),
    meta: {
      title: "身份源",
      titleKey: "route.tenantIdentityProviders",
      hidden: true,
      routeCode: "tenant.identity-providers",
      permission: "tenant:identity-providers:read",
      principalType: "TENANT",
      tenantParam: "tenantId",
      workspace: true,
      cacheName: "IdentityProvidersPage"
    }
  },
  {
    path: "tenant/tenants/:tenantId/dictionaries",
    name: "tenant-dictionaries",
    component: () => import("../../features/dictionaries/DictionaryManagementPage.vue"),
    meta: {
      title: "字典管理",
      titleKey: "route.dictionaries",
      hidden: true,
      routeCode: "tenant.dictionaries",
      permission: "tenant:dictionaries:read",
      principalType: "TENANT",
      tenantParam: "tenantId",
      workspace: true,
      tabMode: "full-path",
      cacheName: "DictionaryManagementPage"
    }
  },
  {
    path: "tenant/tenants/:tenantId/dictionaries/:dictionaryId",
    name: "tenant-dictionary-detail",
    component: () => import("../../features/dictionaries/DictionaryManagementPage.vue"),
    meta: {
      title: "字典详情",
      titleKey: "route.dictionaryDetail",
      hidden: true,
      permission: "tenant:dictionaries:read",
      principalType: "TENANT",
      tenantParam: "tenantId",
      workspace: true,
      tabMode: "full-path",
      cacheName: "DictionaryManagementPage"
    }
  }
];
