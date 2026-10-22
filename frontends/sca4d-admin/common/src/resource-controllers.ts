import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type { PermissionTreeItem } from './permissions';
import type {
  AccountPage,
  AccountRoles,
  AccountStatus,
  CreateAccountInput,
  Int64String,
  LocaleCatalog,
  LocalizedFields,
  PasskeyOptions,
  PasskeyProof,
  PasswordStatus,
  PermissionCatalog,
  PlatformAccount,
  PlatformRole,
  Profile,
  RoleI18n,
  RolePermissions,
  RoleStatus,
  Verification,
} from './types';

import { verificationHeaders } from './action-verification-controller';
import { ObservableController } from './controller';
import { SavedResourceRefreshError } from './errors';
import { SupersededReadError } from './errors';
import { AdminError } from './errors';
import { VERIFICATION_HEADER } from './http-contract';
import { freezeLocaleCatalog } from './localized-fields';
import { permissionTree } from './permissions';

interface AccountPageResponse extends Omit<AccountPage, 'total'> {
  readonly total: Int64String;
}

/** Platform Account 列表筛选。 */
export interface AccountFilters {
  readonly page: number;
  readonly search: string;
  readonly size: number;
  readonly status: '' | AccountStatus;
}

/** Platform Account 页面状态。 */
export interface AccountsState {
  readonly error: AdminError | null;
  readonly filters: AccountFilters;
  readonly page: AccountPage;
  readonly pendingDeletion: null | {
    readonly account: PlatformAccount;
    readonly verification: Verification;
  };
  readonly phase: ResourcePhase;
  readonly roles: readonly PlatformRole[];
  readonly selectedRoles: AccountRoles | null;
  readonly selectedRolesEtag: null | string;
}

/** 删除账号提交 TOTP 后的下一步。 */
export type AccountDeletionOutcome = 'deleted' | 'factorRequired';

const EMPTY_PAGE: AccountPage = Object.freeze({
  items: Object.freeze([]),
  page: 1,
  size: 20,
  total: 0,
});

