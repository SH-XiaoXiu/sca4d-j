<script setup lang="ts">
defineOptions({ name: "AccountSecurityPage" });

import {
  create as createPasskey,
  type CredentialCreationOptionsJSON
} from "@github/webauthn-json";
import {
  isApiError,
  type AccountIdentifierView,
  type AccountCredentialView,
  type AccountSecurityView,
  type AccountSessionView,
  type AuthMethod,
  type ExternalIdentityView,
  type IdentityProviderProtocol,
  type LdapAvailableProvider,
  type OAuth2AvailableProvider,
  type OidcAvailableProvider,
  type PasskeyCredentialView
} from "@sca4d/admin-common";
import { RefreshCw, UserRound } from "lucide-vue-next";
import { message } from "ant-design-vue";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import PageHeader from "@/components/page/PageHeader.vue";
import { accountSecurityApi, authenticationApi } from "@/api/client";
import { useAuthenticationStore } from "@/stores/authentication";
import { useSessionStore } from "@/stores/session";
import AccountSecurityDetails from "./components/AccountSecurityDetails.vue";
import SecondStepVerificationDialog, {
  type SecondStepMethod
} from "./components/SecondStepVerificationDialog.vue";
import ExternalIdentityLinkDialog, {
  type ExternalIdentityLinkRequest
} from "./components/ExternalIdentityLinkDialog.vue";
import PasskeyNameDialog from "./components/PasskeyNameDialog.vue";
import RecoveryCodesDialog from "./components/RecoveryCodesDialog.vue";
import TotpSetupDialog from "./components/TotpSetupDialog.vue";

type SecondStepAction =
  | { type: "session"; session: AccountSessionView }
  | { type: "second-factor"; required: boolean }
  | { type: "totp-enrollment"; credential: AccountCredentialView | null }
  | { type: "totp-delete"; credential: AccountCredentialView }
  | { type: "passkey-registration"; displayName: string }
  | { type: "passkey-delete"; credential: PasskeyCredentialView }
  | { type: "external-identity-delete"; identity: ExternalIdentityView };

const sessionStore = useSessionStore();
const authentication = useAuthenticationStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loading = ref(true);
const loadError = ref(false);
const accounts = ref<AccountSecurityView[]>([]);
const methods = ref<AuthMethod[]>([]);
const selectedAccountId = ref<string | null>(null);
const sessions = ref<AccountSessionView[]>([]);
const sessionsLoading = ref(false);
const sessionsError = ref(false);
const externalIdentities = ref<ExternalIdentityView[]>([]);
const externalIdentitiesLoading = ref(false);
const oidcProviders = ref<OidcAvailableProvider[]>([]);
const oauth2Providers = ref<OAuth2AvailableProvider[]>([]);
const ldapProviders = ref<LdapAvailableProvider[]>([]);
const externalLinkOpen = ref(false);
const externalLinkSubmitting = ref(false);
const externalLinkProtocol = ref<IdentityProviderProtocol>("OIDC");
const secondStepOpen = ref(false);
const secondStepSubmitting = ref(false);
const secondStepAction = ref<SecondStepAction | null>(null);
const recoveryCodesOpen = ref(false);
const issuedRecoveryCodes = ref<string[]>([]);
const recoveryCodeAccountTitle = ref("");
const totpSetupOpen = ref(false);
const totpSubmitting = ref(false);
const totpEnrollment = ref<{ enrollmentId: string; credential: AccountCredentialView | null } | null>(null);
const totpQrUrl = ref<string | null>(null);
const passkeyNameOpen = ref(false);
const webAuthnAvailable = Boolean(window.PublicKeyCredential && navigator.credentials);
let sessionsRequest = 0;
let externalIdentityRequest = 0;

