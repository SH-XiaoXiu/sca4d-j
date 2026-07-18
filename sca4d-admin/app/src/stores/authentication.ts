import {
  type AccountIdentifierType,
  type AuthenticationProcess,
  type AuthenticationScopeSelection,
  type EmailCodeChallenge,
  type LoginSession,
  type PasskeyAssertionView
} from "@sca4d/admin-common";
import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { authenticationApi } from "../api/client";
import { useSessionStore } from "./session";

const DEVICE_KEY = "sca4d-admin.device-id";
const OIDC_LOGIN_KEY = "sca4d-admin.oidc-login";
const OIDC_LINK_KEY = "sca4d-admin.oidc-link";
const OAUTH2_LOGIN_KEY = "sca4d-admin.oauth2-login";
const OAUTH2_LINK_KEY = "sca4d-admin.oauth2-link";
const STEP_UP_KEY = "sca4d-admin.step-up";

interface PendingExternalLogin {
  authenticationId: string;
  remember: boolean;
  redirect: string;
}

interface PendingExternalIdentityLink {
  authenticationId: string;
  redirect: string;
}

interface ReusableStepUp {
  authenticationId: string;
  accountId: string;
  expiresAt: string;
}

function deviceId(): string {
  const stored = localStorage.getItem(DEVICE_KEY);
  if (stored) return stored;
  const value = globalThis.crypto.randomUUID();
  localStorage.setItem(DEVICE_KEY, value);
  return value;
}

function parseStoredState<T extends object>(key: string): T | null {
  const raw = sessionStorage.getItem(key);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as unknown;
    return parsed && typeof parsed === "object" ? parsed as T : null;
  } catch {
    return null;
  }
}

function isCompletion(process: AuthenticationProcess): boolean {
  return process.activityId === "completion";
}

