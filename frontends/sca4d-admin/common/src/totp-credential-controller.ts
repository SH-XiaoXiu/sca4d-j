import type { AdminApiClient } from './api-client';
import type { ResourcePhase } from './controller';
import type { TotpCredential, TotpEnrollment, Verification } from './types';

import { verificationHeaders } from './action-verification-controller';
import { ObservableController } from './controller';
import { AdminError } from './errors';

/** 当前账号 TOTP Credential 和短期 enrollment 的状态。 */
export interface TotpCredentialState {
  readonly credential: null | TotpCredential;
  readonly enrollment: null | TotpEnrollment;
  readonly error: AdminError | null;
  readonly phase: ResourcePhase;
  readonly qrCode: ArrayBuffer | null;
}

/** 管理当前账号的 TOTP Credential、enrollment 和二维码。 */
export class TotpCredentialController extends ObservableController<TotpCredentialState> {
  private activationVerification: null | Verification = null;

  constructor(
    private readonly api: AdminApiClient,
    private readonly onSessionInvalidated: () => void,
  ) {
    super({
      credential: null,
      enrollment: null,
      error: null,
      phase: 'idle',
      qrCode: null,
    });
  }

  /** 使用 enrollment 的首个验证码激活首次配置或替换后的 TOTP。 */
  async activate(code: string): Promise<void> {
    const enrollment = this.state.enrollment;
    if (!enrollment) throw new Error('当前没有待激活的 TOTP enrollment');
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      if (this.state.credential) {
        const verification = this.activationVerification;
        if (!verification) throw new Error('替换 TOTP 的动作认证不存在');
        await this.api.put<TotpCredential>(
          '/me/authentication/totp/credential',
          { code, enrollmentId: enrollment.enrollmentId },
          verificationHeaders(verification),
        );
        this.clearEnrollment();
        this.onSessionInvalidated();
        return;
      }
      const result = await this.api.post<TotpCredential>(
        '/me/authentication/totp/credential',
        { code, enrollmentId: enrollment.enrollmentId },
      );
      this.activationVerification = null;
      this.publish({
        credential: Object.freeze(result.data),
        enrollment: null,
        error: null,
        phase: 'ready',
        qrCode: null,
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 取消当前尚未激活的 enrollment。 */
  async cancelEnrollment(): Promise<void> {
    const enrollment = this.state.enrollment;
    if (!enrollment) return;
    try {
      await this.api.delete(
        `/me/authentication/totp/enrollments/${encodeURIComponent(enrollment.enrollmentId)}`,
      );
      this.clearEnrollment();
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  /** 使用已经满足的动作认证创建首次配置或替换使用的 enrollment。 */
  async createEnrollment(verification: Verification): Promise<void> {
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const enrollment = await this.api.post<TotpEnrollment>(
        '/me/authentication/totp/enrollments',
        undefined,
        verificationHeaders(verification),
      );
      const qrCode = await this.api.getBinary(
        `/me/authentication/totp/enrollments/${encodeURIComponent(enrollment.data.enrollmentId)}/qr-code`,
        'image/png',
      );
      this.activationVerification = verification;
      this.publish({
        ...this.state,
        enrollment: Object.freeze(enrollment.data),
        error: null,
        phase: 'ready',
        qrCode: qrCode.data,
      });
    } catch (error) {
      this.activationVerification = null;
      this.fail(error);
      throw error;
    }
  }

  /** 读取当前账号的 TOTP Credential；未配置时返回空凭据。 */
  async load(): Promise<void> {
    const read = this.beginRead('load');
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      const result = await this.api.get<TotpCredential>(
        '/me/authentication/totp/credential',
      );
      if (!this.isCurrentRead('load', read)) return;
      this.publish({
        credential: Object.freeze(result.data),
        enrollment: null,
        error: null,
        phase: 'ready',
        qrCode: null,
      });
    } catch (error) {
      if (!this.isCurrentRead('load', read)) return;
      if (error instanceof AdminError && error.kind === 'notFound') {
        if (!this.isCurrentRead('load', read)) return;
        this.publish({
          credential: null,
          enrollment: null,
          error: null,
          phase: 'empty',
          qrCode: null,
        });
        return;
      }
      this.fail(error);
      throw error;
    }
  }

  /** 使用已经满足的动作认证删除当前 TOTP Credential 并使 Session 失效。 */
  async remove(verification: Verification): Promise<void> {
    if (!this.state.credential) throw new Error('当前账号没有 TOTP Credential');
    this.publish({ ...this.state, error: null, phase: 'loading' });
    try {
      await this.api.delete(
        '/me/authentication/totp/credential',
        verificationHeaders(verification),
        'session',
      );
      this.onSessionInvalidated();
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  private clearEnrollment(): void {
    this.activationVerification = null;
    this.publish({
      ...this.state,
      enrollment: null,
      error: null,
      phase: this.state.credential ? 'ready' : 'empty',
      qrCode: null,
    });
  }

  private fail(error: unknown): void {
    const value =
      error instanceof AdminError
        ? error
        : new AdminError('unknown', 'TOTP request failed');
    this.publish({ ...this.state, error: value, phase: 'error' });
  }
}
