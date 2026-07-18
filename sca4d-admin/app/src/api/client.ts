import {
  createApiClient,
  createAccountSecurityApi,
  createAuthenticationApi,
  createDictionaryApi,
  createFileApi,
  createIdentityProviderApi,
  createLanguageApi,
  createMailSenderApi,
  createMailTemplateApi,
  createMeApi,
  createPlatformAccountApi,
  createPlatformTenantAccountApi,
  createPlatformOperatorApi,
  createPlatformTenantApi,
  createPlatformTenantAccessApi,
  createPlatformTenantMemberApi,
  createRoleApi,
  createSystemCatalogApi,
  createSystemMenuApi,
  createPlatformScheduleApi,
  createRealtimeClient,
  createSystemScheduledJobApi,
  createTenantMemberManagementApi,
  createTenantAccountManagementApi,
  type ClientStateChange,
  type SessionToken
} from "@sca4d/admin-common";
import { getApplicationLocale, translateApplicationMessage } from "../i18n";

/** 应用启动时绑定到 API 客户端的认证 Store、工作区 Store 和路由跳转函数。 */
export interface ApiStateBinding {
  token: () => SessionToken | null;
  unauthorized: () => void;
  clientState: (change: ClientStateChange) => void;
  realtimeEvent: (event: string) => void;
  realtimeReconnect: () => void;
}

let binding: ApiStateBinding = {
  token: () => null,
  unauthorized: () => undefined,
  clientState: () => undefined,
  realtimeEvent: () => undefined,
  realtimeReconnect: () => undefined
};

/** 把认证失效、权限刷新和菜单刷新回调连接到应用 Store。 */
export function bindApiState(next: ApiStateBinding): void {
  binding = next;
}

/** 管理端调用普通 HTTP 接口使用的共享 API 客户端。 */
export const apiClient = createApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api",
  locale: getApplicationLocale,
  translate: translateApplicationMessage,
  token: () => binding.token(),
  onUnauthorized: () => binding.unauthorized(),
  onClientState: (change) => binding.clientState(change)
});

/** 管理端建立 SSE 和实时订阅时使用的共享 API 客户端。 */
export const realtimeClient = createRealtimeClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api",
  token: () => binding.token(),
  onEvent: (event) => binding.realtimeEvent(event.event),
  onReconnect: () => binding.realtimeReconnect(),
  onUnauthorized: () => binding.unauthorized()
});

/** 认证方式、认证过程和当前 Session 的请求方法。 */
export const authenticationApi = createAuthenticationApi(apiClient);
/** 当前用户账号、认证资料和登录会话自助管理请求方法。 */
export const accountSecurityApi = createAccountSecurityApi(apiClient);
/** 读取当前用户快照和修改当前头像的请求方法。 */
export const meApi = createMeApi(apiClient);
/** 文件签名、上传字节和完成上传的请求方法。 */
export const fileApi = createFileApi(apiClient);
/** 读取后端支持语言目录的请求方法。 */
export const languageApi = createLanguageApi(apiClient);
/** 平台 GLOBAL Account 及其安全子资源请求方法。 */
export const platformAccountApi = createPlatformAccountApi(apiClient);
export const platformTenantAccountApi = createPlatformTenantAccountApi(apiClient);
/** 平台操作员、平台角色和租户 ACL 请求方法。 */
export const platformOperatorApi = createPlatformOperatorApi(apiClient);
/** 平台租户查询、创建、修改、停用和删除请求方法。 */
export const platformTenantApi = createPlatformTenantApi(apiClient);
/** PLATFORM Principal 租户 ACL 查询和完整替换请求方法。 */
export const platformTenantAccessApi = createPlatformTenantAccessApi(apiClient);
/** 平台代管指定租户成员的请求方法。 */
export const platformTenantMemberApi = createPlatformTenantMemberApi(apiClient);
/** 平台角色查询、写入、换权限和删除请求方法。 */
export const platformRoleApi = createRoleApi(apiClient, "/mg/platform/roles");
/** 平台代管指定租户角色的请求方法。 */
export const platformTenantRoleApi = (tenantId: string) =>
  createRoleApi(apiClient, `/mg/platform/tenants/${tenantId}/roles`);
/** 当前租户角色查询、写入、换权限和删除请求方法。 */
export const tenantRoleApi = (tenantId: string) =>
  createRoleApi(apiClient, `/mg/tenant/tenants/${tenantId}/roles`);
/** 当前租户成员查询、创建用户、改状态和换角色请求方法。 */
export const tenantMemberManagementApi = createTenantMemberManagementApi(apiClient);
export const tenantAccountManagementApi = createTenantAccountManagementApi(apiClient);
/** 权限目录和错误码目录请求方法。 */
export const systemCatalogApi = createSystemCatalogApi(apiClient);
/** 平台字典类型、字典项和排序请求方法。 */
export const systemDictionaryApi = createDictionaryApi(apiClient, "/mg/system/dictionaries");
/** 平台菜单树、路由目录、写入和重排请求方法。 */
export const systemMenuApi = createSystemMenuApi(apiClient);
/** 平台 Schedule 查询、写入、删除和人工执行请求方法。 */
export const platformScheduleApi = createPlatformScheduleApi(apiClient);
/** 系统 Quartz Job 状态只读查询方法。 */
export const systemScheduledJobApi = createSystemScheduledJobApi(apiClient);
/** SMTP 邮件发送配置查询、写入、连接测试和启停方法。 */
export const mailSenderApi = createMailSenderApi(apiClient);
/** 邮件模板查询、写入、预览、测试发送和启停方法。 */
export const mailTemplateApi = createMailTemplateApi(apiClient);
/** GLOBAL IdentityProvider 与三种标准协议配置管理方法。 */
export const systemIdentityProviderApi = createIdentityProviderApi(
  apiClient,
  "/mg/system/auth/providers"
);
/** 当前租户 IdentityProvider 与三种标准协议配置管理方法。 */
export const tenantIdentityProviderApi = (tenantId: string) =>
  createIdentityProviderApi(
    apiClient,
    `/mg/tenant/tenants/${encodeURIComponent(tenantId)}/auth/providers`
  );
/** 平台代管指定租户字典的请求方法。 */
export const platformTenantDictionaryApi = (tenantId: string) =>
  createDictionaryApi(apiClient, `/mg/platform/tenants/${tenantId}/dictionaries`);
/** 当前租户字典类型、字典项和排序请求方法。 */
export const tenantDictionaryApi = (tenantId: string) =>
  createDictionaryApi(apiClient, `/mg/tenant/tenants/${tenantId}/dictionaries`);