/** 管理 Platform Account 分页、创建、状态和角色请求流程。 */
export class AccountsController extends ObservableController<AccountsState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      phase: 'idle',
      page: EMPTY_PAGE,
      filters: Object.freeze({ page: 1, size: 20, search: '', status: '' }),
      roles: Object.freeze([]),
      selectedRoles: null,
      selectedRolesEtag: null,
      pendingDeletion: null,
      error: null,
    });
  }

  /** 开始账号终态删除认证；Session 中的近期认证已经满足要求时直接删除。 */
  async beginDeletion(
    account: PlatformAccount,
  ): Promise<AccountDeletionOutcome> {
    this.publish({
      ...this.state,
      phase: 'loading',
      pendingDeletion: null,
      error: null,
    });
    try {
      const started = await this.api.post<Verification>('/verifications', {
        actionCode: 'account.delete',
      });
      if (started.data.status === 'SATISFIED') {
        await this.finishDeletion(account, started.data);
        return 'deleted';
      }
      this.publishPendingDeletion(account, started.data);
      return 'factorRequired';
    } catch (error) {
      this.resourceFailure(error);
      throw error;
    }
  }

  /** 清除页面尚未完成的账号删除操作。 */
  cancelDeletion(): void {
    this.publish({ ...this.state, pendingDeletion: null, error: null });
  }

  /** 修改账号状态并刷新当前页。 */
  async changeStatus(
    account: PlatformAccount,
    status: AccountStatus,
  ): Promise<void> {
    try {
      await this.api.patch<PlatformAccount>(
        `/platform/accounts/${account.id}`,
        { status },
      );
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.resourceFailure(error);
      throw error;
    }
  }

  /** 原子创建完整账号并刷新第一页。 */
  async create(input: CreateAccountInput): Promise<PlatformAccount> {
    const result = await this.api.post<PlatformAccount>(
      '/platform/accounts',
      input,
    );
    try {
      await this.load({ page: 1 });
    } catch {
      throw new SavedResourceRefreshError();
    }
    return result.data;
  }

  /** 开始账号删除 verification 使用的 Passkey assertion。 */
  async createDeletionPasskeyOptions(): Promise<PasskeyOptions> {
    const result = await this.api.post<PasskeyOptions>(
      '/passkey/assertions/options',
    );
    return result.data;
  }

  /** 删除指定 Platform Account 当前绑定的头像。 */
  async deleteAvatar(accountId: Int64String): Promise<PlatformAccount> {
    try {
      const result = await this.api.delete<PlatformAccount>(
        `/platform/accounts/${accountId}/profile/avatar`,
      );
      this.replaceAccount(result.data);
      return result.data;
    } catch (error) {
      this.resourceFailure(error);
      throw error;
    }
  }

  /** 使用完整筛选条件读取账号页。 */
  async load(filters: Partial<AccountFilters> = {}): Promise<void> {
    const read = this.beginRead('load');
    const next = Object.freeze({ ...this.state.filters, ...filters });
    this.publish({
      ...this.state,
      phase: 'loading',
      filters: next,
      error: null,
    });
    try {
      const result = await this.api.get<AccountPageResponse>(
        '/platform/accounts',
        {
          page: next.page,
          size: next.size,
          search: next.search || undefined,
          status: next.status || undefined,
        },
      );
      const page = freezePage(result.data);
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        ...this.state,
        phase: page.items.length > 0 ? 'ready' : 'empty',
        page,
        error: null,
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      this.resourceFailure(error);
      throw error;
    }
  }

  /** 读取全部角色和指定账号当前角色。 */
  async loadRoles(accountId: Int64String): Promise<void> {
    const read = this.beginRead('loadRoles');
    try {
      const [roles, selected] = await Promise.all([
        this.api.get<readonly PlatformRole[]>('/platform/roles'),
        this.api.get<AccountRoles>(`/platform/accounts/${accountId}/roles`),
      ]);
      if (!selected.etag) throw new Error('Account roles response has no ETag');
      if (!this.isCurrentRead('loadRoles', read)) return;
      this.publish({
        ...this.state,
        roles: Object.freeze([...roles.data]),
        selectedRoles: Object.freeze({
          ...selected.data,
          roleIds: Object.freeze([...selected.data.roleIds]),
        }),
        selectedRolesEtag: selected.etag,
      });
    } catch (error) {
      if (!this.isCurrentRead('loadRoles', read)) return;
      this.resourceFailure(error);
      throw error;
    }
  }

  /** 把上传完成的头像文件绑定到指定 Platform Account。 */
  async replaceAvatar(
    accountId: Int64String,
    fileId: Int64String,
  ): Promise<PlatformAccount> {
    try {
      const result = await this.api.put<PlatformAccount>(
        `/platform/accounts/${accountId}/profile/avatar`,
        { fileId },
      );
      this.replaceAccount(result.data);
      return result.data;
    } catch (error) {
      this.resourceFailure(error);
      throw error;
    }
  }

  /** 原子替换当前选中账号的角色。 */
  async replaceRoles(roleIds: readonly Int64String[]): Promise<void> {
    const selected = this.state.selectedRoles;
    const selectedRolesEtag = this.state.selectedRolesEtag;
    if (!selected || !selectedRolesEtag)
      throw new Error('No account roles are loaded');
    try {
      const result = await this.api.put<AccountRoles>(
        `/platform/accounts/${selected.accountId}/roles`,
        { roleIds },
        { 'If-Match': selectedRolesEtag },
      );
      if (!result.etag) throw new Error('Account roles response has no ETag');
      this.publish({
        ...this.state,
        selectedRoles: Object.freeze({
          ...result.data,
          roleIds: Object.freeze([...result.data.roleIds]),
        }),
        selectedRolesEtag: result.etag,
      });
    } catch (error) {
      this.resourceFailure(error);
      throw error;
    }
  }

  /** 读取一个完整 Platform Account，供深链接详情和资源引用使用。 */
  async require(accountId: Int64String): Promise<PlatformAccount> {
    const read = this.beginRead('detail');
    try {
      const result = await this.api.get<PlatformAccount>(
        `/platform/accounts/${encodeURIComponent(accountId)}`,
      );
      if (!this.isCurrentRead('detail', read)) throw new SupersededReadError();
      return result.data;
    } catch (error) {
      if (!this.isCurrentRead('detail', read)) throw new SupersededReadError();
      this.resourceFailure(error);
      throw error;
    }
  }

  /** 向待完成的账号删除 verification 提交 Passkey。 */
  async submitDeletionPasskey(
    proof: PasskeyProof,
  ): Promise<AccountDeletionOutcome> {
    return this.submitDeletionProof('passkey', proof);
  }

  /** 向待完成的账号删除 verification 提交密码。 */
  async submitDeletionPassword(
    password: string,
  ): Promise<AccountDeletionOutcome> {
    return this.submitDeletionProof('password', { password });
  }

  /** 向待完成的账号删除 verification 提交 TOTP。 */
  async submitDeletionTotp(code: string): Promise<AccountDeletionOutcome> {
    return this.submitDeletionProof('totp', { code });
  }

  private async finishDeletion(
    account: PlatformAccount,
    verification: Verification,
  ): Promise<void> {
    await this.api.delete(
      `/platform/accounts/${account.id}`,
      {
        [VERIFICATION_HEADER]: verification.verificationId,
      },
      'session',
    );
    this.publish({ ...this.state, pendingDeletion: null, error: null });
    try {
      await this.load();
    } catch {
      throw new SavedResourceRefreshError();
    }
  }

  private publishPendingDeletion(
    account: PlatformAccount,
    verification: Verification,
  ): void {
    this.publish({
      ...this.state,
      phase: 'ready',
      pendingDeletion: Object.freeze({
        account: Object.freeze(account),
        verification: Object.freeze(verification),
      }),
      error: null,
    });
  }

  private replaceAccount(account: PlatformAccount): void {
    this.publish({
      ...this.state,
      page: Object.freeze({
        ...this.state.page,
        items: Object.freeze(
          this.state.page.items.map((candidate) =>
            candidate.id === account.id ? Object.freeze(account) : candidate,
          ),
        ),
      }),
      error: null,
    });
  }

  private resourceFailure(error: unknown): void {
    const value = asAdminError(error);
    let phase: ResourcePhase = 'error';
    if (value.kind === 'permission') phase = 'forbidden';
    else if (value.kind === 'versionConflict') phase = 'conflict';
    this.publish({ ...this.state, phase, error: value });
  }

  private async submitDeletionProof(
    method: 'passkey' | 'password' | 'totp',
    proof: unknown,
  ): Promise<AccountDeletionOutcome> {
    const pending = this.state.pendingDeletion;
    if (!pending) throw new Error('No account deletion is pending');
    this.publish({ ...this.state, phase: 'loading', error: null });
    try {
      const proved = await this.api.post<Verification>(
        `/verifications/${encodeURIComponent(pending.verification.verificationId)}/${method}/proofs`,
        proof,
      );
      if (proved.data.status !== 'SATISFIED') {
        this.publishPendingDeletion(pending.account, proved.data);
        return 'factorRequired';
      }
      await this.finishDeletion(pending.account, proved.data);
      return 'deleted';
    } catch (error) {
      this.resourceFailure(error);
      throw error;
    }
  }
}

