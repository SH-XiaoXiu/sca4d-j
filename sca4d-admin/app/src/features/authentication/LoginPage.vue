<script setup lang="ts">
import { get as getPasskey, type CredentialRequestOptionsJSON } from "@github/webauthn-json";
import { Fingerprint, Languages, LockKeyhole, LogIn, ShieldCheck, UserRound } from "lucide-vue-next";
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import {
  isApiError,
  type AccountIdentifierType,
  type AccountScopeType,
  type AuthenticationScopeSelection,
  type AuthMethodType,
  type LdapAvailableProvider,
  type OAuth2AvailableProvider,
  type OidcAvailableProvider
} from "@sca4d/admin-common";
import { authenticationApi } from "@/api/client";
import { useAuthenticationStore } from "@/stores/authentication";
import { useSessionStore } from "@/stores/session";
import { useLocaleStore } from "@/stores/locale";
import { useLanguageStore } from "@/stores/languages";
import { isApplicationLocale } from "@/i18n";
import AccountRecoveryFlow from "./components/AccountRecoveryFlow.vue";
import LoginSecondStepForm from "./components/LoginSecondStepForm.vue";

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const authentication = useAuthenticationStore();
const locale = useLocaleStore();
const languageStore = useLanguageStore();
const { t } = useI18n();
const loading = ref(false);
const providerLoading = ref(false);
const errorMessage = ref("");
const mode = ref<"LOGIN" | "RECOVERY">("LOGIN");
const recoveryAvailable = ref(false);
const passkeyRegistered = ref(false);
const oidcProviders = ref<OidcAvailableProvider[]>([]);
const oauth2Providers = ref<OAuth2AvailableProvider[]>([]);
const ldapProviders = ref<LdapAvailableProvider[]>([]);
const primaryMethod = ref<"PASSWORD" | "PASSKEY" | "OIDC" | "OAUTH2" | "LDAP" | null>(null);
const postLoginRedirect = ref<string | null>(null);
type SecondFactorMethod = Extract<AuthMethodType, "PASSWORD" | "TOTP" | "EMAIL_CODE" | "PASSKEY">;
const secondFactorMethods = ref<SecondFactorMethod[]>([]);
const form = reactive({
  scopeType: "GLOBAL" as AccountScopeType,
  tenantCode: "",
  identifierType: "USERNAME" as AccountIdentifierType,
  identifier: "",
  password: "",
  remember: false
});
const ldapForm = reactive({ providerCode: "", username: "", password: "" });
const verificationStep = computed(() => mode.value === "LOGIN" && authentication.secondStepRequired);
const webAuthnAvailable = Boolean(window.PublicKeyCredential && navigator.credentials);
const passkeyLoginAvailable = computed(() => passkeyRegistered.value && webAuthnAvailable);
const pageTitle = computed(() => {
  if (verificationStep.value) return t("login.verificationTitle");
  return t("login.title");
});
const pageDescription = computed(() => {
  if (verificationStep.value) {
    return t("login.secondStepDescription");
  }
  return t("login.description");
});
const scopeSelection = computed<AuthenticationScopeSelection>(() => form.scopeType === "TENANT"
  ? { scopeType: "TENANT", tenantCode: form.tenantCode }
  : { scopeType: "GLOBAL" });
let providerRequest = 0;