const selectedAccount = computed(
  () => accounts.value.find((account) => account.accountId === selectedAccountId.value) ?? null
);
const authenticatedAccount = computed(
  () => accounts.value.find((account) => account.accountId === sessionStore.accountId) ?? null
);
const externalProviderOptions = computed(() => {
  const linked = new Set(externalIdentities.value.map((identity) => identity.providerCode));
  if (externalLinkProtocol.value === "OIDC") {
    return oidcProviders.value.filter((provider) => !linked.has(provider.code));
  }
  if (externalLinkProtocol.value === "OAUTH2") {
    return oauth2Providers.value.filter((provider) => !linked.has(provider.code));
  }
  return ldapProviders.value.filter((provider) => !linked.has(provider.code));
});
const changingSecondFactor = computed(() => secondStepAction.value?.type === "second-factor");
const enablingSecondFactor = computed(
  () => secondStepAction.value?.type === "second-factor" && secondStepAction.value.required
);
const secondStepTitle = computed(() =>
  changingSecondFactor.value
    ? t("accountSecurity.secondFactorTitle")
    : secondStepAction.value?.type === "totp-enrollment"
      ? t("accountSecurity.totpVerifyTitle")
      : secondStepAction.value?.type === "totp-delete"
        ? t("accountSecurity.totpDeleteTitle")
        : secondStepAction.value?.type === "passkey-registration"
      ? t("accountSecurity.passkeyVerifyTitle")
      : secondStepAction.value?.type === "passkey-delete"
        ? t("accountSecurity.passkeyDeleteTitle")
        : secondStepAction.value?.type === "external-identity-delete"
          ? t("accountSecurity.externalIdentityDeleteTitle")
        : t("accountSecurity.endTitle")
);
const secondStepImpact = computed(() => {
  const action = secondStepAction.value;
  if (action?.type === "session") {
    return t("accountSecurity.endConfirm", {
      terminal: action.session.terminalName || t("accountSecurity.unknownTerminal")
    });
  }
  if (action?.type === "totp-enrollment") {
    return action.credential
      ? t("accountSecurity.totpReplaceConfirm")
      : t("accountSecurity.totpSetupConfirm");
  }
  if (action?.type === "totp-delete") return t("accountSecurity.totpDeleteConfirm");
  if (action?.type === "passkey-registration") return t("accountSecurity.passkeyRegistrationConfirm");
  if (action?.type === "passkey-delete") {
    return t("accountSecurity.passkeyDeleteConfirm", { name: action.credential.displayName });
  }
  if (action?.type === "external-identity-delete") {
    return t("accountSecurity.externalIdentityDeleteConfirm", { name: action.identity.providerName });
  }
  if (!action?.required) return t("accountSecurity.disableSecondFactorConfirm");
  return selectedAccount.value?.secondFactorRequired
    ? t("accountSecurity.regenerateRecoveryCodesConfirm")
    : t("accountSecurity.enableSecondFactorConfirm");
});
const secondStepSubmitLabel = computed(() => {
  if (secondStepAction.value?.type === "totp-enrollment") return t("accountSecurity.verifyAndContinue");
  if (secondStepAction.value?.type === "totp-delete") return t("accountSecurity.verifyAndDelete");
  if (secondStepAction.value?.type === "passkey-registration") return t("accountSecurity.verifyAndContinue");
  if (secondStepAction.value?.type === "passkey-delete") return t("accountSecurity.verifyAndDelete");
  if (secondStepAction.value?.type === "external-identity-delete") return t("accountSecurity.verifyAndDelete");
  if (!changingSecondFactor.value) return t("accountSecurity.verifyAndEnd");
  if (!enablingSecondFactor.value) return t("accountSecurity.verifyAndDisable");
  return selectedAccount.value?.secondFactorRequired
    ? t("accountSecurity.verifyAndRegenerate")
    : t("accountSecurity.verifyAndEnable");
});
const secondStepDanger = computed(
  () =>
    secondStepAction.value?.type === "session" ||
    secondStepAction.value?.type === "totp-delete" ||
    secondStepAction.value?.type === "passkey-delete" ||
    secondStepAction.value?.type === "external-identity-delete" ||
    (changingSecondFactor.value && !enablingSecondFactor.value)
);
const activePasswordIdentifier = computed<AccountIdentifierView | null>(() => {
  const identifiers = authenticatedAccount.value?.identifiers ?? [];
  return (
    identifiers.find(
      (identifier) => identifier.status === "ACTIVE" && identifier.identifierType === "USERNAME"
    ) ??
    identifiers.find(
      (identifier) => identifier.status === "ACTIVE" && identifier.identifierType === "EMAIL"
    ) ??
    null
  );
});
const availableSecondStepMethods = computed<SecondStepMethod[]>(() => {
  const registered = new Set(
    methods.value.filter((method) => method.capabilities.includes("STEP_UP")).map((method) => method.type)
  );
  const available: SecondStepMethod[] = [];
  const passwordActive = authenticatedAccount.value?.credentials.some(
    (credential) => credential.credentialType === "PASSWORD" && credential.status === "ACTIVE"
  );
  if (registered.has("PASSWORD") && passwordActive && activePasswordIdentifier.value) {
    available.push("PASSWORD");
  }
  const emailActive = authenticatedAccount.value?.identifiers.some(
    (identifier) => identifier.identifierType === "EMAIL" && identifier.status === "ACTIVE"
  );
  if (registered.has("EMAIL_CODE") && emailActive) {
    available.push("EMAIL_CODE");
  }
  const totpActive = authenticatedAccount.value?.credentials.some(
    (credential) => credential.credentialType === "TOTP" && credential.status === "ACTIVE"
  );
  if (registered.has("TOTP") && totpActive) available.push("TOTP");
  const passkeyActive = authenticatedAccount.value?.passkeyCredentials.some(
    (credential) => credential.status === "ACTIVE"
  );
  if (registered.has("PASSKEY") && passkeyActive && webAuthnAvailable) available.push("PASSKEY");
  return available;
});

