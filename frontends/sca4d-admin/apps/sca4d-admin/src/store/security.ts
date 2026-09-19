import type {
  ActionVerificationState,
  AuthenticationSettings,
  AuthenticationSettingsState,
  Int64String,
  PasskeyState,
  PasswordState,
  SmsCodeChallenge,
  TotpCredentialState,
  Verification,
} from '@sca4d/admin';

import { computed, ref, shallowRef } from 'vue';

import { defineStore } from 'pinia';

import { browserPasskeyClient } from '#/auth/passkey';
import { businessApplication } from '#/platform/business-application';

/** 安全设置页可以执行的受保护动作。 */
export type SecurityAction =
  | 'authenticationSettings.replace'
  | 'passkey.register'
  | 'passkey.remove'
  | 'password.replace'
  | 'phone.verify'
  | 'totp.configure'
  | 'totp.remove'
  | 'totp.replace';

type ProtectedAction = (verification: Verification) => Promise<void>;

/** 管理认证资源状态，并把受保护写入交给统一动作认证推进。 */
export const useSecurityStore = defineStore('security', () => {
  const authenticationSettings = shallowRef<
    Readonly<AuthenticationSettingsState>
  >(businessApplication.authenticationSettings.state);
  const passkeys = shallowRef<Readonly<PasskeyState>>(
    businessApplication.passkeys.state,
  );
  const password = shallowRef<Readonly<PasswordState>>(
    businessApplication.password.state,
  );
  const totp = shallowRef<Readonly<TotpCredentialState>>(
    businessApplication.totp.state,
  );
  const verification = shallowRef<Readonly<ActionVerificationState>>(
    businessApplication.verification.state,
  );
  const pendingAction = ref<null | SecurityAction>(null);
  const smsCodeChallenge = ref<SmsCodeChallenge | null>(null);
  const completedAction = ref<null | SecurityAction>(null);
  const enrollmentAction = ref<Extract<
    SecurityAction,
    'totp.configure' | 'totp.replace'
  > | null>(null);
  const verificationOpen = computed(
    () =>
      pendingAction.value !== null &&
      verification.value.verification?.status === 'FACTOR_REQUIRED',
  );
  const passkeySupported = computed(() => browserPasskeyClient.isSupported());

  let protectedAction: null | ProtectedAction = null;

  businessApplication.authenticationSettings.subscribe(
    (value) => (authenticationSettings.value = value),
  );
  businessApplication.passkeys.subscribe((value) => (passkeys.value = value));
  businessApplication.password.subscribe((value) => (password.value = value));
  businessApplication.totp.subscribe((value) => (totp.value = value));
  businessApplication.verification.subscribe(
    (value) => (verification.value = value),
  );

  /** 并行读取四类认证资源；单个资源失败不覆盖其他资源状态。 */
  async function load(): Promise<void> {
    await Promise.allSettled([
      loadAuthenticationSettings(),
      loadPasskeys(),
      loadPassword(),
      loadTotp(),
    ]);
  }

  /** 读取当前账号认证设置。 */
  async function loadAuthenticationSettings(): Promise<void> {
    await businessApplication.authenticationSettings.load();
  }

  /** 读取当前账号 Passkey。 */
  async function loadPasskeys(): Promise<void> {
    await businessApplication.passkeys.load();
  }

  /** 读取当前账号密码凭据状态。 */
  async function loadPassword(): Promise<void> {
    await businessApplication.password.load();
  }

  /** 读取当前账号 TOTP Credential。 */
  async function loadTotp(): Promise<void> {
    await businessApplication.totp.load();
  }

  /** 开始首次配置或替换 TOTP 所需的动作认证。 */
  async function beginTotpEnrollment(): Promise<null | SecurityAction> {
    const replacing = Boolean(totp.value.credential);
    return beginProtectedAction(
      replacing ? 'totp.replace' : 'totp.configure',
      'authentication.totp.enroll',
      (satisfied) => businessApplication.totp.createEnrollment(satisfied),
    );
  }

  /** 使用首个验证码激活当前 TOTP enrollment。 */
  async function activateTotp(code: string): Promise<void> {
    const action = enrollmentAction.value;
    await businessApplication.totp.activate(code);
    enrollmentAction.value = null;
    if (action) completedAction.value = action;
  }

  /** 取消当前 TOTP enrollment。 */
  async function cancelTotpEnrollment(): Promise<void> {
    await businessApplication.totp.cancelEnrollment();
    enrollmentAction.value = null;
  }

  /** 开始删除当前 TOTP Credential 所需的动作认证。 */
  async function removeTotp(): Promise<null | SecurityAction> {
    return beginProtectedAction(
      'totp.remove',
      'authentication.totp.replace',
      (satisfied) => businessApplication.totp.remove(satisfied),
    );
  }

  /** 开始注册 Passkey 所需的动作认证和浏览器 ceremony。 */
  async function registerPasskey(
    name: string,
    displayName: string,
  ): Promise<null | SecurityAction> {
    return beginProtectedAction(
      'passkey.register',
      'authentication.passkey.register',
      async (satisfied) => {
        const options =
          await businessApplication.passkeys.createRegistrationOptions(
            displayName,
          );
        const proof = await browserPasskeyClient.createCredential(options);
        await businessApplication.passkeys.register(name, proof, satisfied);
      },
    );
  }

  /** 开始删除指定 Passkey 所需的动作认证。 */
  async function removePasskey(
    credentialId: Int64String,
  ): Promise<null | SecurityAction> {
    return beginProtectedAction(
      'passkey.remove',
      'authentication.passkey.delete',
      (satisfied) =>
        businessApplication.passkeys.remove(credentialId, satisfied),
    );
  }

  /** 开始完整替换账号认证设置所需的动作认证。 */
  async function replaceAuthenticationSettings(
    replacement: AuthenticationSettings,
  ): Promise<null | SecurityAction> {
    return beginProtectedAction(
      'authenticationSettings.replace',
      'authentication.settings.replace',
      (satisfied) =>
        businessApplication.authenticationSettings.replace(
          replacement,
          satisfied,
        ),
    );
  }

  /** 开始替换密码所需的动作认证。 */
  async function replacePassword(
    nextPassword: string,
  ): Promise<null | SecurityAction> {
    return beginProtectedAction(
      'password.replace',
      'authentication.password.replace',
      (satisfied) =>
        businessApplication.password.replace(nextPassword, satisfied),
    );
  }

  /** 开始验证并绑定当前 Profile 手机号所需的 SMS 动作认证。 */
  async function verifyPhone(): Promise<null | SecurityAction> {
    return beginProtectedAction(
      'phone.verify',
      'authentication.phone.verify',
      (satisfied) => businessApplication.profile.verifyPhone(satisfied),
    );
  }

  /** 向当前动作认证提交密码；满足要求时继续原动作。 */
  async function submitPassword(
    passwordValue: string,
  ): Promise<null | SecurityAction> {
    return acceptVerification(
      await businessApplication.verification.submitPassword(passwordValue),
    );
  }

  /** 向当前动作认证提交 TOTP；满足要求时继续原动作。 */
  async function submitTotp(code: string): Promise<null | SecurityAction> {
    return acceptVerification(
      await businessApplication.verification.submitTotp(code),
    );
  }

  /** 向当前动作认证提交 Passkey；满足要求时继续原动作。 */
  async function submitPasskey(): Promise<null | SecurityAction> {
    const options =
      await businessApplication.verification.createPasskeyOptions();
    const proof = await browserPasskeyClient.getAssertion(options);
    return acceptVerification(
      await businessApplication.verification.submitPasskey(proof),
    );
  }

  /** 为当前动作认证签发 SMS Code Challenge。 */
  async function requestSmsCode(): Promise<SmsCodeChallenge> {
    const challenge = await businessApplication.verification.requestSmsCode();
    smsCodeChallenge.value = challenge;
    return challenge;
  }

  /** 向当前动作认证提交 SMS Code；满足要求时继续原动作。 */
  async function submitSmsCode(code: string): Promise<null | SecurityAction> {
    const challenge = smsCodeChallenge.value;
    if (!challenge) throw new Error('请先发送 SMS 验证码');
    const result = await acceptVerification(
      await businessApplication.verification.submitSmsCode(
        challenge.challengeId,
        code,
      ),
    );
    smsCodeChallenge.value = null;
    return result;
  }

  /** 取消当前尚未完成的动作认证。 */
  function cancelVerification(): void {
    pendingAction.value = null;
    protectedAction = null;
    businessApplication.verification.clear();
    smsCodeChallenge.value = null;
  }

  /** 清除页面已经展示过的安全操作完成结果。 */
  function consumeCompletedAction(): void {
    completedAction.value = null;
  }

  async function beginProtectedAction(
    action: SecurityAction,
    actionCode: string,
    completion: ProtectedAction,
  ): Promise<null | SecurityAction> {
    if (pendingAction.value) throw new Error('已有安全操作正在等待认证');
    pendingAction.value = action;
    protectedAction = completion;
    try {
      return acceptVerification(
        await businessApplication.verification.start(actionCode),
      );
    } catch (error) {
      cancelVerification();
      throw error;
    }
  }

  async function acceptVerification(
    current: Verification,
  ): Promise<null | SecurityAction> {
    if (current.status !== 'SATISFIED') return null;
    const action = pendingAction.value;
    const completion = protectedAction;
    if (!action || !completion) throw new Error('动作认证没有对应的安全操作');
    try {
      await completion(current);
      if (action === 'totp.configure' || action === 'totp.replace') {
        enrollmentAction.value = action;
      } else {
        completedAction.value = action;
      }
      cancelVerification();
      return action;
    } catch (error) {
      cancelVerification();
      throw error;
    }
  }

  return {
    activateTotp,
    authenticationSettings,
    beginTotpEnrollment,
    cancelTotpEnrollment,
    cancelVerification,
    completedAction,
    consumeCompletedAction,
    load,
    loadAuthenticationSettings,
    loadPasskeys,
    loadPassword,
    loadTotp,
    passkeySupported,
    passkeys,
    password,
    pendingAction,
    registerPasskey,
    removePasskey,
    removeTotp,
    replaceAuthenticationSettings,
    replacePassword,
    requestSmsCode,
    smsCodeChallenge,
    submitPasskey,
    submitPassword,
    submitSmsCode,
    submitTotp,
    totp,
    verification,
    verificationOpen,
    verifyPhone,
  };
});