onMounted(async () => {
  const oidcFailed = route.query.oidcFailed === "true";
  const oauth2Failed = route.query.oauth2Failed === "true";
  const authenticationId = typeof route.query.authenticationId === "string"
    ? route.query.authenticationId
    : null;
  const activityId = typeof route.query.activityId === "string" ? route.query.activityId : null;
  const callbackProtocol = route.query.protocol === "OIDC" || route.query.protocol === "OAUTH2"
    ? route.query.protocol
    : oidcFailed
      ? "OIDC"
      : oauth2Failed
        ? "OAUTH2"
        : null;
  const pendingExternalIdentityLink = callbackProtocol
    ? authentication.hasPendingExternalIdentityLink(callbackProtocol)
    : false;

  if (callbackProtocol && session.authenticated && !pendingExternalIdentityLink) {
    await router.replace({
      path: "/account-security",
      query: { identityLinkFailed: callbackProtocol }
    });
    return;
  }

  if (callbackProtocol && pendingExternalIdentityLink) {
    const failed = callbackProtocol === "OIDC" ? oidcFailed : oauth2Failed;
    try {
      if (failed || !authenticationId || !activityId) throw new Error("External identity callback failed");
      const redirect = authentication.finishExternalIdentityLink(callbackProtocol, authenticationId, activityId);
      const target = router.resolve(redirect);
      await router.replace({
        path: target.path,
        query: { ...target.query, identityLinked: callbackProtocol },
        hash: target.hash
      });
    } catch {
      const redirect = authentication.cancelExternalIdentityLink(callbackProtocol) ?? "/account-security";
      const target = router.resolve(redirect);
      await router.replace({
        path: target.path,
        query: { ...target.query, identityLinkFailed: callbackProtocol },
        hash: target.hash
      });
    }
    return;
  }

  if (oidcFailed || oauth2Failed) {
    authentication.cancelLogin();
    const query = { ...route.query };
    delete query.oidcFailed;
    delete query.oauth2Failed;
    delete query.protocol;
    delete query.authenticationId;
    delete query.activityId;
    await router.replace({ name: "login", query });
    errorMessage.value = t("login.externalIdentityFailed");
    return;
  }

  try {
    const methods = await authenticationApi.methods();
    recoveryAvailable.value = methods.some(
      (method) => method.type === "RECOVERY_CODE" && method.capabilities.includes("RECOVERY")
    );
    passkeyRegistered.value = methods.some(
      (method) => method.type === "PASSKEY" && method.capabilities.includes("LOGIN")
    );
    secondFactorMethods.value = methods
      .filter(
        (method): method is typeof method & { type: SecondFactorMethod } =>
          (method.type === "PASSWORD" || method.type === "TOTP" || method.type === "EMAIL_CODE" || method.type === "PASSKEY") &&
          method.capabilities.includes("STEP_UP")
      )
      .map((method) => method.type);
  } catch {
    recoveryAvailable.value = false;
  }
  await loadProviders();

  if (!callbackProtocol) return;
  loading.value = true;
  try {
    if (!authenticationId || !activityId) throw new Error("External identity callback is incomplete");
    const result = callbackProtocol === "OIDC"
      ? await authentication.finishOidcLogin(authenticationId, activityId)
      : await authentication.finishOAuth2Login(authenticationId, activityId);
    postLoginRedirect.value = result.redirect;
    primaryMethod.value = callbackProtocol;
    const query = { ...route.query };
    delete query.oidcFailed;
    delete query.oauth2Failed;
    delete query.protocol;
    delete query.authenticationId;
    delete query.activityId;
    await router.replace({ name: "login", query });
    if (!result.requiresSecondFactor) {
      await navigateAfterLogin();
    }
  } catch (error) {
    authentication.cancelLogin();
    const query = { ...route.query };
    delete query.oidcFailed;
    delete query.oauth2Failed;
    delete query.protocol;
    delete query.authenticationId;
    delete query.activityId;
    await router.replace({ name: "login", query });
    errorMessage.value = isApiError(error) ? error.message : t("login.externalIdentityFailed");
  } finally {
    loading.value = false;
  }
});

function selectLocale(value: unknown): void {
  if (typeof value === "string" && isApplicationLocale(value)) locale.select(value);
}

/** 清除上一个 Account 作用域读取到的 Provider，避免用户切换租户后继续提交旧选项。 */
function clearProviders(): void {
  oidcProviders.value = [];
  oauth2Providers.value = [];
  ldapProviders.value = [];
  ldapForm.providerCode = "";
}

/** 选择 GLOBAL 或 TENANT 登录入口；GLOBAL 立即读取 Provider，TENANT 等待用户提交业务编码。 */
function selectAccountScope(value: unknown): void {
  if (value !== "GLOBAL" && value !== "TENANT") return;
  form.scopeType = value;
  form.tenantCode = "";
  providerRequest += 1;
  providerLoading.value = false;
  clearProviders();
  if (value === "GLOBAL") void loadProviders();
}

/** 修改租户编码时使已读取的 Provider 失效，防止编码和 Provider 来自不同租户。 */
function updateTenantCode(value: string): void {
  form.tenantCode = value;
  providerRequest += 1;
  providerLoading.value = false;
  clearProviders();
}