function accountTitle(account: AccountSecurityView): string {
  return account.scopeType === "GLOBAL"
    ? t("accountSecurity.globalAccount")
    : t("accountSecurity.tenantAccount", { tenantId: account.scopeId ?? t("common.unavailable") });
}

async function loadAccounts(): Promise<void> {
  loading.value = true;
  loadError.value = false;
  try {
    [accounts.value, methods.value] = await Promise.all([
      accountSecurityApi.accounts(),
      authenticationApi.methods()
    ]);
    const preferred =
      accounts.value.find((account) => account.accountId === sessionStore.accountId) ?? accounts.value[0];
    selectedAccountId.value = preferred?.accountId ?? null;
    if (preferred) {
      await Promise.all([loadSessions(preferred.accountId), loadExternalIdentityState(preferred)]);
    }
  } catch (error) {
    loadError.value = true;
    message.error(isApiError(error) ? error.message : t("accountSecurity.loadFailed"));
  } finally {
    loading.value = false;
  }
}

async function selectAccount(accountId: string): Promise<void> {
  if (selectedAccountId.value === accountId) return;
  selectedAccountId.value = accountId;
  const account = accounts.value.find((item) => item.accountId === accountId);
  if (account) await Promise.all([loadSessions(accountId), loadExternalIdentityState(account)]);
}

async function loadSessions(accountId = selectedAccountId.value): Promise<void> {
  if (!accountId) return;
  const requestId = ++sessionsRequest;
  sessionsLoading.value = true;
  sessionsError.value = false;
  try {
    const result = await accountSecurityApi.sessions(accountId);
    // 切换账号后丢弃较早请求的结果，避免慢响应覆盖当前账号的登录会话。
    if (requestId === sessionsRequest && selectedAccountId.value === accountId) sessions.value = result;
  } catch (error) {
    if (requestId === sessionsRequest) {
      sessionsError.value = true;
      message.error(isApiError(error) ? error.message : t("accountSecurity.sessionsFailed"));
    }
  } finally {
    if (requestId === sessionsRequest) sessionsLoading.value = false;
  }
}

async function loadExternalIdentityState(account = selectedAccount.value): Promise<void> {
  if (!account) return;
  const requestId = ++externalIdentityRequest;
  externalIdentitiesLoading.value = true;
  const results = await Promise.allSettled([
    accountSecurityApi.externalIdentities(account.accountId),
    accountSecurityApi.availableIdentityProviders(account.accountId)
  ]);
  if (requestId !== externalIdentityRequest || selectedAccountId.value !== account.accountId) return;
  externalIdentities.value = results[0].status === "fulfilled" ? results[0].value : [];
  const availableProviders = results[1].status === "fulfilled" ? results[1].value : [];
  oidcProviders.value = availableProviders.filter((provider) => provider.protocol === "OIDC");
  oauth2Providers.value = availableProviders.filter((provider) => provider.protocol === "OAUTH2");
  ldapProviders.value = availableProviders.filter((provider) => provider.protocol === "LDAP");
  if (requestId === externalIdentityRequest) externalIdentitiesLoading.value = false;
}

