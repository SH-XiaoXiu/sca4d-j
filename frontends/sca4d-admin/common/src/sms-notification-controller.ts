import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type {
  Int64String,
  SmsProvider,
  SmsProviderBinding,
  SmsProviderBindingInput,
  SmsProviderInput,
} from './types';

import { ObservableController } from './controller';
import { SavedResourceRefreshError } from './errors';
import { SupersededReadError } from './errors';
import { AdminError } from './errors';

/** SMS Provider 与模板绑定页面使用的不可变状态。 */
export interface SmsNotificationsState {
  readonly bindingDetail: null | SmsProviderBinding;
  readonly bindingEtag: null | string;
  readonly bindings: readonly SmsProviderBinding[];
  readonly error: AdminError | null;
  readonly phase: ResourcePhase;
  readonly providerDetail: null | SmsProvider;
  readonly providerEtag: null | string;
  readonly providers: readonly SmsProvider[];
  readonly selectedProviderId: Int64String | null;
}

/** 管理 SMS Provider、供应商模板绑定和各自 ETag。 */
export class SmsNotificationsController extends ObservableController<SmsNotificationsState> {
  constructor(private readonly api: AdminApiClient) {
    super({
      bindingDetail: null,
      bindingEtag: null,
      bindings: Object.freeze([]),
      error: null,
      phase: 'idle',
      providerDetail: null,
      providerEtag: null,
      providers: Object.freeze([]),
      selectedProviderId: null,
    });
  }

  /** 清除绑定详情和 ETag。 */
  clearBinding(): void {
    this.cancelRead('requireBinding');
    this.publish({
      ...this.state,
      bindingDetail: null,
      bindingEtag: null,
    });
  }

  /** 清除 Provider 详情和 ETag。 */
  clearProvider(): void {
    this.cancelRead('requireProvider');
    this.publish({
      ...this.state,
      providerDetail: null,
      providerEtag: null,
    });
  }

  /** 创建默认停用绑定并刷新当前 Provider 的绑定列表。 */
  async createBinding(
    providerId: Int64String,
    input: SmsProviderBindingInput,
  ): Promise<SmsProviderBinding> {
    try {
      const result = await this.api.post<SmsProviderBinding>(
        bindingsPath(providerId),
        input,
      );
      try {
        await this.loadBindings(providerId);
      } catch {
        throw new SavedResourceRefreshError();
      }
      return freezeBinding(result.data);
    } catch (error) {
      this.failed(error, false);
      throw error;
    }
  }

  /** 创建默认停用 Provider 并刷新列表。 */
  async createProvider(input: SmsProviderInput): Promise<SmsProvider> {
    try {
      const result = await this.api.post<SmsProvider>(
        '/system/sms/providers',
        input,
      );
      try {
        await this.loadProviders();
      } catch {
        throw new SavedResourceRefreshError();
      }
      return freezeProvider(result.data);
    } catch (error) {
      this.failed(error, false);
      throw error;
    }
  }