/** 使用当前公开作用域读取三种标准协议的 ACTIVE Provider，并丢弃较早请求的结果。 */
async function loadProviders(): Promise<void> {
  if (form.scopeType === "TENANT" && !form.tenantCode) {
    clearProviders();
    return;
  }
  const requestId = ++providerRequest;
  const selection = scopeSelection.value;
  const tenantCode = selection.scopeType === "TENANT" ? selection.tenantCode : undefined;
  errorMessage.value = "";
  providerLoading.value = true;
  const results = await Promise.allSettled([
    authenticationApi.oidcProviders(selection.scopeType, tenantCode),
    authenticationApi.oauth2Providers(selection.scopeType, tenantCode),
    authenticationApi.ldapProviders(selection.scopeType, tenantCode)
  ]);
  if (requestId !== providerRequest) return;
  oidcProviders.value = results[0].status === "fulfilled" ? results[0].value : [];
  oauth2Providers.value = results[1].status === "fulfilled" ? results[1].value : [];
  ldapProviders.value = results[2].status === "fulfilled" ? results[2].value : [];
  ldapForm.providerCode = ldapProviders.value[0]?.code ?? "";
  providerLoading.value = false;
  const failure = results.find((result) => result.status === "rejected");
  if (failure?.status === "rejected") {
    errorMessage.value = isApiError(failure.reason)
      ? failure.reason.message
      : t("login.methodsFailed");
  }
}

async function navigateAfterLogin(): Promise<void> {
  const redirect = postLoginRedirect.value
    ?? (typeof route.query.redirect === "string" ? route.query.redirect : "/");
  const target = router.resolve(redirect);
  await router.replace(target.matched.some((record) => record.meta.public) ? "/dashboard" : target.fullPath);
}

async function submitOidc(providerCode: string): Promise<void> {
  errorMessage.value = "";
  loading.value = true;
  try {
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    const authorizationUri = await authentication.startOidcLogin(
      providerCode,
      form.remember,
      redirect,
      scopeSelection.value
    );
    window.location.assign(authorizationUri);
  } catch (error) {
    authentication.cancelLogin();
    errorMessage.value = isApiError(error) ? error.message : t("login.oidcFailed");
    loading.value = false;
  }
}

async function submitOAuth2(providerCode: string): Promise<void> {
  errorMessage.value = "";
  loading.value = true;
  try {
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    const authorizationUri = await authentication.startOAuth2Login(
      providerCode,
      form.remember,
      redirect,
      scopeSelection.value
    );
    window.location.assign(authorizationUri);
  } catch (error) {
    authentication.cancelLogin();
    errorMessage.value = isApiError(error) ? error.message : t("login.externalIdentityFailed");
    loading.value = false;
  }
}

async function submitLdap(): Promise<void> {
  if (!ldapForm.providerCode || !ldapForm.username || !ldapForm.password) return;
  errorMessage.value = "";
  loading.value = true;
  try {
    const requiresSecondFactor = await authentication.loginWithLdap(
      ldapForm.providerCode,
      ldapForm.username,
      ldapForm.password,
      form.remember,
      scopeSelection.value
    );
    primaryMethod.value = "LDAP";
    ldapForm.password = "";
    if (!requiresSecondFactor) await navigateAfterLogin();
  } catch (error) {
    ldapForm.password = "";
    errorMessage.value = isApiError(error) ? error.message : t("login.externalIdentityFailed");
  } finally {
    loading.value = false;
  }
}

async function submitPassword(): Promise<void> {
  errorMessage.value = "";
  loading.value = true;
  try {
    const requiresSecondFactor = await authentication.loginWithPassword(
      {
        identifierType: form.identifierType,
        identifier: form.identifier,
        password: form.password
      },
      form.remember,
      scopeSelection.value
    );
    primaryMethod.value = "PASSWORD";
    form.password = "";
    if (!requiresSecondFactor) await navigateAfterLogin();
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : t("login.failed");
  } finally {
    loading.value = false;
  }
}

async function submitPasskeyLogin(): Promise<void> {
  if (!passkeyLoginAvailable.value) return;
  errorMessage.value = "";
  loading.value = true;
  try {
    const assertion = await authentication.startPasskeyLogin(form.remember, scopeSelection.value);
    // WebAuthn 库负责 options 和认证器响应中的 ArrayBuffer/Base64URL 转换。
    const credential = await getPasskey(assertion.options as CredentialRequestOptionsJSON);
    const requiresSecondFactor = await authentication.finishPasskeyLogin(assertion.assertionId, credential);
    primaryMethod.value = "PASSKEY";
    if (!requiresSecondFactor) {
      await navigateAfterLogin();
    }
  } catch (error) {
    authentication.cancelLogin();
    errorMessage.value = isApiError(error)
      ? error.message
      : error instanceof DOMException && error.name === "NotAllowedError"
        ? t("login.passkeyCancelled")
        : t("login.passkeyFailed");
  } finally {
    loading.value = false;
  }
}