function openExternalIdentityLink(protocol: IdentityProviderProtocol): void {
  externalLinkProtocol.value = protocol;
  externalLinkOpen.value = true;
}

async function linkExternalIdentity(request: ExternalIdentityLinkRequest): Promise<void> {
  const account = selectedAccount.value;
  if (!account || account.accountId !== sessionStore.accountId || !request.providerCode) return;
  externalLinkSubmitting.value = true;
  try {
    if (request.protocol === "LDAP") {
      if (!request.username || !request.password) return;
      await authentication.linkLdap(request.providerCode, request.username, request.password);
      externalLinkOpen.value = false;
      await loadExternalIdentityState(account);
      message.success(t("accountSecurity.externalIdentityLinked"));
      return;
    }
    const redirect = route.fullPath;
    const authorizationUri = request.protocol === "OIDC"
      ? await authentication.startOidcLink(request.providerCode, redirect)
      : await authentication.startOAuth2Link(request.providerCode, redirect);
    window.location.assign(authorizationUri);
  } catch (error) {
    message.error(isApiError(error) ? error.message : t("accountSecurity.externalIdentityLinkFailed"));
  } finally {
    externalLinkSubmitting.value = false;
  }
}

function openSessionSecondStep(session: AccountSessionView): void {
  requestSecondStep({ type: "session", session });
}

function openSecondFactorSecondStep(required: boolean): void {
  if (!selectedAccount.value) return;
  requestSecondStep({ type: "second-factor", required });
}

function openTotpEnrollment(credential: AccountCredentialView | null): void {
  requestSecondStep({ type: "totp-enrollment", credential });
}

function openTotpDelete(credential: AccountCredentialView): void {
  requestSecondStep({ type: "totp-delete", credential });
}

function openPasskeyName(): void {
  if (!webAuthnAvailable) return;
  passkeyNameOpen.value = true;
}

function continuePasskeyRegistration(displayName: string): void {
  passkeyNameOpen.value = false;
  requestSecondStep({ type: "passkey-registration", displayName });
}

function openPasskeyDelete(credential: PasskeyCredentialView): void {
  requestSecondStep({ type: "passkey-delete", credential });
}

function openExternalIdentityDelete(identity: ExternalIdentityView): void {
  requestSecondStep({ type: "external-identity-delete", identity });
}

function requestSecondStep(action: SecondStepAction): void {
  secondStepAction.value = action;
  const authorizationId = authentication.reusableStepUp(sessionStore.accountId);
  if (authorizationId) {
    void completeSecondStepAction(authorizationId);
    return;
  }
  secondStepOpen.value = true;
}

