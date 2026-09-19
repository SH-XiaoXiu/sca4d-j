import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type { AuthenticationSettings, Verification } from './types';

import { verificationHeaders } from './action-verification-controller';
import { ObservableController } from './controller';
import { AdminError } from './errors';

/** 当前账号认证设置的读取和写入状态。 */
export interface AuthenticationSettingsState {
  readonly etag: null | string;
  readonly error: AdminError | null;
  readonly phase: ResourcePhase;
  readonly settings: AuthenticationSettings | null;
}

/** 读取并完整替换当前账号的认证因素数量和方法用途。 */
export class AuthenticationSettingsController extends ObservableController<AuthenticationSettingsState> {
  constructor(private readonly api: AdminApiClient) {
    super({ error: null, etag: null, phase: 'idle', settings: null });
  }

  /** 读取当前账号认证设置。 */
  async load(): Promise<void> {
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const result = await this.api.get<AuthenticationSettings>(
        '/me/authentication/settings',
      );
      if (!result.etag) {
        throw new Error('Authentication settings response has no ETag');
      }
      this.publish({
        error: null,
        etag: result.etag,
        phase: 'ready',
        settings: freezeSettings(result.data),
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 使用已经满足的动作认证完整替换当前账号认证设置。 */
  async replace(
    replacement: AuthenticationSettings,
    verification: Verification,
  ): Promise<void> {
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      if (!this.state.etag) {
        throw new Error('Authentication settings have not been loaded');
      }
      const result = await this.api.put<AuthenticationSettings>(
        '/me/authentication/settings',
        replacement,
        {
          ...verificationHeaders(verification),
          'If-Match': this.state.etag,
        },
      );
      if (!result.etag) {
        throw new Error('Authentication settings response has no ETag');
      }
      this.publish({
        error: null,
        etag: result.etag,
        phase: 'ready',
        settings: freezeSettings(result.data),
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  private fail(error: unknown): void {
    const value =
      error instanceof AdminError
        ? error
        : new AdminError('unknown', 'Authentication settings request failed');
    this.publish({
      ...this.state,
      error: value,
      phase: value.kind === 'versionConflict' ? 'conflict' : 'error',
    });
  }
}

function freezeSettings(
  settings: AuthenticationSettings,
): AuthenticationSettings {
  return Object.freeze({
    ...settings,
    methods: Object.freeze(
      settings.methods.map((method) => Object.freeze({ ...method })),
    ),
  });
}
