import type { ApiClient } from "../api-client";
import type {
  Id,
  PageData,
  AccountPageQuery,
  AccountSecondFactorRequest,
  AccountSecondFactorView,
  AccountSecurityView,
  AccountSessionView,
  AccountStatus,
  GlobalAccountCreateInput,
  MembershipStatus,
  PrincipalStatus,
  ManagedAccountView,
  ManagedAccountProfileUpdateInput,
  ManagedAccountProfileView,
  PlatformOperatorCreateInput,
  PlatformOperatorPageQuery,
  PlatformOperatorView,
  PlatformRoleView,
  TenantCreateInput,
  AccessAction,
  PlatformTenantAccessView,
  TenantMemberCreateAccountInput,
  TenantMemberExistingAccountInput,
  TenantMemberPageQuery,
  TenantMemberView,
  TenantRoleView,
  TenantPageQuery,
  TenantStatus,
  TenantView,
} from "../types";

/** 创建读取和完整替换 PLATFORM Principal 租户 ACL 的请求方法。 */
export function createPlatformTenantAccessApi(client: ApiClient) {
  const root = "/mg/platform/operators";
  return {
    current(principalId: Id, signal?: AbortSignal): Promise<PlatformTenantAccessView> {
      return client.get(`${root}/${principalId}/tenant-access-grants`, undefined, signal);
    },
    replace(
      principalId: Id,
      defaultAction: AccessAction | null,
      grants: Array<{ tenantId: Id; accessAction: AccessAction }>,
      version: Id,
      idempotencyKey: string
    ): Promise<void> {
      return client.put(`${root}/${principalId}/tenant-access-grants`, {
        defaultAction,
        grants,
        version,
        idempotencyKey
      });
    }
  };
}

