import type {
  CurrentSession,
  EmailCodeChallenge,
  FederatedProvider,
  LoginOutcome,
  SessionToken,
  SmsCodeChallenge,
} from '@sca4d/admin';

import type { Recordable, UserInfo } from '@vben/types';

import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { useAccessStore, useTabbarStore, useUserStore } from '@vben/stores';

import { defineStore } from 'pinia';

import {
  createEmailCodeChallengeApi,
  createEmailCodeLoginAttemptApi,
  createFederatedAuthorizationApi,
  createFederatedLoginAttemptApi,
  createLoginAttemptApi,
  createPasskeyAssertionOptionsApi,
  createPasskeyLoginAttemptApi,
  createPendingEmailCodeChallengeApi,
  createPendingSmsCodeChallengeApi,
  createSmsCodeChallengeApi,
  createSmsCodeLoginAttemptApi,
  getCurrentProfileApi,
  getCurrentSessionApi,
  getFederatedProvidersApi,
  getFileAccessUrlsApi,
  logoutSessionApi,
  refreshSessionTokensApi,
  submitLoginEmailCodeApi,
  submitLoginPasskeyApi,
  submitLoginPasswordApi,
  submitLoginSmsCodeApi,
  submitLoginTotpApi,
} from '#/api';
import { subscribeSessionEvents } from '#/api/core/session-events';
import { registerAuthenticationFailureHandler } from '#/auth/authentication-failure';
import { registerAuthenticationSessionRefreshHandler } from '#/auth/client-state-refresh';
import { browserPasskeyClient } from '#/auth/passkey';
import {
  currentSessionUser,
  millisecondsUntilTokenRefresh,
  selectAuthenticationToken,
} from '#/auth/session';
import { $t } from '#/locales';
import { businessApplication } from '#/platform/business-application';
import {
  activatePreferenceSync,
  deactivatePreferenceSync,
} from '#/preferences/preference-sync';
import { replaceAccess } from '#/router/access';
import { safeLoginRedirect } from '#/router/login-redirect';

/** 持久化后端一次性交付的完整 Session Token 集合。 */
export const useSessionTokenStore = defineStore('sca4d-session-tokens', {
  actions: {
    /** 清除本地保存的完整 Token 集合。 */
    clearTokens(): void {
      this.tokens = null;
    },
    /** 原子替换本地保存的完整 Token 集合。 */
    setTokens(tokens: SessionToken): void {
      this.tokens = { ...tokens };
    },
  },
  persist: {
    pick: ['tokens'],
  },
  state: (): { tokens: null | SessionToken } => ({
    tokens: null,
  }),
});

type PendingLoginMethod =
  | 'email-code'
  | 'passkey'
  | 'password'
  | 'sms-code'
  | 'totp';

interface InitialEmailCodeChallenge extends EmailCodeChallenge {
  readonly email: string;
}

interface InitialSmsCodeChallenge extends SmsCodeChallenge {
  readonly phoneNumber: string;
}

const SUPPORTED_PENDING_LOGIN_METHODS: readonly PendingLoginMethod[] = [
  'password',
  'totp',
  'passkey',
  'email-code',
  'sms-code',
];

