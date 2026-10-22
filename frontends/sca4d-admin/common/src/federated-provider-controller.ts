import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type {
  CreateFederatedProviderInput,
  FederatedProvider,
  FederatedProviderSettings,
  FederatedProviderSettingsInput,
  LocaleCatalog,
  ReplaceFederatedProviderInput,
} from './types';

import { ObservableController } from './controller';
import { SavedResourceRefreshError } from './errors';
import { SupersededReadError } from './errors';
import { AdminError } from './errors';
import { freezeLocaleCatalog } from './localized-fields';

/** Provider 管理页面使用的不可变状态。 */
export interface FederatedProvidersState {
  readonly detail: FederatedProvider | null;
  readonly error: AdminError | null;
  readonly localeCatalog: LocaleCatalog | null;
  readonly phase: ResourcePhase;
  readonly providers: readonly FederatedProvider[];
  readonly settings: FederatedProviderSettings | null;
}

/** 读取和修改系统管理员维护的 OIDC/OAuth2 Provider。 */
export class FederatedProviderController extends ObservableController<FederatedProvidersState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      detail: null,
      error: null,
      localeCatalog: null,
      phase: 'idle',
      providers: Object.freeze([]),
      settings: null,
    });
  }

  /** 创建默认停用的 Provider，并刷新列表。 */
  async create(
    input: CreateFederatedProviderInput,
  ): Promise<FederatedProvider> {
    try {
      const result = await this.api.post<FederatedProvider>(
        '/system/authentication/federated-providers',
        input,
      );
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
      return freezeProvider(result.data);
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 按 Provider 当前版本删除停用且没有绑定身份的 Provider。 */
  async delete(provider: FederatedProvider): Promise<void> {
    try {
      await this.api.delete(providerPath(provider.code), {
        'If-Match': providerTag(provider),
      });
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 读取全部未删除 Provider；停用 Provider 也会返回。 */
  async load(): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const [result, locales] = await Promise.all([
        this.api.get<readonly FederatedProvider[]>(
          '/system/authentication/federated-providers',
        ),
        this.api.get<LocaleCatalog>('/system/locales'),
      ]);
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        ...this.state,
        detail: null,
        error: null,
        localeCatalog: freezeLocaleCatalog(locales.data),
        phase: result.data.length > 0 ? 'ready' : 'empty',
        providers: Object.freeze(
          result.data.map((item) => freezeProvider(item)),
        ),
        settings: null,
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 读取指定 Provider 的脱敏协议配置。 */
  async loadSettings(code: string): Promise<FederatedProviderSettings> {
    try {
      const result = await this.api.get<FederatedProviderSettings>(
        `${providerPath(code)}/settings`,
      );
      const settings = freezeSettings(result.data);
      this.publish({ ...this.state, error: null, settings });
      return settings;
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 按 Provider 当前版本替换登录页展示设置。 */
  async replace(
    provider: FederatedProvider,
    input: ReplaceFederatedProviderInput,
  ): Promise<FederatedProvider> {
    try {
      const result = await this.api.put<FederatedProvider>(
        providerPath(provider.code),
        input,
        { 'If-Match': providerTag(provider) },
      );
      const updated = freezeProvider(result.data);
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
      return updated;
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 按 Provider 当前版本替换协议配置；空 Secret 保留原值。 */
  async replaceSettings(
    provider: FederatedProvider,
    input: FederatedProviderSettingsInput,
  ): Promise<void> {
    try {
      await this.api.put<unknown>(
        `${providerPath(provider.code)}/settings`,
        input,
        { 'If-Match': providerTag(provider) },
      );
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  /** 读取一个 Provider 的最新展示信息。 */
  async require(code: string): Promise<FederatedProvider> {
    const read = this.beginRead('detail');
    try {
      const result = await this.api.get<FederatedProvider>(providerPath(code));
      const detail = freezeProvider(result.data);
      if (!this.isCurrentRead('detail', read)) throw new SupersededReadError();
      this.publish({ ...this.state, detail, error: null });
      if (!this.isCurrentRead('detail', read)) throw new SupersededReadError();
      return detail;
    } catch (error) {
      if (!this.isCurrentRead('detail', read)) throw new SupersededReadError();
      this.failMutation(error);
      throw error;
    }
  }

  /** 按 Provider 当前版本修改启用状态。 */
  async setStatus(
    provider: FederatedProvider,
    status: 'ACTIVE' | 'DISABLED',
  ): Promise<void> {
    try {
      await this.api.patch<FederatedProvider>(
        `${providerPath(provider.code)}/status`,
        { status },
        { 'If-Match': providerTag(provider) },
      );
      try {
        await this.load();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failMutation(error);
      throw error;
    }
  }

  private fail(error: unknown): void {
    const value = asAdminError(error);
    this.publish({ ...this.state, error: value, phase: failurePhase(value) });
  }

  private failMutation(error: unknown): void {
    const value = asAdminError(error);
    const phase =
      this.state.phase === 'ready' || this.state.phase === 'empty'
        ? this.state.phase
        : failurePhase(value);
    this.publish({ ...this.state, error: value, phase });
  }
}

function freezeProvider(provider: FederatedProvider): FederatedProvider {
  return Object.freeze({ ...provider });
}

function freezeSettings(
  settings: FederatedProviderSettings,
): FederatedProviderSettings {
  return Object.freeze({
    ...settings,
    authorizationParameters: Object.freeze({
      ...settings.authorizationParameters,
    }),
    profileMapping: Object.freeze({ ...settings.profileMapping }),
    scopes: Object.freeze([...settings.scopes]),
  });
}

function providerPath(code: string): string {
  return `/system/authentication/federated-providers/${encodeURIComponent(code)}`;
}

function providerTag(provider: FederatedProvider): string {
  return `"${provider.version}"`;
}

function failurePhase(error: AdminError): ResourcePhase {
  if (error.kind === 'permission') return 'forbidden';
  if (error.kind === 'versionConflict') return 'conflict';
  return 'error';
}

function asAdminError(error: unknown): AdminError {
  return error instanceof AdminError
    ? error
    : new AdminError('unknown', 'Request failed');
}