async function completeSecondStepAction(authenticationId: string): Promise<void> {
  const account = selectedAccount.value;
  const action = secondStepAction.value;
  if (!account || !action) return;
  // 安全材料写入后 Client-State 刷新可能先清空 Store；请求前固定目标是否为当前 Session Account。
  const endsCurrentSession = account.accountId === sessionStore.accountId;
  secondStepSubmitting.value = true;
  try {
    if (action.type === "session") {
      // 后端单次消费本次二步验证后才结束目标登录会话。
      await accountSecurityApi.endSession(account.accountId, action.session.sessionId, authenticationId);
      const endedCurrent = action.session.sessionId === sessionStore.session?.sessionId;
      message.success(t("accountSecurity.sessionEnded"));
      if (endedCurrent) {
        await sessionStore.leaveInvalidSession();
        return;
      }
      secondStepOpen.value = false;
      secondStepAction.value = null;
      await loadSessions(account.accountId);
      return;
    }
    if (action.type === "totp-enrollment") {
      const enrollment = await accountSecurityApi.createTotpEnrollment(account.accountId, authenticationId);
      const qrCode = await accountSecurityApi.totpEnrollmentQrCode(
        account.accountId,
        enrollment.enrollmentId
      );
      if (totpQrUrl.value) URL.revokeObjectURL(totpQrUrl.value);
      totpQrUrl.value = URL.createObjectURL(qrCode);
      totpEnrollment.value = { enrollmentId: enrollment.enrollmentId, credential: action.credential };
      secondStepOpen.value = false;
      secondStepAction.value = null;
      totpSetupOpen.value = true;
      return;
    }
    if (action.type === "totp-delete") {
      await accountSecurityApi.deleteTotpCredential(
        account.accountId,
        action.credential.credentialId,
        action.credential.version,
        authenticationId
      );
      account.credentials = account.credentials.filter(
        (credential) => credential.credentialId !== action.credential.credentialId
      );
      secondStepOpen.value = false;
      secondStepAction.value = null;
      message.success(t("accountSecurity.totpDeleted"));
      return;
    }
    if (action.type === "passkey-registration") {
      const registration = await accountSecurityApi.createPasskeyRegistration(
        account.accountId,
        { displayName: action.displayName },
        authenticationId
      );
      // 私钥始终留在认证器；页面只把库返回的 attestation JSON 提交给当前 registration。
      const credential = await createPasskey(
        registration.options as CredentialCreationOptionsJSON
      );
      await accountSecurityApi.activatePasskeyCredential(account.accountId, {
        registrationId: registration.registrationId,
        credential
      });
      secondStepOpen.value = false;
      secondStepAction.value = null;
      sessions.value = [];
      message.success(t("accountSecurity.passkeyRegistered"));
      if (endsCurrentSession) {
        await sessionStore.leaveInvalidSession();
      } else {
        await loadAccounts();
      }
      return;
    }
    if (action.type === "passkey-delete") {
      let version = action.credential.version;
      // 二步验证使用 Passkey 时会推进该凭证版本；删除前始终重新读取最新 ETag。
      const refreshed = await accountSecurityApi.accounts();
      accounts.value = refreshed;
      const current = refreshed
        .find((item) => item.accountId === account.accountId)
        ?.passkeyCredentials.find(
          (credential) => credential.credentialId === action.credential.credentialId
        );
      if (!current) throw new Error("Passkey no longer exists");
      version = current.version;
      await accountSecurityApi.deletePasskeyCredential(
        account.accountId,
        action.credential.credentialId,
        version,
        authenticationId
      );
      secondStepOpen.value = false;
      secondStepAction.value = null;
      sessions.value = [];
      message.success(t("accountSecurity.passkeyDeleted"));
      if (endsCurrentSession) {
        await sessionStore.leaveInvalidSession();
      } else {
        await loadAccounts();
      }
      return;
    }
    if (action.type === "external-identity-delete") {
      await accountSecurityApi.deleteExternalIdentity(
        account.accountId,
        action.identity.identityId,
        action.identity.version,
        authenticationId
      );
      externalIdentities.value = externalIdentities.value.filter(
        (identity) => identity.identityId !== action.identity.identityId
      );
      sessions.value = [];
      secondStepOpen.value = false;
      secondStepAction.value = null;
      message.success(t("accountSecurity.externalIdentityDeleted"));
      if (endsCurrentSession) {
        await sessionStore.leaveInvalidSession();
      } else {
        await loadSessions(account.accountId);
      }
      return;
    }

    const result = await accountSecurityApi.changeSecondFactorRequirement(
      account.accountId,
      { required: action.required, version: account.version },
      authenticationId
    );
    account.secondFactorRequired = result.required;
    account.version = result.accountVersion;
    account.recoveryCodesIssuedAt = result.recoveryCodesIssuedAt;
    account.recoveryCodesRemaining = result.recoveryCodesRemaining;
    secondStepOpen.value = false;
    secondStepAction.value = null;
    if (result.recoveryCodes.length) {
      issuedRecoveryCodes.value = [...result.recoveryCodes];
      recoveryCodeAccountTitle.value = accountTitle(account);
      recoveryCodesOpen.value = true;
      message.success(t("accountSecurity.secondFactorEnabled"));
      return;
    }
    message.success(t("accountSecurity.secondFactorDisabled"));
  } catch (error) {
    authentication.forgetStepUp(authenticationId);
    const fallback = changingSecondFactor.value
      ? t("accountSecurity.secondFactorChangeFailed")
      : t("accountSecurity.endFailed");
    message.error(isApiError(error) ? error.message : fallback);
  } finally {
    secondStepSubmitting.value = false;
  }
}