/** 管理登录认证、登录二步验证、账号恢复和外部身份认证的短期状态。 */
export const useAuthenticationStore = defineStore("authentication", () => {
  const pendingAuthenticationId = ref<string | null>(null);
  const pendingSecondStep = ref(false);
  const pendingEmailCode = ref<EmailCodeChallenge | null>(null);
  const remember = ref(false);
  const secondStepRequired = computed(() => pendingSecondStep.value && Boolean(pendingAuthenticationId.value));

  function rememberStepUp(process: AuthenticationProcess): void {
    if (!isCompletion(process) || !process.accountId) return;
    sessionStorage.setItem(STEP_UP_KEY, JSON.stringify({
      authenticationId: process.authenticationId,
      accountId: process.accountId,
      expiresAt: process.expiresAt
    } satisfies ReusableStepUp));
  }

  function reusableStepUp(accountId: string | null): string | null {
    const stored = parseStoredState<ReusableStepUp>(STEP_UP_KEY);
    if (!accountId || !stored || typeof stored.authenticationId !== "string"
      || typeof stored.accountId !== "string" || typeof stored.expiresAt !== "string"
      || stored.accountId !== accountId || new Date(stored.expiresAt).getTime() <= Date.now()) {
      sessionStorage.removeItem(STEP_UP_KEY);
      return null;
    }
    return stored.authenticationId;
  }

  function forgetStepUp(authenticationId?: string): void {
    const stored = parseStoredState<ReusableStepUp>(STEP_UP_KEY);
    if (!authenticationId || stored?.authenticationId === authenticationId) sessionStorage.removeItem(STEP_UP_KEY);
  }

  async function establishSession(authenticationId: string): Promise<LoginSession> {
    const created = await authenticationApi.createSession(authenticationId);
    await useSessionStore().establish(created, remember.value);
    pendingAuthenticationId.value = null;
    pendingSecondStep.value = false;
    pendingEmailCode.value = null;
    return created;
  }

  function acceptPrimaryProof(process: AuthenticationProcess): boolean {
    pendingAuthenticationId.value = process.authenticationId;
    pendingEmailCode.value = null;
    if (process.activityId === "second-factor-proof") {
      pendingSecondStep.value = true;
      return true;
    }
    if (!isCompletion(process)) {
      throw new Error(`Authentication entered unsupported activity: ${process.activityId}`);
    }
    pendingSecondStep.value = false;
    return false;
  }

  /** 使用账号密码完成第一步登录；返回是否需要继续二步验证。 */
  async function loginWithPassword(
    input: { identifierType?: AccountIdentifierType; identifier: string; password: string },
    keep: boolean,
    scope: AuthenticationScopeSelection
  ): Promise<boolean> {
    remember.value = keep;
    const authentication = await authenticationApi.createAuthentication({
      purpose: "LOGIN",
      ...scope,
      deviceId: deviceId(),
      terminalName: "SCA4D Admin"
    });
    const process = await authenticationApi.passwordProof(authentication.authenticationId, {
      identifierType: input.identifierType ?? "USERNAME",
      identifier: input.identifier,
      password: input.password
    });
    const requiresSecondStep = acceptPrimaryProof(process);
    if (!requiresSecondStep) await establishSession(process.authenticationId);
    return requiresSecondStep;
  }

  /** 使用 LDAP 身份源完成第一步登录；返回是否需要继续二步验证。 */
  async function loginWithLdap(
    providerCode: string,
    username: string,
    password: string,
    keep: boolean,
    scope: AuthenticationScopeSelection
  ): Promise<boolean> {
    remember.value = keep;
    const authentication = await authenticationApi.createAuthentication({
      purpose: "LOGIN",
      ...scope,
      deviceId: deviceId(),
      terminalName: "SCA4D Admin"
    });
    const process = await authenticationApi.ldapProof(authentication.authenticationId, {
      providerCode,
      username,
      password
    });
    const requiresSecondStep = acceptPrimaryProof(process);
    if (!requiresSecondStep) await establishSession(process.authenticationId);
    return requiresSecondStep;
  }

  /** 创建 OIDC 登录认证并保存回调恢复所需状态。 */
  async function startOidcLogin(
    providerCode: string,
    keep: boolean,
    redirect: string,
    scope: AuthenticationScopeSelection
  ): Promise<string> {
    remember.value = keep;
    const authentication = await authenticationApi.createAuthentication({
      purpose: "LOGIN",
      ...scope,
      deviceId: deviceId(),
      terminalName: "SCA4D Admin"
    });
    const authorization = await authenticationApi.createOidcAuthorization(authentication.authenticationId, { providerCode });
    sessionStorage.setItem(OIDC_LOGIN_KEY, JSON.stringify({
      authenticationId: authentication.authenticationId,
      remember: keep,
      redirect
    } satisfies PendingExternalLogin));
    return authorization.authorizationUri;
  }

  /** 创建 OAuth2 登录认证并保存回调恢复所需状态。 */
  async function startOAuth2Login(
    providerCode: string,
    keep: boolean,
    redirect: string,
    scope: AuthenticationScopeSelection
  ): Promise<string> {
    remember.value = keep;
    const authentication = await authenticationApi.createAuthentication({
      purpose: "LOGIN",
      ...scope,
      deviceId: deviceId(),
      terminalName: "SCA4D Admin"
    });
    const authorization = await authenticationApi.createOAuth2Authorization(authentication.authenticationId, { providerCode });
    sessionStorage.setItem(OAUTH2_LOGIN_KEY, JSON.stringify({
      authenticationId: authentication.authenticationId,
      remember: keep,
      redirect
    } satisfies PendingExternalLogin));
    return authorization.authorizationUri;
  }

  async function finishExternalLogin(
    key: string,
    protocol: "OIDC" | "OAUTH2",
    authenticationId: string,
    activityId: string
  ): Promise<{ requiresSecondFactor: boolean; redirect: string }> {
    const pending = parseStoredState<PendingExternalLogin>(key);
    if (!pending
      || typeof pending.authenticationId !== "string"
      || typeof pending.remember !== "boolean"
      || typeof pending.redirect !== "string") {
      sessionStorage.removeItem(key);
      throw new Error(`${protocol} login state is invalid`);
    }
    if (pending.authenticationId !== authenticationId) {
      sessionStorage.removeItem(key);
      throw new Error(`${protocol} login authentication does not match`);
    }
    remember.value = pending.remember;
    pendingAuthenticationId.value = authenticationId;
    pendingEmailCode.value = null;
    sessionStorage.removeItem(key);
    if (activityId === "second-factor-proof") {
      pendingSecondStep.value = true;
      return { requiresSecondFactor: true, redirect: pending.redirect };
    }
    if (activityId !== "completion") {
      cancelLogin();
      throw new Error(`Authentication entered unsupported activity: ${activityId}`);
    }
    await establishSession(authenticationId);
    return { requiresSecondFactor: false, redirect: pending.redirect };
  }

  /** 恢复 OIDC 回调后的登录认证。 */
  function finishOidcLogin(authenticationId: string, activityId: string) {
    return finishExternalLogin(OIDC_LOGIN_KEY, "OIDC", authenticationId, activityId);
  }

  /** 恢复 OAuth2 回调后的登录认证。 */
  function finishOAuth2Login(authenticationId: string, activityId: string) {
    return finishExternalLogin(OAUTH2_LOGIN_KEY, "OAUTH2", authenticationId, activityId);
  }

  /** 创建 discoverable Passkey 登录认证和 assertion。 */
  async function startPasskeyLogin(
    keep: boolean,
    scope: AuthenticationScopeSelection
  ): Promise<PasskeyAssertionView> {
    remember.value = keep;
    const authentication = await authenticationApi.createAuthentication({
      purpose: "LOGIN",
      ...scope,
      deviceId: deviceId(),
      terminalName: "SCA4D Admin"
    });
    pendingAuthenticationId.value = authentication.authenticationId;
    pendingSecondStep.value = false;
    pendingEmailCode.value = null;
    return authenticationApi.createPasskeyAssertion(authentication.authenticationId);
  }

  /** 提交 Passkey 登录结果；返回是否需要继续二步验证。 */
  async function finishPasskeyLogin(assertionId: string, credential: unknown): Promise<boolean> {
    if (!pendingAuthenticationId.value) throw new Error("No authentication is active");
    const process = await authenticationApi.passkeyProof(pendingAuthenticationId.value, { assertionId, credential });
    const requiresSecondStep = acceptPrimaryProof(process);
    if (!requiresSecondStep) await establishSession(process.authenticationId);
    return requiresSecondStep;
  }

  /** 为等待二步验证的登录认证发送邮件验证码。 */
  async function startEmailCode(): Promise<EmailCodeChallenge> {
    if (!pendingAuthenticationId.value || !pendingSecondStep.value) {
      throw new Error("No authentication is waiting for a second step");
    }
    pendingEmailCode.value = await authenticationApi.createEmailCodeChallenge(pendingAuthenticationId.value);
    return pendingEmailCode.value;
  }

  /** 继续使用当前登录认证重发邮件验证码。 */
  function resendEmailCode(): Promise<EmailCodeChallenge> {
    return startEmailCode();
  }

  /** 使用邮件验证码完成登录二步验证并创建 Session。 */
  async function completeEmailCode(code: string): Promise<void> {
    if (!pendingAuthenticationId.value || !pendingEmailCode.value) {
      throw new Error("No email code challenge is active");
    }
    const process = await authenticationApi.emailCodeProof(pendingAuthenticationId.value, {
      challengeId: pendingEmailCode.value.challengeId,
      code
    });
    if (!isCompletion(process)) throw new Error(`Authentication entered unsupported activity: ${process.activityId}`);
    await establishSession(process.authenticationId);
  }

  /** 使用身份验证器验证码完成登录二步验证并创建 Session。 */
  async function completeTotp(code: string): Promise<void> {
    if (!pendingAuthenticationId.value || !pendingSecondStep.value) {
      throw new Error("No authentication is waiting for a second step");
    }
    const process = await authenticationApi.totpProof(pendingAuthenticationId.value, { code });
    if (!isCompletion(process)) throw new Error(`Authentication entered unsupported activity: ${process.activityId}`);
    await establishSession(process.authenticationId);
  }

  /** 使用本地密码完成非密码首因子的登录二步验证并创建 Session。 */
  async function completePasswordSecondStep(password: string): Promise<void> {
    if (!pendingAuthenticationId.value || !pendingSecondStep.value) {
      throw new Error("No authentication is waiting for a second step");
    }
    const process = await authenticationApi.passwordProof(pendingAuthenticationId.value, { password });
    if (!isCompletion(process)) throw new Error(`Authentication entered unsupported activity: ${process.activityId}`);
    await establishSession(process.authenticationId);
  }

  /** 为登录二步验证创建 Passkey assertion。 */
  function startPasskeySecondStep(): Promise<PasskeyAssertionView> {
    if (!pendingAuthenticationId.value || !pendingSecondStep.value) {
      throw new Error("No authentication is waiting for a second step");
    }
    return authenticationApi.createPasskeyAssertion(pendingAuthenticationId.value);
  }

  /** 使用 Passkey 完成登录二步验证并创建 Session。 */
  async function completePasskeySecondStep(assertionId: string, credential: unknown): Promise<void> {
    if (!pendingAuthenticationId.value || !pendingSecondStep.value) {
      throw new Error("No authentication is waiting for a second step");
    }
    const process = await authenticationApi.passkeyProof(pendingAuthenticationId.value, { assertionId, credential });
    if (!isCompletion(process)) throw new Error(`Authentication entered unsupported activity: ${process.activityId}`);
    await establishSession(process.authenticationId);
  }

  /** 创建当前 Session Account 的 OIDC 外部身份关联。 */
  async function startOidcLink(providerCode: string, redirect: string): Promise<string> {
    const authentication = await authenticationApi.createAuthentication({ purpose: "LINKING" });
    const authorization = await authenticationApi.createOidcAuthorization(authentication.authenticationId, { providerCode });
    sessionStorage.setItem(OIDC_LINK_KEY, JSON.stringify({
      authenticationId: authentication.authenticationId,
      redirect
    } satisfies PendingExternalIdentityLink));
    return authorization.authorizationUri;
  }

  /** 创建当前 Session Account 的 OAuth2 外部身份关联。 */
  async function startOAuth2Link(providerCode: string, redirect: string): Promise<string> {
    const authentication = await authenticationApi.createAuthentication({ purpose: "LINKING" });
    const authorization = await authenticationApi.createOAuth2Authorization(authentication.authenticationId, { providerCode });
    sessionStorage.setItem(OAUTH2_LINK_KEY, JSON.stringify({
      authenticationId: authentication.authenticationId,
      redirect
    } satisfies PendingExternalIdentityLink));
    return authorization.authorizationUri;
  }

  /** 使用 LDAP 凭据完成当前 Session Account 的外部身份关联。 */
  async function linkLdap(providerCode: string, username: string, password: string): Promise<void> {
    const authentication = await authenticationApi.createAuthentication({ purpose: "LINKING" });
    const process = await authenticationApi.ldapProof(authentication.authenticationId, {
      providerCode,
      username,
      password
    });
    if (process.activityId !== "completed") {
      throw new Error(`Authentication entered unsupported activity: ${process.activityId}`);
    }
  }

  /** 判断当前标签是否正在等待指定外部身份关联回调。 */
  function hasPendingExternalIdentityLink(protocol: "OIDC" | "OAUTH2"): boolean {
    return sessionStorage.getItem(protocol === "OIDC" ? OIDC_LINK_KEY : OAUTH2_LINK_KEY) !== null;
  }

  /** 验证外部回调属于当前标签发起的关联认证并返回原页面。 */
  function finishExternalIdentityLink(
    protocol: "OIDC" | "OAUTH2",
    authenticationId: string,
    activityId: string
  ): string {
    const key = protocol === "OIDC" ? OIDC_LINK_KEY : OAUTH2_LINK_KEY;
    const pending = parseStoredState<PendingExternalIdentityLink>(key);
    sessionStorage.removeItem(key);
    if (!pending
      || typeof pending.authenticationId !== "string"
      || typeof pending.redirect !== "string"
      || pending.authenticationId !== authenticationId
      || activityId !== "completed") {
      throw new Error(`${protocol} identity link state is invalid`);
    }
    return pending.redirect;
  }

  /** 取消外部身份关联并返回发起关联前的页面。 */
  function cancelExternalIdentityLink(protocol: "OIDC" | "OAUTH2"): string | null {
    const key = protocol === "OIDC" ? OIDC_LINK_KEY : OAUTH2_LINK_KEY;
    const pending = parseStoredState<PendingExternalIdentityLink>(key);
    sessionStorage.removeItem(key);
    return pending && typeof pending.redirect === "string" ? pending.redirect : null;
  }

  /** 清除未完成的登录认证和外部登录回调状态。 */
  function cancelLogin(): void {
    pendingAuthenticationId.value = null;
    pendingSecondStep.value = false;
    pendingEmailCode.value = null;
    sessionStorage.removeItem(OIDC_LOGIN_KEY);
    sessionStorage.removeItem(OAUTH2_LOGIN_KEY);
  }

  return {
    pendingAuthenticationId,
    pendingSecondStep,
    pendingEmailCode,
    secondStepRequired,
    rememberStepUp,
    reusableStepUp,
    forgetStepUp,
    loginWithPassword,
    loginWithLdap,
    startOidcLogin,
    finishOidcLogin,
    startOAuth2Login,
    finishOAuth2Login,
    startPasskeyLogin,
    finishPasskeyLogin,
    startEmailCode,
    resendEmailCode,
    completeEmailCode,
    completeTotp,
    completePasswordSecondStep,
    startPasskeySecondStep,
    completePasskeySecondStep,
    startOidcLink,
    startOAuth2Link,
    linkLdap,
    hasPendingExternalIdentityLink,
    finishExternalIdentityLink,
    cancelExternalIdentityLink,
    cancelLogin
  };
});