/** 管理登录起点、后续认证因素、Session 恢复、刷新和注销。 */
export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const sessionTokenStore = useSessionTokenStore();
  const tabbarStore = useTabbarStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);
  const federatedLoginLoading = ref(false);
  const emailCodeSending = ref(false);
  const smsCodeSending = ref(false);
  const initialEmailCodeChallenge = ref<InitialEmailCodeChallenge | null>(null);
  const initialSmsCodeChallenge = ref<InitialSmsCodeChallenge | null>(null);
  const pendingEmailCodeChallenge = ref<EmailCodeChallenge | null>(null);
  const pendingSmsCodeChallenge = ref<SmsCodeChallenge | null>(null);
  const currentSession = ref<CurrentSession | null>(null);
  const federatedProviders = ref<readonly FederatedProvider[]>([]);
  const pendingLoginAttemptId = ref<null | string>(null);
  const pendingLoginMethods = ref<readonly string[]>([]);
  const restorationComplete = ref(false);
  const requiresAdditionalAuthentication = computed(
    () => pendingLoginAttemptId.value !== null,
  );
  const supportsPendingTotp = computed(() =>
    pendingLoginMethods.value.includes('totp'),
  );
  const supportsPendingPassword = computed(() =>
    pendingLoginMethods.value.includes('password'),
  );
  const supportsPendingEmailCode = computed(() =>
    pendingLoginMethods.value.includes('email-code'),
  );
  const supportsPendingSmsCode = computed(() =>
    pendingLoginMethods.value.includes('sms-code'),
  );
  const availablePendingLoginMethods = computed<readonly PendingLoginMethod[]>(
    () => {
      const available = new Set(pendingLoginMethods.value);
      return SUPPORTED_PENDING_LOGIN_METHODS.filter((method) =>
        method === 'passkey'
          ? available.has(method) && browserPasskeyClient.isSupported()
          : available.has(method),
      );
    },
  );
  const passkeySupported = computed(() => browserPasskeyClient.isSupported());
  const supportsPendingPasskey = computed(
    () =>
      passkeySupported.value && pendingLoginMethods.value.includes('passkey'),
  );

  let refreshPromise: null | Promise<void> = null;
  let refreshTimer: null | ReturnType<typeof setTimeout> = null;
  let serverRefreshPromise: null | Promise<void> = null;
  let restorationPromise: null | Promise<boolean> = null;
  let stopSessionEvents: (() => void) | null = null;

  /** 终止当前 Session SSE 请求和自动重连。 */
  function closeSessionEvents(): void {
    const stop = stopSessionEvents;
    stopSessionEvents = null;
    stop?.();
  }

  /** 清除计时器、Token、用户和动态访问状态。 */
  function clearLocalSession(): void {
    businessApplication.files.clear();
    deactivatePreferenceSync();
    closeSessionEvents();
    if (refreshTimer) clearTimeout(refreshTimer);
    refreshTimer = null;
    sessionTokenStore.clearTokens();
    accessStore.$reset();
    tabbarStore.$reset();
    userStore.$reset();
    accessStore.setLoginExpired(false);
    pendingLoginAttemptId.value = null;
    pendingLoginMethods.value = [];
    initialEmailCodeChallenge.value = null;
    initialSmsCodeChallenge.value = null;
    pendingEmailCodeChallenge.value = null;
    pendingSmsCodeChallenge.value = null;
    currentSession.value = null;
  }

  /** 使用当前 backing Session Token 建立唯一 SSE 连接。 */
  function openSessionEvents(): void {
    if (stopSessionEvents) return;
    const tokens = sessionTokenStore.tokens;
    if (!tokens) return;
    stopSessionEvents = subscribeSessionEvents({
      onAuthenticationFailure: () => void handleAuthenticationFailure(),
      onInboxChanged: () =>
        window.dispatchEvent(new Event('sca4d:notification-inbox-changed')),
      onSessionChanged: refreshSessionFromServer,
      sessionToken: selectAuthenticationToken(tokens, 'session'),
    });
  }

  /** 合并响应头和实时事件，刷新 Session 后重新安装权限路由并重建 SSE。 */
  function refreshSessionFromServer(): void {
    if (serverRefreshPromise) return;
    closeSessionEvents();
    serverRefreshPromise = (async () => {
      const tokens = sessionTokenStore.tokens;
      if (!tokens) return;
      const sessionId = tokens.sessionId;
      const activeRouteName = router.currentRoute.value.name;

      if (tokens.tokenMode === 'JWT') {
        await refreshAccessToken();
      }
      if (sessionTokenStore.tokens?.sessionId !== sessionId) return;

      const userInfo = await fetchUserInfo();
      if (sessionTokenStore.tokens?.sessionId !== sessionId) return;

      tabbarStore.$reset();
      await replaceAccess(router, [...accessStore.accessCodes]);
      if (!activeRouteName || !router.hasRoute(activeRouteName)) {
        await router.replace(
          userInfo.homePath || preferences.app.defaultHomePath,
        );
      }
    })()
      .catch(async () => {
        if (sessionTokenStore.tokens) {
          await handleAuthenticationFailure();
        }
      })
      .finally(() => {
        serverRefreshPromise = null;
        openSessionEvents();
      });
  }

  /** 保存新 Token，并让 Vben 普通请求立即使用新的 access Token。 */
  function acceptTokens(tokens: SessionToken): void {
    if (sessionTokenStore.tokens?.sessionId !== tokens.sessionId)
      businessApplication.files.clear();
    sessionTokenStore.setTokens(tokens);
    accessStore.setAccessToken(tokens.accessToken);
    scheduleTokenRefresh(tokens);
  }

  /** 在 Token 到期前刷新 JWT；Session 到期时只清除本地状态。 */
  function scheduleTokenRefresh(tokens: SessionToken): void {
    if (refreshTimer) clearTimeout(refreshTimer);
    refreshTimer = null;

    const now = Date.now();
    const sessionRemaining = Date.parse(tokens.sessionExpiresAt) - now;
    const accessRemaining = Date.parse(tokens.accessTokenExpiresAt) - now;
    if (sessionRemaining <= 0 || accessRemaining <= 0) {
      clearLocalSession();
      return;
    }

    const refreshDelay = millisecondsUntilTokenRefresh(tokens, now);
    if (refreshDelay === null) {
      const expiryDelay = Math.min(
        tokens.tokenMode === 'SESSION'
          ? Math.min(sessionRemaining, accessRemaining)
          : sessionRemaining,
        2_147_483_647,
      );
      refreshTimer = setTimeout(() => {
        const current = sessionTokenStore.tokens;
        if (!current) return;
        if (
          Date.parse(current.sessionExpiresAt) <= Date.now() ||
          (current.tokenMode === 'SESSION' &&
            Date.parse(current.accessTokenExpiresAt) <= Date.now())
        ) {
          clearLocalSession();
        } else {
          scheduleTokenRefresh(current);
        }
      }, expiryDelay);
      return;
    }

    refreshTimer = setTimeout(() => {
      const current = sessionTokenStore.tokens;
      if (!current) return;
      const remainingDelay = millisecondsUntilTokenRefresh(current);
      if (remainingDelay !== null && remainingDelay > 0) {
        scheduleTokenRefresh(current);
        return;
      }
      void refreshAccessToken().catch(() => clearLocalSession());
    }, refreshDelay);
  }

  /** 使用 Session Token 刷新 JWT，并合并同时发生的刷新请求。 */
  function refreshAccessToken(): Promise<void> {
    if (refreshPromise) return refreshPromise;

    const tokens = sessionTokenStore.tokens;
    if (!tokens || tokens.tokenMode !== 'JWT') {
      return Promise.reject(new Error('当前 Session 不需要刷新 access Token'));
    }
    if (Date.parse(tokens.sessionExpiresAt) <= Date.now()) {
      return Promise.reject(new Error('当前 Session 已经过期'));
    }

    const sessionToken = selectAuthenticationToken(tokens, 'session');
    refreshPromise = refreshSessionTokensApi(sessionToken)
      .then((nextTokens) => {
        if (sessionTokenStore.tokens?.sessionId === nextTokens.sessionId) {
          acceptTokens(nextTokens);
        }
      })
      .finally(() => {
        refreshPromise = null;
      });
    return refreshPromise;
  }

  /** 读取当前 Session，把身份与权限写入 Vben Store。 */
  async function fetchUserInfo(): Promise<UserInfo> {
    const expectedSessionId = sessionTokenStore.tokens?.sessionId;
    if (!expectedSessionId) {
      throw new Error('当前没有可读取的 Session');
    }
    const [session, profile] = await Promise.all([
      getCurrentSessionApi(),
      getCurrentProfileApi(),
    ]);
    if (
      session.sessionId !== expectedSessionId ||
      sessionTokenStore.tokens?.sessionId !== expectedSessionId
    ) {
      throw new Error('读取 Session 期间本地登录状态已变化');
    }
    currentSession.value = session;
    await activatePreferenceSync(profile.principalId);
    const accountId = session.account.accountId;
    const baseUserInfo = currentSessionUser(session, profile, {
      accountDescription: $t('page.business.accountIdentifier', { accountId }),
      fallbackName: $t('page.business.accountFallbackName', { accountId }),
      realmDescription: $t(
        session.account.realmKey === 'platform'
          ? 'page.business.platformRealm'
          : 'page.business.otherRealm',
        { realmKey: session.account.realmKey },
      ),
    });
    let avatar = '';
    if (profile.avatarFileId) {
      try {
        const accessUrls = await getFileAccessUrlsApi([profile.avatarFileId]);
        avatar =
          accessUrls.find((item) => item.fileId === profile.avatarFileId)
            ?.url ?? '';
      } catch {
        // 头像是可选展示内容，签发失败时继续使用 Vben 默认头像。
      }
    }
    const userInfo = { ...baseUserInfo, avatar };
    accessStore.setAccessCodes([...session.permissions]);
    userStore.setUserInfo(userInfo);
    return userInfo;
  }

  /** 接收认证结果；策略满足后保存 Token 并进入站内目标页。 */
  async function acceptLoginOutcome(
    outcome: LoginOutcome,
  ): Promise<null | UserInfo> {
    if (!outcome.session) {
      if (
        !outcome.authentication.availableMethods.some((method) =>
          SUPPORTED_PENDING_LOGIN_METHODS.includes(
            method as PendingLoginMethod,
          ),
        )
      ) {
        throw new Error('登录还需要示例页面不支持的认证因素');
      }
      if (pendingLoginAttemptId.value !== outcome.authentication.attemptId) {
        pendingEmailCodeChallenge.value = null;
      }
      pendingLoginAttemptId.value = outcome.authentication.attemptId;
      pendingLoginMethods.value = outcome.authentication.availableMethods;
      return null;
    }

    pendingLoginAttemptId.value = null;
    pendingLoginMethods.value = [];
    initialEmailCodeChallenge.value = null;
    pendingEmailCodeChallenge.value = null;
    acceptTokens(outcome.session);
    const userInfo = await fetchUserInfo();
    restorationComplete.value = true;
    openSessionEvents();
    await router.replace(
      safeLoginRedirect(
        router.currentRoute.value.query.redirect,
        userInfo.homePath || preferences.app.defaultHomePath,
      ),
    );
    return userInfo;
  }

  /** 使用表单中的用户名或邮箱和密码开始登录；后续因素继续使用同一 attempt。 */
  async function authLogin(params: Recordable<unknown>) {
    loginLoading.value = true;
    try {
      initialEmailCodeChallenge.value = null;
      pendingEmailCodeChallenge.value = null;
      const identifier =
        typeof params.username === 'string' ? params.username : '';
      const password =
        typeof params.password === 'string' ? params.password : '';
      const outcome = await createLoginAttemptApi({ identifier, password });
      return { userInfo: await acceptLoginOutcome(outcome) };
    } finally {
      loginLoading.value = false;
    }
  }

  /** 读取后端按当前语言返回的登录 Provider。 */
  async function loadFederatedProviders(): Promise<void> {
    try {
      federatedProviders.value = Object.freeze([
        ...(await getFederatedProvidersApi()),
      ]);
    } catch {
      federatedProviders.value = [];
    }
  }

  /** 使用已加载 Provider 跳转到后端生成的上游授权地址。 */
  async function loginWithFederated(providerCode: string): Promise<void> {
    federatedLoginLoading.value = true;
    try {
      if (
        !federatedProviders.value.some(
          (provider) =>
            provider.code === providerCode && provider.status === 'ACTIVE',
        )
      ) {
        throw new Error('联邦 Provider 当前不可用');
      }
      const authorization = await createFederatedAuthorizationApi(providerCode);
      window.location.assign(authorization.authorizationUrl);
    } finally {
      federatedLoginLoading.value = false;
    }
  }

  /** 消费 callback completion id，继续已有认证策略。 */
  async function completeFederatedLogin(
    authorizationId: string,
  ): Promise<void> {
    loginLoading.value = true;
    try {
      await acceptLoginOutcome(
        await createFederatedLoginAttemptApi(authorizationId),
      );
    } finally {
      loginLoading.value = false;
    }
  }

  /** 清除尚未消费的匿名 Email Code Challenge。 */
  function clearInitialEmailCodeChallenge(): void {
    initialEmailCodeChallenge.value = null;
  }

  /** 清除尚未消费的匿名 SMS Code Challenge。 */
  function clearInitialSmsCodeChallenge(): void {
    initialSmsCodeChallenge.value = null;
  }

  /** 使用当前邮箱签发匿名一致的 Email Code Challenge。 */
  async function requestEmailLoginCode(
    email: string,
  ): Promise<EmailCodeChallenge> {
    const normalizedEmail = email.trim();
    emailCodeSending.value = true;
    try {
      const challenge = await createEmailCodeChallengeApi({
        email: normalizedEmail,
      });
      initialEmailCodeChallenge.value = Object.freeze({
        ...challenge,
        email: normalizedEmail,
      });
      return challenge;
    } finally {
      emailCodeSending.value = false;
    }
  }

  /** 使用当前匿名 Challenge 和六位验证码开始登录。 */
  async function loginWithEmailCode(email: string, code: string) {
    const challenge = initialEmailCodeChallenge.value;
    if (!challenge || challenge.email !== email.trim()) {
      throw new Error('邮箱已经变化，请重新发送验证码');
    }
    loginLoading.value = true;
    try {
      const outcome = await createEmailCodeLoginAttemptApi({
        challengeId: challenge.challengeId,
        code,
      });
      initialEmailCodeChallenge.value = null;
      return { userInfo: await acceptLoginOutcome(outcome) };
    } finally {
      loginLoading.value = false;
    }
  }

  /** 使用当前手机号签发匿名一致的 SMS Code Challenge。 */
  async function requestSmsLoginCode(
    phoneNumber: string,
  ): Promise<SmsCodeChallenge> {
    const normalizedPhone = phoneNumber.trim();
    smsCodeSending.value = true;
    try {
      const challenge = await createSmsCodeChallengeApi({
        phoneNumber: normalizedPhone,
      });
      initialSmsCodeChallenge.value = Object.freeze({
        ...challenge,
        phoneNumber: normalizedPhone,
      });
      return challenge;
    } finally {
      smsCodeSending.value = false;
    }
  }

  /** 使用当前匿名 SMS Challenge 和六位验证码开始登录。 */
  async function loginWithSmsCode(phoneNumber: string, code: string) {
    const challenge = initialSmsCodeChallenge.value;
    if (!challenge || challenge.phoneNumber !== phoneNumber.trim()) {
      throw new Error('手机号已经变化，请重新发送验证码');
    }
    loginLoading.value = true;
    try {
      const outcome = await createSmsCodeLoginAttemptApi({
        challengeId: challenge.challengeId,
        code,
      });
      initialSmsCodeChallenge.value = null;
      return { userInfo: await acceptLoginOutcome(outcome) };
    } finally {
      loginLoading.value = false;
    }
  }

  /** 为当前登录 attempt 签发 Email Code Challenge。 */
  async function requestPendingEmailCode(): Promise<EmailCodeChallenge> {
    const attemptId = pendingLoginAttemptId.value;
    if (!attemptId) throw new Error('当前没有等待 Email Code 的登录 attempt');
    emailCodeSending.value = true;
    try {
      const challenge = await createPendingEmailCodeChallengeApi(attemptId);
      pendingEmailCodeChallenge.value = Object.freeze({ ...challenge });
      return challenge;
    } finally {
      emailCodeSending.value = false;
    }
  }

  /** 向当前登录 attempt 提交 Email Code。 */
  async function submitEmailCode(code: string) {
    const attemptId = pendingLoginAttemptId.value;
    const challenge = pendingEmailCodeChallenge.value;
    if (!attemptId || !challenge) {
      throw new Error('请先发送 Email 验证码');
    }
    loginLoading.value = true;
    try {
      const outcome = await submitLoginEmailCodeApi(attemptId, {
        challengeId: challenge.challengeId,
        code,
      });
      pendingEmailCodeChallenge.value = null;
      return { userInfo: await acceptLoginOutcome(outcome) };
    } finally {
      loginLoading.value = false;
    }
  }

  /** 为当前登录 attempt 签发 SMS Code Challenge。 */
  async function requestPendingSmsCode(): Promise<SmsCodeChallenge> {
    const attemptId = pendingLoginAttemptId.value;
    if (!attemptId) throw new Error('当前没有等待 SMS Code 的登录 attempt');
    smsCodeSending.value = true;
    try {
      const challenge = await createPendingSmsCodeChallengeApi(attemptId);
      pendingSmsCodeChallenge.value = Object.freeze({ ...challenge });
      return challenge;
    } finally {
      smsCodeSending.value = false;
    }
  }

  /** 向当前登录 attempt 提交 SMS Code。 */
  async function submitSmsCode(code: string) {
    const attemptId = pendingLoginAttemptId.value;
    const challenge = pendingSmsCodeChallenge.value;
    if (!attemptId || !challenge) throw new Error('请先发送 SMS 验证码');
    loginLoading.value = true;
    try {
      const outcome = await submitLoginSmsCodeApi(attemptId, {
        challengeId: challenge.challengeId,
        code,
      });
      pendingSmsCodeChallenge.value = null;
      return { userInfo: await acceptLoginOutcome(outcome) };
    } finally {
      loginLoading.value = false;
    }
  }

  /** 向当前登录 attempt 提交密码。 */
  async function submitPassword(password: string) {
    const attemptId = pendingLoginAttemptId.value;
    if (!attemptId) throw new Error('当前没有等待密码的登录 attempt');
    loginLoading.value = true;
    try {
      const outcome = await submitLoginPasswordApi(attemptId, { password });
      return { userInfo: await acceptLoginOutcome(outcome) };
    } finally {
      loginLoading.value = false;
    }
  }

  /** 向当前登录 attempt 提交 TOTP，成功后建立 Session。 */
  async function submitTotp(code: string) {
    const attemptId = pendingLoginAttemptId.value;
    if (!attemptId) throw new Error('当前没有等待 TOTP 的登录 attempt');

    loginLoading.value = true;
    try {
      const outcome = await submitLoginTotpApi(attemptId, { code });
      return { userInfo: await acceptLoginOutcome(outcome) };
    } finally {
      loginLoading.value = false;
    }
  }

  /** 使用 discoverable Passkey 开始登录。 */
  async function loginWithPasskey() {
    loginLoading.value = true;
    try {
      const proof = await browserPasskeyClient.getAssertion(
        await createPasskeyAssertionOptionsApi(),
      );
      return {
        userInfo: await acceptLoginOutcome(
          await createPasskeyLoginAttemptApi(proof),
        ),
      };
    } finally {
      loginLoading.value = false;
    }
  }

  /** 向当前登录 attempt 提交 Passkey。 */
  async function submitPasskey() {
    const attemptId = pendingLoginAttemptId.value;
    if (!attemptId) throw new Error('当前没有等待 Passkey 的登录 attempt');
    loginLoading.value = true;
    try {
      const proof = await browserPasskeyClient.getAssertion(
        await createPasskeyAssertionOptionsApi(),
      );
      return {
        userInfo: await acceptLoginOutcome(
          await submitLoginPasskeyApi(attemptId, proof),
        ),
      };
    } finally {
      loginLoading.value = false;
    }
  }

  /** 从持久化 Token 恢复 Session；失败时删除已经失效的本地状态。 */
  async function restoreSession(): Promise<boolean> {
    if (restorationComplete.value) return Boolean(accessStore.accessToken);
    if (restorationPromise) return restorationPromise;

    restorationPromise = (async () => {
      const tokens = sessionTokenStore.tokens;
      if (!tokens || Date.parse(tokens.sessionExpiresAt) <= Date.now()) {
        clearLocalSession();
        restorationComplete.value = true;
        return false;
      }

      try {
        if (
          tokens.tokenMode === 'JWT' &&
          Date.parse(tokens.accessTokenExpiresAt) <= Date.now()
        ) {
          await refreshAccessToken();
        } else {
          acceptTokens(tokens);
        }
        await fetchUserInfo();
        restorationComplete.value = true;
        openSessionEvents();
        return true;
      } catch {
        clearLocalSession();
        restorationComplete.value = true;
        return false;
      }
    })().finally(() => {
      restorationPromise = null;
    });

    return restorationPromise;
  }

  /** 注销远端 Session；无论远端结果如何都删除本地 Token。 */
  async function logout(redirect: boolean = true): Promise<void> {
    const tokens = sessionTokenStore.tokens;
    try {
      if (tokens) {
        await logoutSessionApi(selectAuthenticationToken(tokens, 'session'));
      }
    } finally {
      const redirectPath = router.currentRoute.value.fullPath;
      clearLocalSession();
      await router.replace({
        path: LOGIN_PATH,
        query: redirect ? { redirect: redirectPath } : {},
      });
    }
  }

  /** 处理普通认证请求的 401：清除本地状态并返回登录页。 */
  async function handleAuthenticationFailure(): Promise<void> {
    const redirectPath = router.currentRoute.value.fullPath;
    clearLocalSession();
    await router.replace({
      path: LOGIN_PATH,
      query: { redirect: redirectPath },
    });
  }

  /** 重置不由 Pinia 自动重置的临时状态和计时器。 */
  function $reset(): void {
    closeSessionEvents();
    if (refreshTimer) clearTimeout(refreshTimer);
    refreshTimer = null;
    loginLoading.value = false;
    federatedLoginLoading.value = false;
    emailCodeSending.value = false;
    smsCodeSending.value = false;
    pendingLoginAttemptId.value = null;
    pendingLoginMethods.value = [];
    initialEmailCodeChallenge.value = null;
    initialSmsCodeChallenge.value = null;
    pendingEmailCodeChallenge.value = null;
    pendingSmsCodeChallenge.value = null;
    restorationComplete.value = false;
    currentSession.value = null;
    federatedProviders.value = [];
  }

  registerAuthenticationFailureHandler(handleAuthenticationFailure);
  registerAuthenticationSessionRefreshHandler(refreshSessionFromServer);

  return {
    $reset,
    availablePendingLoginMethods,
    authLogin,
    completeFederatedLogin,
    loginWithFederated,
    clearInitialEmailCodeChallenge,
    clearInitialSmsCodeChallenge,
    currentSession,
    emailCodeSending,
    federatedProviders,
    federatedLoginLoading,
    fetchUserInfo,
    initialEmailCodeChallenge,
    initialSmsCodeChallenge,
    loginLoading,
    loadFederatedProviders,
    loginWithEmailCode,
    loginWithSmsCode,
    loginWithPasskey,
    logout,
    passkeySupported,
    pendingEmailCodeChallenge,
    pendingSmsCodeChallenge,
    requestEmailLoginCode,
    requestPendingEmailCode,
    requestPendingSmsCode,
    requestSmsLoginCode,
    requiresAdditionalAuthentication,
    restoreSession,
    submitEmailCode,
    submitPasskey,
    submitPassword,
    submitSmsCode,
    submitTotp,
    supportsPendingEmailCode,
    supportsPendingSmsCode,
    supportsPendingPasskey,
    supportsPendingPassword,
    supportsPendingTotp,
    smsCodeSending,
  };
});