function closeTotpSetup(): void {
  if (totpSubmitting.value) return;
  totpSetupOpen.value = false;
  totpEnrollment.value = null;
  if (totpQrUrl.value) URL.revokeObjectURL(totpQrUrl.value);
  totpQrUrl.value = null;
}

async function activateTotp(code: string): Promise<void> {
  const account = selectedAccount.value;
  const enrollment = totpEnrollment.value;
  if (!account || !enrollment || !code) return;
  totpSubmitting.value = true;
  try {
    // 后端只在验证码正确后永久消费 enrollment，再写入 Credential；成功后使用响应版本更新当前页面。
    const changed = enrollment.credential
      ? await accountSecurityApi.replaceTotpCredential(
          account.accountId,
          enrollment.credential.credentialId,
          enrollment.credential.version,
          { enrollmentId: enrollment.enrollmentId, code }
        )
      : await accountSecurityApi.activateTotpCredential(account.accountId, {
          enrollmentId: enrollment.enrollmentId,
          code
        });
    if (enrollment.credential) {
      enrollment.credential.version = changed.version;
      enrollment.credential.status = "ACTIVE";
      enrollment.credential.updatedAt = new Date().toISOString();
    } else {
      account.credentials.push({
        credentialId: changed.credentialId,
        credentialType: "TOTP",
        status: "ACTIVE",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        version: changed.version
      });
    }
    message.success(enrollment.credential ? t("accountSecurity.totpReplaced") : t("accountSecurity.totpEnabled"));
    totpSubmitting.value = false;
    closeTotpSetup();
  } catch (error) {
    message.error(isApiError(error) ? error.message : t("accountSecurity.totpActivationFailed"));
    const canRetryCurrentEnrollment =
      isApiError(error) && (error.code === 2024 || error.httpStatus === 429);
    if (!canRetryCurrentEnrollment) {
      // 版本冲突、数据库失败或响应丢失时 enrollment 可能已经消费；关闭旧二维码并重新读取服务端结果。
      totpSubmitting.value = false;
      closeTotpSetup();
      await loadAccounts();
    }
  } finally {
    totpSubmitting.value = false;
  }
}

async function closeRecoveryCodes(): Promise<void> {
  recoveryCodesOpen.value = false;
  issuedRecoveryCodes.value = [];
  recoveryCodeAccountTitle.value = "";
}

onMounted(async () => {
  await loadAccounts();
  if (route.query.identityLinked) {
    message.success(t("accountSecurity.externalIdentityLinked"));
  } else if (route.query.identityLinkFailed) {
    message.error(t("accountSecurity.externalIdentityLinkFailed"));
  }
  if (route.query.identityLinked || route.query.identityLinkFailed) {
    const query = { ...route.query };
    delete query.identityLinked;
    delete query.identityLinkFailed;
    await router.replace({ query });
  }
});
onUnmounted(() => {
  if (totpQrUrl.value) URL.revokeObjectURL(totpQrUrl.value);
});
</script>