function backToPassword(): void {
  authentication.cancelLogin();
  errorMessage.value = "";
  primaryMethod.value = null;
}

function startRecovery(): void {
  authentication.cancelLogin();
  form.password = "";
  mode.value = "RECOVERY";
  errorMessage.value = "";
}

function backToLogin(): void {
  mode.value = "LOGIN";
  errorMessage.value = "";
}
</script>

<template>
  <main class="login-page">
    <a-select
      :value="locale.locale"
      :loading="languageStore.loading"
      class="login-locale"
      :aria-label="t('app.language')"
      @change="selectLocale"
    >
      <template #suffixIcon>
        <Languages :size="16" />
      </template>
      <a-select-option
        v-for="language in languageStore.applicationLanguages"
        :key="language.code"
        :value="language.code"
      >
        {{ language.nativeName }}
      </a-select-option>
    </a-select>
    <section
      class="login-panel"
      :aria-labelledby="mode === 'RECOVERY' ? 'account-recovery-title' : 'login-title'"
    >
      <div class="login-brand">
        <div class="brand__mark">
          <ShieldCheck :size="25" />
        </div>
        <div><strong>SCA4D</strong><span>{{ t("login.brand") }}</span></div>
      </div>
      <div
        v-if="mode === 'LOGIN'"
        class="login-heading"
      >
        <h1 id="login-title">
          {{ pageTitle }}
        </h1>
        <p>
          {{ pageDescription }}
        </p>
      </div>
      <a-alert
        v-if="mode === 'LOGIN' && errorMessage"
        type="error"
        show-icon
        :message="errorMessage"
        class="login-error"
      />
      <a-form
        v-if="mode === 'LOGIN' && !verificationStep"
        layout="vertical"
        :model="form"
        @finish="submitPassword"
      >
        <a-form-item :label="t('login.accountScope')">
          <a-segmented
            :value="form.scopeType"
            block
            :disabled="loading || providerLoading"
            :options="[
              { value: 'GLOBAL', label: t('login.globalAccount') },
              { value: 'TENANT', label: t('login.tenantAccount') }
            ]"
            @change="selectAccountScope"
          />
        </a-form-item>
        <a-form-item
          v-if="form.scopeType === 'TENANT'"
          :label="t('login.tenantCode')"
          name="tenantCode"
          :rules="[{ required: true, message: t('login.tenantCodeRequired') }]"
        >
          <a-input-search
            :value="form.tenantCode"
            size="large"
            autocomplete="organization"
            :placeholder="t('login.tenantCodePlaceholder')"
            :enter-button="t('login.loadMethods')"
            :loading="providerLoading"
            :disabled="loading"
            @update:value="updateTenantCode"
            @search="loadProviders"
          />
        </a-form-item>
        <template v-if="oidcProviders.length > 0 || oauth2Providers.length > 0 || ldapProviders.length > 0">
          <div class="oidc-options">
            <a-button
              v-for="provider in oidcProviders"
              :key="`oidc-${provider.code}`"
              size="large"
              block
              html-type="button"
              :loading="loading || providerLoading"
              @click="submitOidc(provider.code)"
            >
              <LogIn :size="18" />{{ t("login.continueWith", { provider: provider.name }) }}
            </a-button>
            <a-button
              v-for="provider in oauth2Providers"
              :key="`oauth2-${provider.code}`"
              size="large"
              block
              html-type="button"
              :loading="loading || providerLoading"
              @click="submitOAuth2(provider.code)"
            >
              <LogIn :size="18" />{{ t("login.continueWith", { provider: provider.name }) }}
            </a-button>
          </div>
          <div
            v-if="ldapProviders.length > 0"
            class="ldap-options"
          >
            <a-select
              v-model:value="ldapForm.providerCode"
              size="large"
              :aria-label="t('login.directoryProvider')"
            >
              <a-select-option
                v-for="provider in ldapProviders"
                :key="provider.code"
                :value="provider.code"
              >
                {{ provider.name }}
              </a-select-option>
            </a-select>
            <a-input
              v-model:value="ldapForm.username"
              size="large"
              autocomplete="username"
              :placeholder="t('login.directoryUsername')"
            >
              <template #prefix>
                <UserRound :size="17" />
              </template>
            </a-input>
            <a-input-password
              v-model:value="ldapForm.password"
              size="large"
              autocomplete="current-password"
              :placeholder="t('login.directoryPassword')"
              @press-enter.prevent="submitLdap"
            >
              <template #prefix>
                <LockKeyhole :size="17" />
              </template>
            </a-input-password>
            <a-button
              size="large"
              block
              html-type="button"
              :loading="loading"
              :disabled="!ldapForm.providerCode || !ldapForm.username || !ldapForm.password"
              @click="submitLdap"
            >
              <LogIn :size="18" />{{ t("login.directorySignIn") }}
            </a-button>
          </div>
          <a-divider>{{ t("login.orLocalSignIn") }}</a-divider>
        </template>
        <template v-if="passkeyRegistered">
          <a-button
            size="large"
            block
            :loading="loading"
            :disabled="!passkeyLoginAvailable"
            @click="submitPasskeyLogin"
          >
            <Fingerprint :size="18" />{{ t("login.usePasskey") }}
          </a-button>
          <a-alert
            v-if="!webAuthnAvailable"
            type="warning"
            show-icon
            :message="t('login.passkeyUnavailable')"
            class="passkey-status"
          />
          <a-divider>{{ t("login.orPassword") }}</a-divider>
        </template>
        <a-form-item
          :label="t('login.accountType')"
        >
          <a-segmented
            v-model:value="form.identifierType"
            block
            :options="[
              { value: 'USERNAME', label: t('login.username') },
              { value: 'EMAIL', label: t('login.email') }
            ]"
          />
        </a-form-item>
        <a-form-item
          :label="t('login.account')"
          name="identifier"
          :rules="[{ required: true, message: t('login.accountRequired') }]"
        >
          <a-input
            v-model:value="form.identifier"
            size="large"
            autocomplete="username"
            :placeholder="t('login.accountPlaceholder')"
          >
            <template #prefix>
              <UserRound :size="17" />
            </template>
          </a-input>
        </a-form-item>
        <a-form-item
          :label="t('login.password')"
          name="password"
          :rules="[{ required: true, message: t('login.passwordRequired') }]"
        >
          <a-input-password
            v-model:value="form.password"
            size="large"
            autocomplete="current-password"
            :placeholder="t('login.passwordPlaceholder')"
          >
            <template #prefix>
              <LockKeyhole :size="17" />
            </template>
          </a-input-password>
        </a-form-item>
        <div class="login-options">
          <a-button
            v-if="recoveryAvailable"
            type="link"
            class="recovery-entry"
            :disabled="loading"
            @click="startRecovery"
          >
            {{ t("login.forgotPassword") }}
          </a-button>
          <a-checkbox v-model:checked="form.remember">
            {{ t("login.remember") }}
          </a-checkbox>
        </div>
        <a-button
          type="primary"
          html-type="submit"
          size="large"
          block
          :loading="loading"
        >
          {{ t("login.submit") }}
        </a-button>
      </a-form>
      <LoginSecondStepForm
        v-else-if="verificationStep"
        :methods="secondFactorMethods"
        :primary-method="primaryMethod"
        :loading="loading"
        @completed="navigateAfterLogin"
        @back="backToPassword"
        @error="errorMessage = $event"
      />
      <AccountRecoveryFlow
        v-else
        :scope="scopeSelection"
        :initial-identifier-type="form.identifierType"
        :initial-identifier="form.identifier"
        @back="backToLogin"
      />
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 28px 18px;
  background: var(--app-bg);
}

.login-locale {
  position: fixed;
  top: 20px;
  right: 20px;
  width: 144px;
}

.login-panel {
  width: min(400px, 100%);
  padding: 30px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-brand > div:last-child {
  display: flex;
  flex-direction: column;
}

.login-brand strong {
  font-size: 17px;
}

.login-brand span {
  margin-top: 2px;
  color: var(--app-text-secondary);
  font-size: 11px;
}

.login-heading {
  margin: 28px 0 22px;
}

.login-heading h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.login-heading p {
  margin: 7px 0 0;
  color: var(--app-text-secondary);
}

.login-error {
  margin-bottom: 18px;
}

.passkey-status {
  margin-top: 12px;
}

.oidc-options {
  display: grid;
  gap: 10px;
}

.ldap-options {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.login-options {
  display: flex;
  justify-content: space-between;
  margin: -4px 0 20px;
}

.recovery-entry {
  min-height: 40px;
  padding: 0;
}

@media (max-width: 520px) {
  .login-page {
    place-items: start center;
    padding-top: 56px;
  }

  .login-panel {
    padding: 24px 20px;
  }
}
</style>
