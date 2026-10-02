import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type {
  Int64String,
  PasskeyCredential,
  PasskeyOptions,
  PasskeyProof,
  Verification,
} from './types';

import { verificationHeaders } from './action-verification-controller';
import { ObservableController } from './controller';
import { AdminError } from './errors';

/** 当前账号 Passkey 列表的读取和写入状态。 */
export interface PasskeyState {
  readonly credentials: readonly PasskeyCredential[];
  readonly error: AdminError | null;
  readonly phase: ResourcePhase;
}

/** 管理当前账号保存的 Passkey Credential 和 registration ceremony。 */
export class PasskeyController extends ObservableController<PasskeyState> {
  constructor(private readonly api: AdminApiClient) {
    super({ credentials: Object.freeze([]), error: null, phase: 'idle' });
  }

  /** 为当前账号创建 Passkey registration options。 */
  async createRegistrationOptions(
    displayName: string,
  ): Promise<PasskeyOptions> {
    const result = await this.api.post<PasskeyOptions>(
      '/me/passkeys/registrations/options',
      {
        displayName,
      },
    );
    return result.data;
  }

  /** 读取当前账号全部 Passkey。 */
  async load(): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const result =
        await this.api.get<readonly PasskeyCredential[]>('/me/passkeys');
      const credentials = Object.freeze(
        result.data.map((credential) => freezeCredential(credential)),
      );
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        credentials,
        error: null,
        phase: credentials.length === 0 ? 'empty' : 'ready',
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      this.fail(error);
      throw error;
    }
  }

  /** 使用已经满足的动作认证保存浏览器创建的 Passkey。 */
  async register(
    name: string,
    proof: PasskeyProof,
    verification: Verification,
  ): Promise<void> {
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const result = await this.api.post<PasskeyCredential>(
        '/me/passkeys',
        { ...proof, name },
        verificationHeaders(verification),
      );
      this.publish({
        credentials: Object.freeze([
          ...this.state.credentials,
          freezeCredential(result.data),
        ]),
        error: null,
        phase: 'ready',
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 使用已经满足的动作认证删除指定 Passkey。 */
  async remove(
    credentialId: Int64String,
    verification: Verification,
  ): Promise<void> {
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      await this.api.delete(
        `/me/passkeys/${encodeURIComponent(credentialId)}`,
        verificationHeaders(verification),
      );
      const credentials = Object.freeze(
        this.state.credentials.filter(
          (credential) => credential.id !== credentialId,
        ),
      );
      this.publish({
        credentials,
        error: null,
        phase: credentials.length === 0 ? 'empty' : 'ready',
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
        : new AdminError('unknown', 'Passkey request failed');
    this.publish({ ...this.state, error: value, phase: 'error' });
  }
}

function freezeCredential(credential: PasskeyCredential): PasskeyCredential {
  return Object.freeze({
    ...credential,
    transports: Object.freeze([...credential.transports]),
  });
}
