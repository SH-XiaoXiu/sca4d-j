import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type {
  PasskeyOptions,
  PasskeyProof,
  SmsCodeChallenge,
  Verification,
} from './types';

import { ObservableController } from './controller';
import { AdminError } from './errors';
import { VERIFICATION_HEADER } from './http-contract';

/** 动作认证当前公开进度与失败结果。 */
export interface ActionVerificationState {
  readonly error: AdminError | null;
  readonly phase: ResourcePhase;
  readonly verification: null | Verification;
}

/** 创建动作认证并按调用方选择的方法逐个提交认证因素。 */
export class ActionVerificationController extends ObservableController<ActionVerificationState> {
  constructor(private readonly api: AdminApiClient) {
    super({ error: null, phase: 'idle', verification: null });
  }

  /** 取消页面当前持有的 verification。 */
  clear(): void {
    this.publish({ error: null, phase: 'idle', verification: null });
  }

  /** 创建当前 verification 使用的 Passkey assertion options。 */
  async createPasskeyOptions(): Promise<PasskeyOptions> {
    const result = await this.api.post<PasskeyOptions>(
      '/passkey/assertions/options',
    );
    return result.data;
  }

  /** 为指定动作创建 verification；近期认证满足要求时直接返回满足状态。 */
  async start(actionCode: string): Promise<Verification> {
    this.publish({ error: null, phase: 'loading', verification: null });
    try {
      const result = await this.api.post<Verification>('/verifications', {
        actionCode,
      });
      return this.accept(result.data);
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 向当前 verification 提交 Passkey assertion。 */
  submitPasskey(proof: PasskeyProof): Promise<Verification> {
    return this.submitProof('passkey', proof);
  }

  /** 向当前 verification 提交密码。 */
  submitPassword(password: string): Promise<Verification> {
    return this.submitProof('password', { password });
  }

  /** 向当前 verification 提交 TOTP 验证码。 */
  submitTotp(code: string): Promise<Verification> {
    return this.submitProof('totp', { code });
  }

  /** 为当前 verification 签发 SMS Code Challenge。 */
  async requestSmsCode(): Promise<SmsCodeChallenge> {
    const current = this.requirePending();
    const result = await this.api.post<SmsCodeChallenge>(
      `/verifications/${encodeURIComponent(current.verificationId)}/sms-code/challenges`,
    );
    return Object.freeze({ ...result.data });
  }

  /** 向当前 verification 提交 SMS Code。 */
  submitSmsCode(challengeId: string, code: string): Promise<Verification> {
    return this.submitProof('sms-code', { challengeId, code });
  }

  private accept(verification: Verification): Verification {
    const accepted = freezeVerification(verification);
    this.publish({ error: null, phase: 'ready', verification: accepted });
    return accepted;
  }

  private fail(error: unknown): void {
    const value =
      error instanceof AdminError
        ? error
        : new AdminError('unknown', 'Action verification failed');
    this.publish({
      ...this.state,
      error: value,
      phase: value.kind === 'versionConflict' ? 'conflict' : 'error',
    });
  }

  private async submitProof(
    method: string,
    proof: unknown,
  ): Promise<Verification> {
    const current = this.requirePending();
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const result = await this.api.post<Verification>(
        `/verifications/${encodeURIComponent(current.verificationId)}/${method}/proofs`,
        proof,
      );
      return this.accept(result.data);
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  private requirePending(): Verification {
    const current = this.state.verification;
    if (!current || current.status === 'SATISFIED') {
      throw new Error('当前没有等待认证因素的 verification');
    }
    return current;
  }
}

/** 返回受保护写入使用的 verification 请求头。 */
export function verificationHeaders(
  verification: Verification,
): Readonly<Record<string, string>> {
  if (verification.status !== 'SATISFIED') {
    throw new Error('动作认证尚未满足要求');
  }
  return Object.freeze({
    [VERIFICATION_HEADER]: verification.verificationId,
  });
}

function freezeVerification(verification: Verification): Verification {
  return Object.freeze({
    ...verification,
    availableMethods: Object.freeze([...verification.availableMethods]),
  });
}