<template>
  <div>
    <PageHeader :title="t('route.accountSecurity')">
      <template #actions>
        <a-tooltip :title="t('common.refresh')">
          <a-button
            :aria-label="t('common.refresh')"
            :loading="loading"
            @click="loadAccounts"
          >
            <RefreshCw :size="16" />
          </a-button>
        </a-tooltip>
      </template>
    </PageHeader>

    <a-skeleton
      v-if="loading && !accounts.length"
      active
      :paragraph="{ rows: 8 }"
    />
    <a-result
      v-else-if="loadError"
      status="error"
      :title="t('accountSecurity.loadFailed')"
    >
      <template #extra>
        <a-button
          type="primary"
          @click="loadAccounts"
        >
          <RefreshCw :size="16" />{{ t("common.refresh") }}
        </a-button>
      </template>
    </a-result>
    <a-empty
      v-else-if="!accounts.length"
      :description="t('accountSecurity.accountsEmpty')"
    />
    <div
      v-else
      class="account-security"
    >
      <aside
        class="account-list"
        :aria-label="t('accountSecurity.accounts')"
      >
        <button
          v-for="account in accounts"
          :key="account.accountId"
          type="button"
          class="account-option"
          :class="{ 'account-option--selected': account.accountId === selectedAccountId }"
          :aria-pressed="account.accountId === selectedAccountId"
          @click="selectAccount(account.accountId)"
        >
          <span class="account-option__icon"><UserRound :size="18" /></span>
          <span class="account-option__body">
            <strong>{{ accountTitle(account) }}</strong>
            <span>{{ account.identifiers[0]?.value || t("accountSecurity.noIdentifier") }}</span>
          </span>
          <a-tag :color="account.status === 'ACTIVE' ? 'success' : 'default'">
            {{ account.status === "ACTIVE" ? t("common.active") : t("common.disabled") }}
          </a-tag>
        </button>
      </aside>

      <AccountSecurityDetails
        v-if="selectedAccount"
        :account="selectedAccount"
        :account-title="accountTitle(selectedAccount)"
        :current-account-id="sessionStore.accountId"
        :current-session-id="sessionStore.session?.sessionId ?? null"
        :second-step-available="availableSecondStepMethods.length > 0"
        :web-authn-available="webAuthnAvailable"
        :sessions="sessions"
        :sessions-loading="sessionsLoading"
        :sessions-error="sessionsError"
        :external-identities="externalIdentities"
        :external-identities-loading="externalIdentitiesLoading"
        :oidc-providers="oidcProviders"
        :oauth2-providers="oauth2Providers"
        :ldap-providers="ldapProviders"
        @add-passkey="openPasskeyName"
        @setup-totp="openTotpEnrollment"
        @delete-totp="openTotpDelete"
        @delete-passkey="openPasskeyDelete"
        @link-identity="openExternalIdentityLink"
        @unlink-identity="openExternalIdentityDelete"
        @change-second-factor="openSecondFactorSecondStep"
        @refresh-sessions="loadSessions()"
        @end-session="openSessionSecondStep"
      />
    </div>

    <ExternalIdentityLinkDialog
      v-model:open="externalLinkOpen"
      :protocol="externalLinkProtocol"
      :providers="externalProviderOptions"
      :submitting="externalLinkSubmitting"
      @submit="linkExternalIdentity"
    />

    <PasskeyNameDialog
      v-model:open="passkeyNameOpen"
      @continue="continuePasskeyRegistration"
    />

    <SecondStepVerificationDialog
      v-model:open="secondStepOpen"
      :methods="availableSecondStepMethods"
      :password-identifier="activePasswordIdentifier"
      :title="secondStepTitle"
      :impact="secondStepImpact"
      :submit-label="secondStepSubmitLabel"
      :danger="secondStepDanger"
      :submitting="secondStepSubmitting"
      @verified="completeSecondStepAction"
    />

    <TotpSetupDialog
      :open="totpSetupOpen"
      :replacing="Boolean(totpEnrollment?.credential)"
      :qr-url="totpQrUrl"
      :submitting="totpSubmitting"
      @close="closeTotpSetup"
      @activate="activateTotp"
    />

    <RecoveryCodesDialog
      :open="recoveryCodesOpen"
      :account-title="recoveryCodeAccountTitle"
      :codes="issuedRecoveryCodes"
      @done="closeRecoveryCodes"
    />
  </div>
</template>

<style scoped>
.account-security {
  display: grid;
  grid-template-columns: minmax(240px, 300px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.account-list {
  display: grid;
  gap: 8px;
}

.account-option {
  display: grid;
  width: 100%;
  min-height: 68px;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  color: var(--app-text);
  text-align: left;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  cursor: pointer;
}

.account-option:hover,
.account-option:focus-visible {
  border-color: var(--app-primary);
}

.account-option--selected {
  background: var(--app-primary-soft);
  border-color: var(--app-primary);
}

.account-option__icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  color: var(--app-primary);
  background: var(--app-primary-soft);
  border-radius: var(--app-radius);
}

.account-option__body {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.account-option__body span {
  overflow: hidden;
  color: var(--app-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .account-security {
    grid-template-columns: minmax(0, 1fr);
  }

  .account-list {
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  }
}

@media (max-width: 560px) {
  .account-list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