  /** 按最新 ETag 删除停用绑定。 */
  async deleteBinding(
    providerId: Int64String,
    bindingId: Int64String,
  ): Promise<void> {
    try {
      const current = await this.api.get<SmsProviderBinding>(
        bindingPath(providerId, bindingId),
      );
      if (!current.etag) throw new Error('SMS binding response has no ETag');
      await this.api.delete(bindingPath(providerId, bindingId), {
        'If-Match': current.etag,
      });
      this.clearBinding();
      try {
        await this.loadBindings(providerId);
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failed(error, false);
      throw error;
    }
  }

  /** 按最新 ETag 删除停用且没有绑定的 Provider。 */
  async deleteProvider(providerId: Int64String): Promise<void> {
    try {
      const current = await this.api.get<SmsProvider>(providerPath(providerId));
      if (!current.etag) throw new Error('SMS provider response has no ETag');
      await this.api.delete(providerPath(providerId), {
        'If-Match': current.etag,
      });
      this.clearProvider();
      try {
        await this.loadProviders();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failed(error, false);
      throw error;
    }
  }

  /** 读取指定 Provider 的全部绑定。 */
  async loadBindings(providerId: Int64String): Promise<void> {
    const read = this.beginRead('loadBindings');
    try {
      const result = await this.api.get<readonly SmsProviderBinding[]>(
        bindingsPath(providerId),
      );
      if (!this.isCurrentRead('loadBindings', read)) return;
      this.publish({
        ...this.state,
        bindings: Object.freeze(result.data.map((item) => freezeBinding(item))),
        error: null,
        selectedProviderId: providerId,
      });
    } catch (error) {
      if (!this.isCurrentRead('loadBindings', read)) return;
      this.failed(error, false);
      throw error;
    }
  }

  /** 读取全部 Provider；空列表仍允许创建。 */
  async loadProviders(): Promise<void> {
    const read = this.beginRead('loadProviders');
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const result = await this.api.get<readonly SmsProvider[]>(
        '/system/sms/providers',
      );
      if (!this.isCurrentRead('loadProviders', read)) return;
      this.publish({
        ...this.state,
        error: null,
        phase: 'ready',
        providers: Object.freeze(
          result.data.map((item) => freezeProvider(item)),
        ),
      });
    } catch (error) {
      if (!this.isCurrentRead('loadProviders', read)) return;
      this.failed(error, true);
      throw error;
    }
  }

  /** 按已加载 ETag 完整替换停用绑定。 */
  async replaceBinding(
    input: SmsProviderBindingInput,
  ): Promise<SmsProviderBinding> {
    const detail = this.state.bindingDetail;
    const etag = this.state.bindingEtag;
    if (!detail || !etag) throw new Error('No SMS binding detail is loaded');
    try {
      const result = await this.api.put<SmsProviderBinding>(
        bindingPath(detail.providerId, detail.bindingId),
        input,
        { 'If-Match': etag },
      );
      const updated = freezeBinding(result.data);
      this.publish({
        ...this.state,
        bindingDetail: updated,
        bindingEtag: result.etag,
        error: null,
      });
      try {
        await this.loadBindings(detail.providerId);
      } catch {
        throw new SavedResourceRefreshError();
      }
      return updated;
    } catch (error) {
      this.failed(error, false);
      throw error;
    }
  }

  /** 按已加载 ETag 完整替换停用 Provider。 */
  async replaceProvider(input: SmsProviderInput): Promise<SmsProvider> {
    const detail = this.state.providerDetail;
    const etag = this.state.providerEtag;
    if (!detail || !etag) throw new Error('No SMS provider detail is loaded');
    try {
      const result = await this.api.put<SmsProvider>(
        providerPath(detail.providerId),
        input,
        { 'If-Match': etag },
      );
      const updated = freezeProvider(result.data);
      this.publish({
        ...this.state,
        error: null,
        providerDetail: updated,
        providerEtag: result.etag,
      });
      try {
        await this.loadProviders();
      } catch {
        throw new SavedResourceRefreshError();
      }
      return updated;
    } catch (error) {
      this.failed(error, false);
      throw error;
    }
  }

  /** 读取绑定详情及后续写入使用的 ETag。 */
  async requireBinding(
    providerId: Int64String,
    bindingId: Int64String,
  ): Promise<SmsProviderBinding> {
    const read = this.beginRead('requireBinding');
    try {
      const result = await this.api.get<SmsProviderBinding>(
        bindingPath(providerId, bindingId),
      );
      if (!result.etag) throw new Error('SMS binding response has no ETag');
      const binding = freezeBinding(result.data);
      if (!this.isCurrentRead('requireBinding', read))
        throw new SupersededReadError();
      this.publish({
        ...this.state,
        bindingDetail: binding,
        bindingEtag: result.etag,
        error: null,
      });
      if (!this.isCurrentRead('requireBinding', read))
        throw new SupersededReadError();
      return binding;
    } catch (error) {
      if (!this.isCurrentRead('requireBinding', read))
        throw new SupersededReadError();
      this.failed(error, false);
      throw error;
    }
  }

  /** 读取 Provider 详情及后续写入使用的 ETag。 */
  async requireProvider(providerId: Int64String): Promise<SmsProvider> {
    const read = this.beginRead('requireProvider');
    try {
      const result = await this.api.get<SmsProvider>(providerPath(providerId));
      if (!result.etag) throw new Error('SMS provider response has no ETag');
      const provider = freezeProvider(result.data);
      if (!this.isCurrentRead('requireProvider', read))
        throw new SupersededReadError();
      this.publish({
        ...this.state,
        error: null,
        providerDetail: provider,
        providerEtag: result.etag,
      });
      if (!this.isCurrentRead('requireProvider', read))
        throw new SupersededReadError();
      return provider;
    } catch (error) {
      if (!this.isCurrentRead('requireProvider', read))
        throw new SupersededReadError();
      this.failed(error, false);
      throw error;
    }
  }

  /** 读取最新 ETag 后修改绑定状态。 */
  async setBindingEnabled(
    providerId: Int64String,
    bindingId: Int64String,
    enabled: boolean,
  ): Promise<void> {
    try {
      const current = await this.api.get<SmsProviderBinding>(
        bindingPath(providerId, bindingId),
      );
      if (!current.etag) throw new Error('SMS binding response has no ETag');
      await this.api.patch(
        `${bindingPath(providerId, bindingId)}/status`,
        { enabled },
        { 'If-Match': current.etag },
      );
      try {
        await this.loadBindings(providerId);
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failed(error, false);
      throw error;
    }
  }

  /** 读取最新 ETag 后修改 Provider 状态。 */
  async setProviderEnabled(
    providerId: Int64String,
    enabled: boolean,
  ): Promise<void> {
    try {
      const current = await this.api.get<SmsProvider>(providerPath(providerId));
      if (!current.etag) throw new Error('SMS provider response has no ETag');
      await this.api.patch(
        `${providerPath(providerId)}/status`,
        { enabled },
        { 'If-Match': current.etag },
      );
      try {
        await this.loadProviders();
      } catch {
        throw new SavedResourceRefreshError();
      }
    } catch (error) {
      this.failed(error, false);
      throw error;
    }
  }

  /** 映射错误并保留已读取资源。 */
  private failed(error: unknown, loading: boolean): void {
    const value =
      error instanceof AdminError
        ? error
        : new AdminError('unknown', 'SMS notification request failed');
    let phase = this.state.phase;
    if (value.kind === 'permission') phase = 'forbidden';
    else if (value.kind === 'versionConflict') phase = 'conflict';
    else if (loading) phase = 'error';
    this.publish({ ...this.state, error: value, phase });
  }
}

function providerPath(providerId: Int64String): string {
  return `/system/sms/providers/${encodeURIComponent(providerId)}`;
}

function bindingsPath(providerId: Int64String): string {
  return `${providerPath(providerId)}/bindings`;
}

function bindingPath(providerId: Int64String, bindingId: Int64String): string {
  return `${bindingsPath(providerId)}/${encodeURIComponent(bindingId)}`;
}

function freezeProvider(provider: SmsProvider): SmsProvider {
  return Object.freeze({ ...provider });
}

function freezeBinding(binding: SmsProviderBinding): SmsProviderBinding {
  return Object.freeze({
    ...binding,
    parameterMapping: Object.freeze({ ...binding.parameterMapping }),
  });
}