/** Platform 角色页面状态。 */
export interface RolesState {
  readonly catalog: null | PermissionCatalog;
  readonly error: AdminError | null;
  readonly i18n: null | RoleI18n;
  /** 与名称快照一起读取的强 ETag，不由其他角色操作替换。 */
  readonly i18nEtag: null | string;
  readonly localeCatalog: LocaleCatalog | null;
  readonly permissions: null | RolePermissions;
  /** 与权限集合一起读取的强 ETag。 */
  readonly permissionsEtag: null | string;
  readonly permissionTree: readonly PermissionTreeItem[];
  readonly phase: ResourcePhase;
  readonly roleEtag: null | {
    readonly roleId: Int64String;
    readonly value: string;
  };
  readonly roles: readonly PlatformRole[];
}

/** 管理角色 CRUD 与权限树分配流程。 */
export class RolesController extends ObservableController<RolesState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      phase: 'idle',
      roles: Object.freeze([]),
      i18n: null,
      i18nEtag: null,
      localeCatalog: null,
      permissions: null,
      permissionsEtag: null,
      roleEtag: null,
      catalog: null,
      permissionTree: Object.freeze([]),
      error: null,
    });
  }

  /** 使用角色详情的统一 ETag 修改角色状态。 */
  async changeStatus(role: PlatformRole, status: RoleStatus): Promise<void> {
    try {
      const current = await this.api.get<PlatformRole>(
        `/platform/roles/${role.id}`,
      );
      if (!current.etag) throw new Error('Role response has no ETag');
      const updated = await this.api.put<PlatformRole>(
        `/platform/roles/${role.id}/status`,
        { status },
        { 'If-Match': current.etag },
      );
      if (!updated.etag) throw new Error('Role response has no ETag');
      this.publish({
        ...this.state,
        roleEtag: Object.freeze({ roleId: role.id, value: updated.etag }),
        error: null,
      });
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 创建角色并刷新角色列表。 */
  async create(code: string, i18n: LocalizedFields): Promise<void> {
    try {
      await this.api.post<PlatformRole>('/platform/roles', {
        code,
        i18n,
        permissionCodes: [],
      });
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 删除非内置角色。 */
  async delete(role: PlatformRole): Promise<void> {
    try {
      const current = await this.api.get<PlatformRole>(
        `/platform/roles/${role.id}`,
      );
      if (!current.etag) throw new Error('Role response has no ETag');
      await this.api.delete(
        `/platform/roles/${role.id}`,
        { 'If-Match': current.etag },
        'session',
      );
      this.publish({
        ...this.state,
        i18n: this.state.i18n?.roleId === role.id ? null : this.state.i18n,
        permissions:
          this.state.permissions?.roleId === role.id
            ? null
            : this.state.permissions,
        roleEtag:
          this.state.roleEtag?.roleId === role.id ? null : this.state.roleEtag,
      });
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 读取全部角色。 */
  async load(): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, phase: 'loading', error: null });
    try {
      const result =
        await this.api.get<readonly PlatformRole[]>('/platform/roles');
      const roles = Object.freeze([...result.data]);
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        ...this.state,
        phase: roles.length > 0 ? 'ready' : 'empty',
        roles,
        error: null,
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 同时读取角色完整多语言字段和应用 Locale 目录。 */
  async loadI18n(roleId: Int64String): Promise<void> {
    const read = this.beginRead('loadI18n');
    try {
      const [i18n, locales] = await Promise.all([
        this.api.get<RoleI18n>(`/platform/roles/${roleId}/i18n`),
        this.api.get<LocaleCatalog>('/system/locales'),
      ]);
      if (!i18n.etag) throw new Error('Role i18n response has no ETag');
      if (!this.isCurrentRead('loadI18n', read)) return;
      this.publish({
        ...this.state,
        i18n: freezeRoleI18n(i18n.data),
        i18nEtag: i18n.etag,
        localeCatalog: freezeLocaleCatalog(locales.data),
        roleEtag: Object.freeze({ roleId: i18n.data.roleId, value: i18n.etag }),
        error: null,
      });
    } catch (error) {
      if (!this.isCurrentRead('loadI18n', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 读取应用支持和默认使用的 Locale。 */
  async loadLocales(): Promise<void> {
    const read = this.beginRead('loadLocales');
    try {
      const result = await this.api.get<LocaleCatalog>('/system/locales');
      if (!this.isCurrentRead('loadLocales', read)) return;
      this.publish({
        ...this.state,
        localeCatalog: freezeLocaleCatalog(result.data),
        error: null,
      });
    } catch (error) {
      if (!this.isCurrentRead('loadLocales', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 同时读取角色权限和系统权限目录。 */
  async loadPermissions(roleId: Int64String): Promise<void> {
    const read = this.beginRead('loadPermissions');
    try {
      const [permissions, catalog] = await Promise.all([
        this.api.get<RolePermissions>(`/platform/roles/${roleId}/permissions`),
        this.api.get<PermissionCatalog>('/system/permissions'),
      ]);
      if (!permissions.etag)
        throw new Error('Role permissions response has no ETag');
      if (!this.isCurrentRead('loadPermissions', read)) return;
      this.publish({
        ...this.state,
        permissions: Object.freeze({
          ...permissions.data,
          permissionCodes: Object.freeze([...permissions.data.permissionCodes]),
        }),
        permissionsEtag: permissions.etag,
        roleEtag: Object.freeze({ roleId, value: permissions.etag }),
        catalog: catalog.data,
        permissionTree: permissionTree(catalog.data.tree),
        error: null,
      });
    } catch (error) {
      if (!this.isCurrentRead('loadPermissions', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 使用完整多语言字段的 ETag 原子替换角色多语言字段。 */
  async replaceI18n(i18n: LocalizedFields): Promise<void> {
    const current = this.state.i18n;
    const etag = this.state.i18nEtag;
    if (!current || !etag) throw new Error('No role i18n is loaded');
    try {
      const result = await this.api.put<RoleI18n>(
        `/platform/roles/${current.roleId}/i18n`,
        { i18n },
        { 'If-Match': etag },
      );
      if (!result.etag) throw new Error('Role i18n response has no ETag');
      this.publish({
        ...this.state,
        i18n: freezeRoleI18n(result.data),
        i18nEtag: result.etag,
        roleEtag: Object.freeze({
          roleId: result.data.roleId,
          value: result.etag,
        }),
        error: null,
      });
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 使用当前权限集合的 ETag 原子替换权限，并刷新角色和权限状态。 */
  async replacePermissions(permissionCodes: readonly string[]): Promise<void> {
    const permissions = this.state.permissions;
    const role = this.state.roles.find(
      (candidate) => candidate.id === permissions?.roleId,
    );
    const etag = this.state.permissionsEtag;
    if (!permissions || !role || !etag)
      throw new Error('No role permissions are loaded');
    try {
      const result = await this.api.put<RolePermissions>(
        `/platform/roles/${role.id}/permissions`,
        { permissionCodes },
        { 'If-Match': etag },
      );
      if (!result.etag)
        throw new Error('Role permissions response has no ETag');
      this.publish({
        ...this.state,
        roleEtag: Object.freeze({ roleId: role.id, value: result.etag }),
        permissionsEtag: result.etag,
        permissions: Object.freeze({
          ...result.data,
          permissionCodes: Object.freeze([...result.data.permissionCodes]),
        }),
        error: null,
      });
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 读取一个完整 Platform Role，供深链接详情和资源引用使用。 */
  async require(roleId: Int64String): Promise<PlatformRole> {
    const read = this.beginRead('detail');
    try {
      const result = await this.api.get<PlatformRole>(
        `/platform/roles/${encodeURIComponent(roleId)}`,
      );
      if (!this.isCurrentRead('detail', read)) throw new SupersededReadError();
      return result.data;
    } catch (error) {
      if (!this.isCurrentRead('detail', read)) throw new SupersededReadError();
      this.fail(error);
      throw error;
    }
  }

  private fail(error: unknown): void {
    const value = asAdminError(error);
    let phase: ResourcePhase = 'error';
    if (value.kind === 'permission') phase = 'forbidden';
    else if (value.kind === 'versionConflict') phase = 'conflict';
    this.publish({ ...this.state, phase, error: value });
  }
}

/** 只读权限目录状态。 */
export interface PermissionsState {
  readonly catalog: null | PermissionCatalog;
  readonly error: AdminError | null;
  readonly phase: ResourcePhase;
  readonly tree: readonly PermissionTreeItem[];
}

/** 读取并转换系统权限目录。 */
export class PermissionsController extends ObservableController<PermissionsState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      phase: 'idle',
      catalog: null,
      tree: Object.freeze([]),
      error: null,
    });
  }

  /** 读取只读权限目录。 */
  async load(): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, phase: 'loading', error: null });
    try {
      const result = await this.api.get<PermissionCatalog>(
        '/system/permissions',
      );
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        phase: result.data.total ? 'ready' : 'empty',
        catalog: result.data,
        tree: permissionTree(result.data.tree),
        error: null,
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      const value = asAdminError(error);
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        ...this.state,
        phase: value.kind === 'permission' ? 'forbidden' : 'error',
        error: value,
      });
      throw error;
    }
  }
}

/** 当前资料页面状态。 */
export interface ProfileState {
  readonly error: AdminError | null;
  readonly phase: ResourcePhase;
  readonly profile: null | Profile;
}

/** 管理当前账号资料读取和替换。 */
export class ProfileController extends ObservableController<ProfileState> {
  constructor(private readonly api: AdminApiClient) {
    super({ phase: 'idle', profile: null, error: null });
  }

  /** 删除当前账号头像。 */
  async deleteAvatar(): Promise<void> {
    try {
      const result = await this.api.delete<Profile>('/me/profile/avatar');
      this.publish({
        phase: 'ready',
        profile: Object.freeze(result.data),
        error: null,
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 读取当前账号资料。 */
  async load(): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, phase: 'loading', error: null });
    try {
      const result = await this.api.get<Profile>('/me/profile');
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        phase: 'ready',
        profile: Object.freeze(result.data),
        error: null,
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 把上传完成的文件绑定为当前账号头像。 */
  async replaceAvatar(fileId: Int64String): Promise<void> {
    try {
      const result = await this.api.put<Profile>('/me/profile/avatar', {
        fileId,
      });
      this.publish({
        phase: 'ready',
        profile: Object.freeze(result.data),
        error: null,
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 完整替换当前资料。 */
  async save(
    displayName: string,
    email: null | string,
    phoneNumber: null | string,
  ): Promise<void> {
    const profile = this.state.profile;
    if (!profile) throw new Error('Profile is not loaded');
    try {
      const result = await this.api.put<Profile>('/me/profile', {
        displayName,
        email,
        phoneNumber,
      });
      this.publish({
        phase: 'ready',
        profile: Object.freeze(result.data),
        error: null,
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 使用已经满足的动作认证验证并绑定当前 Profile 手机号。 */
  async verifyPhone(verification: Verification): Promise<void> {
    try {
      await this.api.put<unknown>(
        '/me/phone-identifier',
        undefined,
        verificationHeaders(verification),
        'session',
      );
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  private fail(error: unknown): void {
    const value = asAdminError(error);
    this.publish({
      ...this.state,
      phase: value.kind === 'versionConflict' ? 'conflict' : 'error',
      error: value,
    });
  }
}

/** 修改密码页面状态。 */
export interface PasswordState {
  readonly error: AdminError | null;
  readonly password: null | PasswordStatus;
  readonly phase: ResourcePhase;
}

/** 读取密码凭据状态，并在动作认证满足后替换密码。 */
export class PasswordController extends ObservableController<PasswordState> {
  constructor(
    private readonly api: AdminApiClient,
    private readonly onSessionInvalidated: () => void,
  ) {
    super({ error: null, password: null, phase: 'idle' });
  }

  /** 读取不含摘要的密码状态。 */
  async load(): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, phase: 'loading', error: null });
    try {
      const result = await this.api.get<PasswordStatus>('/me/password');
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        error: null,
        password: Object.freeze(result.data),
        phase: 'ready',
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 使用已经满足的动作认证替换密码，并在成功后使当前 Session 失效。 */
  async replace(
    newPassword: string,
    verification: Verification,
  ): Promise<void> {
    if (!this.state.password) throw new Error('密码状态尚未加载');
    this.publish({ ...this.state, phase: 'loading', error: null });
    try {
      await this.api.put<unknown>(
        '/me/password',
        { password: newPassword },
        verificationHeaders(verification),
        'session',
      );
      this.publish({ error: null, password: null, phase: 'ready' });
      this.onSessionInvalidated();
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  private fail(error: unknown): void {
    const value = asAdminError(error);
    this.publish({
      ...this.state,
      phase: value.kind === 'versionConflict' ? 'conflict' : 'error',
      error: value,
    });
  }
}

function freezePage(page: AccountPageResponse): AccountPage {
  if (!/^(0|[1-9][0-9]*)$/.test(page.total))
    throw new Error('Invalid account total');
  const total = Number(page.total);
  if (!Number.isSafeInteger(total))
    throw new Error('Account total exceeds the supported range');
  return Object.freeze({
    ...page,
    total,
    items: Object.freeze(page.items.map((item) => Object.freeze({ ...item }))),
  });
}

function freezeRoleI18n(roleI18n: RoleI18n): RoleI18n {
  return Object.freeze({
    ...roleI18n,
    i18n: Object.freeze(
      Object.fromEntries(
        Object.entries(roleI18n.i18n).map(([locale, fields]) => [
          locale,
          Object.freeze({ ...fields }),
        ]),
      ),
    ),
  });
}

function asAdminError(error: unknown): AdminError {
  return error instanceof AdminError
    ? error
    : new AdminError('unknown', 'Request failed');
}