/** 创建平台 GLOBAL Account 与其安全子资源请求方法。 */
export function createPlatformAccountApi(client: ApiClient) {
  const root = "/mg/platform/accounts";
  return {
    page(query: AccountPageQuery, signal?: AbortSignal): Promise<PageData<ManagedAccountView>> {
      return client.get(root, query, signal);
    },
    profile(accountId: Id, signal?: AbortSignal): Promise<ManagedAccountProfileView> {
      return client.get(`${root}/${encodeURIComponent(accountId)}/profile`, undefined, signal);
    },
    updateProfile(
      accountId: Id,
      input: ManagedAccountProfileUpdateInput,
      authenticationId: string
    ): Promise<void> {
      return client.request<void, ManagedAccountProfileUpdateInput>({
        path: `${root}/${encodeURIComponent(accountId)}/profile`,
        method: "PUT",
        headers: { "X-Authentication-Id": authenticationId },
        body: input
      });
    },
    replaceAvatar(
      accountId: Id,
      fileId: Id,
      uploadGrant: string,
      profileVersion: Id
    ): Promise<void> {
      return client.put(`${root}/${encodeURIComponent(accountId)}/profile/avatar`, {
        fileId,
        uploadGrant,
        profileVersion
      });
    },
    removeAvatar(accountId: Id, profileVersion: Id): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}/profile/avatar`,
        method: "DELETE",
        body: { profileVersion }
      });
    },
    changeStatus(accountId: Id, status: AccountStatus, version: Id): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}`,
        method: "PATCH",
        headers: { "If-Match": `"${version}"` },
        body: { status }
      });
    },
    delete(accountId: Id, version: Id): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}`,
        method: "DELETE",
        headers: { "If-Match": `"${version}"` }
      });
    },
    create(input: GlobalAccountCreateInput): Promise<Id> {
      return client.post(root, input);
    },
    security(accountId: Id, signal?: AbortSignal): Promise<AccountSecurityView> {
      return client.get(`${root}/${encodeURIComponent(accountId)}/security`, undefined, signal);
    },
    sessions(accountId: Id, signal?: AbortSignal): Promise<AccountSessionView[]> {
      return client.get(`${root}/${encodeURIComponent(accountId)}/sessions`, undefined, signal);
    },
    changeSecondFactorRequirement(
      accountId: Id,
      request: AccountSecondFactorRequest,
      authenticationId: string
    ): Promise<AccountSecondFactorView> {
      return client.request<AccountSecondFactorView, AccountSecondFactorRequest>({
        path: `${root}/${encodeURIComponent(accountId)}/second-factor/requirement`,
        method: "PUT",
        headers: {
          "If-Match": `"${request.version}"`,
          "X-Authentication-Id": authenticationId
        },
        body: request
      });
    },
    reissueRecoveryCodes(accountId: Id, version: Id, authenticationId: string): Promise<AccountSecondFactorView> {
      return client.request<AccountSecondFactorView>({
        path: `${root}/${encodeURIComponent(accountId)}/recovery-codes`,
        method: "POST",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        }
      });
    },
    bindPassword(
      accountId: Id,
      identifier: string,
      password: string,
      idempotencyKey: string,
      version: Id,
      authenticationId: string
    ): Promise<Id> {
      return client.request<Id>({
        path: `${root}/${encodeURIComponent(accountId)}/credentials/password`,
        method: "POST",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        },
        body: { identifier, password, idempotencyKey }
      });
    },
    replacePassword(
      accountId: Id,
      credentialId: Id,
      password: string,
      version: Id,
      authenticationId: string
    ): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}/credentials/${encodeURIComponent(credentialId)}/password`,
        method: "PUT",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        },
        body: { password }
      });
    },
    endSession(
      accountId: Id,
      sessionId: string,
      version: Id,
      authenticationId: string
    ): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}/sessions/${encodeURIComponent(sessionId)}`,
        method: "DELETE",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        }
      });
    },
    endSessions(accountId: Id, version: Id, authenticationId: string): Promise<void> {
      return client.request<void>({
        path: `${root}/${encodeURIComponent(accountId)}/sessions`,
        method: "DELETE",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        }
      });
    }
  };
}

function createTenantAccountApi(client: ApiClient, root: (tenantId: Id) => string) {
  return {
    page(
      tenantId: Id,
      query: AccountPageQuery,
      signal?: AbortSignal
    ): Promise<PageData<ManagedAccountView>> {
      return client.get(root(tenantId), query, signal);
    },
    security(tenantId: Id, accountId: Id, signal?: AbortSignal): Promise<AccountSecurityView> {
      return client.get(`${root(tenantId)}/${encodeURIComponent(accountId)}/security`, undefined, signal);
    },
    sessions(tenantId: Id, accountId: Id, signal?: AbortSignal): Promise<AccountSessionView[]> {
      return client.get(`${root(tenantId)}/${encodeURIComponent(accountId)}/sessions`, undefined, signal);
    },
    changeSecondFactorRequirement(
      tenantId: Id,
      accountId: Id,
      request: AccountSecondFactorRequest,
      authenticationId: string
    ): Promise<AccountSecondFactorView> {
      return client.request<AccountSecondFactorView, AccountSecondFactorRequest>({
        path: `${root(tenantId)}/${encodeURIComponent(accountId)}/second-factor/requirement`,
        method: "PUT",
        headers: {
          "If-Match": `"${request.version}"`,
          "X-Authentication-Id": authenticationId
        },
        body: request
      });
    },
    reissueRecoveryCodes(
      tenantId: Id,
      accountId: Id,
      version: Id,
      authenticationId: string
    ): Promise<AccountSecondFactorView> {
      return client.request<AccountSecondFactorView>({
        path: `${root(tenantId)}/${encodeURIComponent(accountId)}/recovery-codes`,
        method: "POST",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        }
      });
    },
    bindPassword(
      tenantId: Id,
      accountId: Id,
      identifier: string,
      password: string,
      idempotencyKey: string,
      version: Id,
      authenticationId: string
    ): Promise<Id> {
      return client.request<Id>({
        path: `${root(tenantId)}/${encodeURIComponent(accountId)}/credentials/password`,
        method: "POST",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        },
        body: { identifier, password, idempotencyKey }
      });
    },
    replacePassword(
      tenantId: Id,
      accountId: Id,
      credentialId: Id,
      password: string,
      version: Id,
      authenticationId: string
    ): Promise<void> {
      return client.request<void>({
        path: `${root(tenantId)}/${encodeURIComponent(accountId)}/credentials/${encodeURIComponent(credentialId)}/password`,
        method: "PUT",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        },
        body: { password }
      });
    },
    endSession(
      tenantId: Id,
      accountId: Id,
      sessionId: string,
      version: Id,
      authenticationId: string
    ): Promise<void> {
      return client.request<void>({
        path: `${root(tenantId)}/${encodeURIComponent(accountId)}/sessions/${encodeURIComponent(sessionId)}`,
        method: "DELETE",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        }
      });
    },
    endSessions(
      tenantId: Id,
      accountId: Id,
      version: Id,
      authenticationId: string
    ): Promise<void> {
      return client.request<void>({
        path: `${root(tenantId)}/${encodeURIComponent(accountId)}/sessions`,
        method: "DELETE",
        headers: {
          "If-Match": `"${version}"`,
          "X-Authentication-Id": authenticationId
        }
      });
    }
  };
}

/** 创建平台代管指定租户 Account 与其安全子资源请求方法。 */
export function createPlatformTenantAccountApi(client: ApiClient) {
  return createTenantAccountApi(client, (tenantId) => `/mg/platform/tenants/${tenantId}/accounts`);
}

/** 创建当前租户 Account 与其安全子资源请求方法。 */
export function createTenantAccountManagementApi(client: ApiClient) {
  return createTenantAccountApi(client, (tenantId) => `/mg/tenant/tenants/${tenantId}/accounts`);
}

/** 创建平台操作员、平台角色和租户 ACL 请求方法。 */
export function createPlatformOperatorApi(client: ApiClient) {
  const root = "/mg/platform/operators";
  return {
    page(query: PlatformOperatorPageQuery, signal?: AbortSignal): Promise<PageData<PlatformOperatorView>> {
      return client.get(root, query, signal);
    },
    detail(principalId: Id, signal?: AbortSignal): Promise<PlatformOperatorView> {
      return client.get(`${root}/${encodeURIComponent(principalId)}`, undefined, signal);
    },
    create(input: PlatformOperatorCreateInput): Promise<Id> {
      return client.post(root, input);
    },
    assignableRoles(principalId: Id): Promise<PlatformRoleView[]> {
      return client.get(`${root}/${encodeURIComponent(principalId)}/assignable-roles`);
    },
    changeStatus(principalId: Id, status: PrincipalStatus, version: Id): Promise<void> {
      return client.patch(`${root}/${encodeURIComponent(principalId)}`, { status, version });
    },
    replaceRoles(principalId: Id, roleIds: Id[], version: Id): Promise<void> {
      return client.put(`${root}/${encodeURIComponent(principalId)}/roles`, { roleIds, version });
    }
  };
}

/** 创建平台租户查询、创建、修改、停用和删除请求方法。 */
export function createPlatformTenantApi(client: ApiClient) {
  const root = "/mg/platform/tenants";
  return {
    page(query: TenantPageQuery, signal?: AbortSignal): Promise<PageData<TenantView>> {
      return client.get(root, query, signal);
    },
    detail(tenantId: Id, signal?: AbortSignal): Promise<TenantView> {
      return client.get(`${root}/${tenantId}`, undefined, signal);
    },
    create(input: TenantCreateInput): Promise<Id> {
      return client.post(root, input);
    },
    update(tenantId: Id, code: string, name: string, version: Id): Promise<void> {
      return client.put(`${root}/${tenantId}`, { code, name, version });
    },
    changeStatus(tenantId: Id, status: TenantStatus, version: Id): Promise<void> {
      return client.patch(`${root}/${tenantId}`, { status, version });
    },
    delete(tenantId: Id, version: Id, idempotencyKey: string): Promise<void> {
      return client.request({
        path: `${root}/${tenantId}`,
        method: "DELETE",
        body: { version, idempotencyKey }
      });
    }
  };
}

function createTenantMemberApi(client: ApiClient, root: (tenantId: Id) => string) {
  return {
    assignableRoles(tenantId: Id): Promise<TenantRoleView[]> {
      return client.get(`${root(tenantId)}/assignable-roles`);
    },
    page(
      tenantId: Id,
      query: TenantMemberPageQuery,
      signal?: AbortSignal
    ): Promise<PageData<TenantMemberView>> {
      return client.get(root(tenantId), query, signal);
    },
    detail(tenantId: Id, principalId: Id, signal?: AbortSignal): Promise<TenantMemberView> {
      return client.get(`${root(tenantId)}/${principalId}`, undefined, signal);
    },
    createAccount(tenantId: Id, input: TenantMemberCreateAccountInput): Promise<Id> {
      return client.post(root(tenantId), input);
    },
    addExistingAccount(tenantId: Id, input: TenantMemberExistingAccountInput): Promise<Id> {
      return client.post(`${root(tenantId)}/existing-accounts`, input);
    },
    changeStatus(tenantId: Id, principalId: Id, status: MembershipStatus, version: Id): Promise<void> {
      return client.patch(`${root(tenantId)}/${principalId}`, { status, version });
    },
    replaceRoles(tenantId: Id, principalId: Id, roleIds: Id[], version: Id): Promise<void> {
      return client.put(`${root(tenantId)}/${principalId}/roles`, { roleIds, version });
    }
  };
}

/** 创建平台代管指定租户成员的请求方法。 */
export function createPlatformTenantMemberApi(client: ApiClient) {
  return createTenantMemberApi(client, (tenantId) => `/mg/platform/tenants/${tenantId}/members`);
}

/** 创建当前租户成员查询、创建 Account、改状态和换角色的请求方法。 */
export function createTenantMemberManagementApi(client: ApiClient) {
  return createTenantMemberApi(client, (tenantId) => `/mg/tenant/tenants/${tenantId}/members`);
}
