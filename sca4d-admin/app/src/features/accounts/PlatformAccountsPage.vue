<script setup lang="ts">
defineOptions({ name: "PlatformAccountsPage" });

import {
  createIdempotencyKey,
  formatDateTime,
  isApiError,
  normalizePageData,
  type AccountIdentifierView,
  type AccountSecurityView,
  type AccountSessionView,
  type AccountStatus,
  type AuthMethod,
  type ManagedAccountProfileView,
  type ManagedAccountView
} from "@sca4d/admin-common";
import {
  CirclePlus,
  Filter,
  KeyRound,
  LogOut,
  Pencil,
  Power,
  RotateCcw,
  Search,
  ShieldCheck,
  Trash2
} from "lucide-vue-next";
import { message, Modal, type FormInstance } from "ant-design-vue";
import { computed, onBeforeUnmount, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter, type LocationQuery } from "vue-router";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import AvatarCropper from "@/components/avatar/AvatarCropper.vue";
import DefaultProfileAvatar from "@/components/data-display/DefaultProfileAvatar.vue";
import PageHeader from "@/components/page/PageHeader.vue";
import QueryToolbar from "@/components/page/QueryToolbar.vue";
import {
  accountSecurityApi,
  authenticationApi,
  platformAccountApi,
  platformTenantAccountApi,
  tenantAccountManagementApi
} from "@/api/client";
import { routePositiveInteger, routeText, useRouteQueryState } from "@/composables/useRouteQueryState";
import { useSessionStore } from "@/stores/session";
import { useAuthenticationStore } from "@/stores/authentication";
import { useFileStore } from "@/stores/files";
import RecoveryCodesDialog from "../account/components/RecoveryCodesDialog.vue";
import SecondStepVerificationDialog, {
  type SecondStepMethod
} from "../account/components/SecondStepVerificationDialog.vue";

interface AccountListQuery {
  page: number;
  size: number;
  search: string;
  status: AccountStatus | undefined;
}

type SecurityAction =
  | { type: "mfa"; required: boolean }
  | { type: "recovery" }
  | { type: "password"; credentialId: string | null }
  | { type: "session"; sessionId: string }
  | { type: "sessions" }
  | { type: "profile" };

const auth = useSessionStore();
const authentication = useAuthenticationStore();
const files = useFileStore();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loading = ref(false);
const submitting = ref(false);
const filterOpen = ref(false);
const createOpen = ref(false);
const securityOpen = ref(false);
const securityLoading = ref(false);
const detailTab = ref("profile");
const records = ref<ManagedAccountView[]>([]);
const avatarUrls = ref(new Map<string, string>());
const total = ref(0);
const selectedAccount = ref<ManagedAccountView | null>(null);
const security = ref<AccountSecurityView | null>(null);
const sessions = ref<AccountSessionView[]>([]);
const profile = ref<ManagedAccountProfileView | null>(null);
const profileAvatarUrl = ref<string | null>(null);
const profileOpen = ref(false);
const profileForm = reactive({ displayName: "", phone: "", email: "" });
const currentAccountSecurity = ref<AccountSecurityView | null>(null);
const authenticationMethods = ref<AuthMethod[]>([]);
const secondStepOpen = ref(false);
const secondStepSubmitting = ref(false);
const securityAction = ref<SecurityAction | null>(null);
const passwordOpen = ref(false);
const passwordForm = reactive({ identifier: "", password: "", confirmPassword: "" });
const recoveryCodesOpen = ref(false);
const issuedRecoveryCodes = ref<string[]>([]);
const recoveryCodeAccountTitle = ref("");
const createFormRef = ref<FormInstance>();
const createForm = reactive({ identifier: "", password: "", displayName: "", phone: "", email: "" });
let listController: AbortController | null = null;
let securityController: AbortController | null = null;

const columns = computed(() => [
  { title: t("accountManagement.account"), key: "account", width: 300 },
  { title: t("common.status"), key: "status", width: 110 },
  { title: t("accountManagement.loginProtection"), key: "security", width: 180 },
  { title: t("common.updatedAt"), key: "updatedAt", width: 180 },
  { title: t("common.actions"), key: "actions", width: 180, fixed: "right" as const }
]);
const canReadSecurity = computed(() => auth.hasPermission("account.security.read"));
const canReadSessions = computed(() => auth.hasPermission("account.sessions.read"));
const canManageMfa = computed(() => auth.hasPermission("account.mfa.reset"));
const canManageRecoveryCodes = computed(() => auth.hasPermission("account.recovery.manage"));
const canManageCredentials = computed(() => auth.hasPermission("account.credentials.manage"));
const canRevokeSessions = computed(() => auth.hasPermission("account.sessions.revoke"));
const tenantId = computed(() => typeof route.params.tenantId === "string" ? route.params.tenantId : null);
const tenantScope = computed(() => tenantId.value !== null);
const currentTenantScope = computed(() => route.name === "tenant-accounts" || route.name === "tenant-account-detail");
const detailRouteName = computed(() => tenantScope.value
  ? (currentTenantScope.value ? "tenant-account-detail" : "platform-tenant-account-detail")
  : "platform-account-detail");
const listRouteName = computed(() => tenantScope.value
  ? (currentTenantScope.value ? "tenant-accounts" : "platform-tenant-accounts")
  : "platform-accounts");
const pageTitle = computed(() => tenantScope.value ? t("route.tenantAccounts") : t("accountManagement.title"));
const canCreate = computed(() => !tenantScope.value && auth.hasPermission("platform:accounts:create"));
const canEditGlobal = computed(() => !tenantScope.value && auth.hasPermission("platform:accounts:update"));
const canDeleteGlobal = computed(() => !tenantScope.value && auth.hasPermission("platform:accounts:delete"));
const needsStepUpContext = computed(() =>
  canManageMfa.value || canManageRecoveryCodes.value || canManageCredentials.value || canRevokeSessions.value
    || canEditGlobal.value
);
const activePasswordIdentifier = computed<AccountIdentifierView | null>(() => {
  const identifiers = currentAccountSecurity.value?.identifiers ?? [];
  return identifiers.find((item) => item.status === "ACTIVE" && item.identifierType === "USERNAME")
    ?? identifiers.find((item) => item.status === "ACTIVE" && item.identifierType === "EMAIL")
    ?? null;
});
const availableSecondStepMethods = computed<SecondStepMethod[]>(() => {
  const registered = new Set(
    authenticationMethods.value
      .filter((method) => method.capabilities.includes("STEP_UP"))
      .map((method) => method.type)
  );
  const available: SecondStepMethod[] = [];
  const current = currentAccountSecurity.value;
  if (registered.has("PASSWORD") && activePasswordIdentifier.value
    && current?.credentials.some((credential) => credential.credentialType === "PASSWORD" && credential.status === "ACTIVE")) {
    available.push("PASSWORD");
  }
  if (registered.has("EMAIL_CODE")
    && current?.identifiers.some((identifier) => identifier.identifierType === "EMAIL" && identifier.status === "ACTIVE")) {
    available.push("EMAIL_CODE");
  }
  if (registered.has("TOTP")
    && current?.credentials.some((credential) => credential.credentialType === "TOTP" && credential.status === "ACTIVE")) {
    available.push("TOTP");
  }
  if (registered.has("PASSKEY") && current?.passkeyCredentials.some((credential) => credential.status === "ACTIVE")
    && Boolean(window.PublicKeyCredential && navigator.credentials)) {
    available.push("PASSKEY");
  }
  return available;
});
const passwordCredential = computed(() =>
  security.value?.credentials.find((credential) => credential.credentialType === "PASSWORD" && credential.status === "ACTIVE") ?? null
);
const secondStepTitle = computed(() => {
  if (securityAction.value?.type === "session" || securityAction.value?.type === "sessions") {
    return t(securityAction.value.type === "sessions" ? "accountManagement.revokeAllSessions" : "accountManagement.revokeSession");
  }
  if (securityAction.value?.type === "profile") return t("accountManagement.editProfile");
  if (securityAction.value?.type === "password") return t("accountManagement.passwordAction");
  if (securityAction.value?.type === "recovery") return t("accountManagement.regenerateRecoveryCodes");
  return t("accountManagement.secondFactorAction");
});
const secondStepImpact = computed(() => {
  if (securityAction.value?.type === "session") return t("accountManagement.revokeSessionImpact");
  if (securityAction.value?.type === "sessions") return t("accountManagement.revokeAllSessionsImpact");
  if (securityAction.value?.type === "profile") return t("accountManagement.profileImpact");
  if (securityAction.value?.type === "password") return t("accountManagement.passwordImpact");
  if (securityAction.value?.type === "recovery") return t("accountManagement.regenerateRecoveryCodesImpact");
  return securityAction.value?.required
    ? t("accountManagement.requireSecondFactorImpact")
    : t("accountManagement.removeSecondFactorImpact");
});
const secondStepDanger = computed(() =>
  securityAction.value?.type === "session"
    || securityAction.value?.type === "sessions"
    || securityAction.value?.type === "password"
    || securityAction.value?.type === "recovery"
    || (securityAction.value?.type === "mfa" && !securityAction.value.required)
);
const queryState = useRouteQueryState<AccountListQuery>({
  decode(value: LocationQuery) {
    const status = routeText(value, "status");
    return {
      page: routePositiveInteger(value, "page", 1),
      size: routePositiveInteger(value, "size", 20),
      search: routeText(value, "search") ?? "",
      status: status === "ACTIVE" || status === "DISABLED" ? status : undefined
    };
  },
  encode(value) {
    return {
      ...(value.page === 1 ? {} : { page: String(value.page) }),
      ...(value.size === 20 ? {} : { size: String(value.size) }),
      ...(value.search ? { search: value.search } : {}),
      ...(value.status ? { status: value.status } : {})
    };
  }
}, load);
const query = queryState.draft;

function errorMessage(error: unknown, fallback: string): string {
  return isApiError(error) ? error.message : t(fallback);
}

function statusColor(status: AccountStatus): "success" | "default" {
  return status === "ACTIVE" ? "success" : "default";
}

function statusLabel(status: AccountStatus): string {
  return status === "ACTIVE" ? t("common.active") : t("common.disabled");
}

function avatarUrl(account: ManagedAccountView): string | null {
  return avatarUrls.value.get(String(account.accountId)) ?? null;
}

function identifierTypeLabel(type: AccountIdentifierView["identifierType"]): string {
  return type === "EMAIL" ? t("accountManagement.emailIdentifier") : t("accountManagement.username");
}

function credentialTypeLabel(type: AccountSecurityView["credentials"][number]["credentialType"]): string {
  if (type === "TOTP") return t("accountManagement.authenticator");
  if (type === "PASSKEY") return t("accountManagement.passkey");
  return t("accountManagement.password");
}

async function load(value: Readonly<AccountListQuery>): Promise<void> {
  listController?.abort();
  const requestController = new AbortController();
  listController = requestController;
  loading.value = true;
  try {
    const pageQuery = {
      page: value.page,
      size: value.size,
      ...(value.search ? { search: value.search } : {}),
      ...(value.status ? { status: value.status } : {})
    };
    const page = normalizePageData(await (tenantId.value
      ? (currentTenantScope.value
        ? tenantAccountManagementApi.page(tenantId.value, pageQuery, requestController.signal)
        : platformTenantAccountApi.page(tenantId.value, pageQuery, requestController.signal))
      : platformAccountApi.page(pageQuery, requestController.signal)));
    if (requestController.signal.aborted || listController !== requestController) return;
    records.value = page.records;
    total.value = page.total;
    const requestedAccountId = typeof route.params.accountId === "string" ? route.params.accountId : null;
    const requestedAccount = requestedAccountId
      ? page.records.find((account) => String(account.accountId) === requestedAccountId)
      : null;
    if (requestedAccount && selectedAccount.value?.accountId !== requestedAccount.accountId) {
      void openSecurity(requestedAccount, false);
    }
    const resolved = await Promise.all(page.records.map(async (account) => [
      String(account.accountId),
      account.avatar ? await files.resolveOne(account.avatar) : null
    ] as const));
    if (!requestController.signal.aborted && listController === requestController) {
      avatarUrls.value = new Map(resolved.filter((entry): entry is readonly [string, string] => Boolean(entry[1])));
    }
  } catch (error) {
    if (!(isApiError(error) && error.messageKey === "client.api.cancelled")) {
      message.error(errorMessage(error, "accountManagement.loadFailed"));
    }
  } finally {
    if (listController === requestController) loading.value = false;
  }
}

function openCreate(): void {
  Object.assign(createForm, { identifier: "", password: "", displayName: "", phone: "", email: "" });
  createOpen.value = true;
}

async function create(): Promise<void> {
  try {
    await createFormRef.value?.validate();
  } catch {
    return;
  }
  submitting.value = true;
  try {
    await platformAccountApi.create({
      identifier: createForm.identifier.trim(),
      password: createForm.password,
      displayName: createForm.displayName.trim(),
      ...(createForm.phone.trim() ? { phone: createForm.phone.trim() } : {}),
      ...(createForm.email.trim() ? { email: createForm.email.trim() } : {}),
      idempotencyKey: createIdempotencyKey()
    });
    createOpen.value = false;
    message.success(t("accountManagement.created"));
    await queryState.reload();
  } catch (error) {
    message.error(errorMessage(error, "accountManagement.createFailed"));
  } finally {
    submitting.value = false;
  }
}

async function openSecurity(account: ManagedAccountView, navigate = true): Promise<void> {
  if (navigate && String(route.params.accountId ?? "") !== String(account.accountId)) {
    await router.push({
      name: detailRouteName.value,
      params: { ...route.params, accountId: String(account.accountId) },
      query: route.query
    });
  }
  securityController?.abort();
  const requestController = new AbortController();
  securityController = requestController;
  selectedAccount.value = account;
  detailTab.value = "profile";
  security.value = null;
  sessions.value = [];
  profile.value = null;
  profileAvatarUrl.value = avatarUrl(account);
  securityOpen.value = true;
  securityLoading.value = true;
  try {
    const [accountSecurity, accountSessions, currentAccounts, methods, accountProfile] = await Promise.all([
      canReadSecurity.value
        ? (tenantId.value
          ? (currentTenantScope.value
            ? tenantAccountManagementApi.security(tenantId.value, account.accountId, requestController.signal)
            : platformTenantAccountApi.security(tenantId.value, account.accountId, requestController.signal))
          : platformAccountApi.security(account.accountId, requestController.signal))
        : Promise.resolve(null),
      canReadSessions.value
        ? (tenantId.value
          ? (currentTenantScope.value
            ? tenantAccountManagementApi.sessions(tenantId.value, account.accountId, requestController.signal)
            : platformTenantAccountApi.sessions(tenantId.value, account.accountId, requestController.signal))
          : platformAccountApi.sessions(account.accountId, requestController.signal))
        : Promise.resolve([]),
      needsStepUpContext.value
        ? accountSecurityApi.accounts()
        : Promise.resolve([]),
      needsStepUpContext.value
        ? authenticationApi.methods()
        : Promise.resolve([]),
      tenantScope.value
        ? Promise.resolve(null)
        : platformAccountApi.profile(account.accountId, requestController.signal)
    ]);
    if (requestController.signal.aborted || securityController !== requestController) return;
    security.value = accountSecurity;
    sessions.value = accountSessions;
    currentAccountSecurity.value = currentAccounts.find((item) => item.accountId === auth.accountId) ?? null;
    authenticationMethods.value = methods;
    profile.value = accountProfile;
    profileAvatarUrl.value = accountProfile?.avatar
      ? await files.resolveOne(accountProfile.avatar)
      : avatarUrl(account);
  } catch (error) {
    if (!requestController.signal.aborted) message.error(errorMessage(error, "accountManagement.securityLoadFailed"));
  } finally {
    if (securityController === requestController) securityLoading.value = false;
  }
}

function openProfileEditor(): void {
  if (!profile.value) return;
  Object.assign(profileForm, {
    displayName: profile.value.displayName,
    phone: profile.value.phone ?? "",
    email: profile.value.email ?? ""
  });
  profileOpen.value = true;
}

function continueProfile(): void {
  if (!profileForm.displayName.trim()) {
    message.warning(t("accountManagement.displayNameRequired"));
    return;
  }
  profileOpen.value = false;
  openSecondStep({ type: "profile" });
}

async function replaceAvatar(value: { fileId: string; uploadGrant: string; previewUrl: string }): Promise<void> {
  const account = selectedAccount.value;
  const currentProfile = profile.value;
  if (!account || !currentProfile) return;
  submitting.value = true;
  try {
    await platformAccountApi.replaceAvatar(
      account.accountId,
      value.fileId,
      value.uploadGrant,
      currentProfile.profileVersion
    );
    profileAvatarUrl.value = value.previewUrl;
    avatarUrls.value = new Map(avatarUrls.value).set(String(account.accountId), value.previewUrl);
    message.success(t("accountManagement.avatarUpdated"));
    try {
      profile.value = await platformAccountApi.profile(account.accountId);
    } catch {
      profile.value = null;
      message.warning(t("avatar.refreshFailed"));
    }
  } catch (error) {
    URL.revokeObjectURL(value.previewUrl);
    message.error(errorMessage(error, "accountManagement.profileUpdateFailed"));
  } finally {
    submitting.value = false;
  }
}

function changeStatus(account: ManagedAccountView): void {
  const next: AccountStatus = account.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  const action = statusLabel(next);
  Modal.confirm({
    title: t("accountManagement.statusConfirm", { action, name: account.displayName }),
    okText: action,
    okButtonProps: { danger: next === "DISABLED" },
    async onOk() {
      try {
        await platformAccountApi.changeStatus(account.accountId, next, account.version);
        message.success(t("accountManagement.statusUpdated", { action }));
        await queryState.reload();
      } catch (error) {
        message.error(errorMessage(error, "accountManagement.statusUpdateFailed"));
        throw error;
      }
    }
  });
}

function deleteAccount(account: ManagedAccountView): void {
  Modal.confirm({
    title: t("accountManagement.deleteConfirm", { name: account.displayName }),
    content: t("accountManagement.deleteImpact"),
    okText: t("common.delete"),
    okButtonProps: { danger: true },
    async onOk() {
      try {
        await platformAccountApi.delete(account.accountId, account.version);
        if (selectedAccount.value?.accountId === account.accountId) closeSecurity();
        message.success(t("accountManagement.deleted"));
        await queryState.reload();
      } catch (error) {
        message.error(errorMessage(error, "accountManagement.deleteFailed"));
        throw error;
      }
    }
  });
}

function openSecondStep(action: SecurityAction): void {
  if (!selectedAccount.value) return;
  securityAction.value = action;
  const authorizationId = authentication.reusableStepUp(auth.accountId);
  if (authorizationId) {
    void completeSecondStepAction(authorizationId);
    return;
  }
  if (!availableSecondStepMethods.value.length) {
    message.warning(t("accountManagement.secondStepUnavailable"));
    return;
  }
  secondStepOpen.value = true;
}

function openPassword(): void {
  if (!selectedAccount.value) return;
  Object.assign(passwordForm, {
    identifier: security.value?.identifiers.find((item) => item.status === "ACTIVE")?.value ?? "",
    password: "",
    confirmPassword: ""
  });
  passwordOpen.value = true;
}

function continuePassword(): void {
  if (!passwordForm.password || passwordForm.password !== passwordForm.confirmPassword) {
    message.warning(t("accountManagement.passwordMismatch"));
    return;
  }
  if (!passwordCredential.value && !passwordForm.identifier.trim()) {
    message.warning(t("accountManagement.identifierRequired"));
    return;
  }
  passwordOpen.value = false;
  openSecondStep({ type: "password", credentialId: passwordCredential.value?.credentialId ?? null });
}

async function completeSecondStepAction(authenticationId: string): Promise<void> {
  const account = selectedAccount.value;
  const action = securityAction.value;
  if (!account || !action) return;
  secondStepSubmitting.value = true;
  try {
    if (action.type === "mfa") {
      const changed = tenantId.value
        ? await (currentTenantScope.value
          ? tenantAccountManagementApi.changeSecondFactorRequirement(
            tenantId.value, account.accountId, { required: action.required, version: account.version }, authenticationId)
          : platformTenantAccountApi.changeSecondFactorRequirement(
            tenantId.value, account.accountId, { required: action.required, version: account.version }, authenticationId))
        : await platformAccountApi.changeSecondFactorRequirement(
          account.accountId, { required: action.required, version: account.version }, authenticationId);
      account.secondFactorRequired = changed.required;
      account.version = changed.accountVersion;
      if (changed.recoveryCodes.length) {
        issuedRecoveryCodes.value = changed.recoveryCodes;
        recoveryCodeAccountTitle.value = account.accountId;
        recoveryCodesOpen.value = true;
      }
      message.success(t("accountManagement.securityUpdated"));
    } else if (action.type === "recovery") {
      const changed = tenantId.value
        ? await (currentTenantScope.value
          ? tenantAccountManagementApi.reissueRecoveryCodes(
            tenantId.value, account.accountId, account.version, authenticationId)
          : platformTenantAccountApi.reissueRecoveryCodes(
            tenantId.value, account.accountId, account.version, authenticationId))
        : await platformAccountApi.reissueRecoveryCodes(
          account.accountId, account.version, authenticationId);
      account.version = changed.accountVersion;
      issuedRecoveryCodes.value = changed.recoveryCodes;
      recoveryCodeAccountTitle.value = account.accountId;
      recoveryCodesOpen.value = true;
      message.success(t("accountManagement.recoveryCodesRegenerated"));
    } else if (action.type === "password") {
      if (action.credentialId) {
        const credential = passwordCredential.value;
        if (!credential) throw new Error("Password credential is no longer available");
        if (tenantId.value) {
          const api = currentTenantScope.value ? tenantAccountManagementApi : platformTenantAccountApi;
          await api.replacePassword(
            tenantId.value, account.accountId, action.credentialId, passwordForm.password, credential.version, authenticationId);
        } else {
          await platformAccountApi.replacePassword(
            account.accountId, action.credentialId, passwordForm.password, credential.version, authenticationId);
        }
      } else {
        if (tenantId.value) {
          const api = currentTenantScope.value ? tenantAccountManagementApi : platformTenantAccountApi;
          await api.bindPassword(
            tenantId.value,
            account.accountId,
            passwordForm.identifier.trim(),
            passwordForm.password,
            createIdempotencyKey(),
            account.version,
            authenticationId
          );
        } else {
          await platformAccountApi.bindPassword(
            account.accountId,
            passwordForm.identifier.trim(),
            passwordForm.password,
            createIdempotencyKey(),
            account.version,
            authenticationId
          );
        }
      }
      message.success(t("accountManagement.passwordUpdated"));
    } else if (action.type === "profile") {
      const currentProfile = profile.value;
      if (!currentProfile) return;
      await platformAccountApi.updateProfile(account.accountId, {
        displayName: profileForm.displayName.trim(),
        ...(profileForm.phone.trim() ? { phone: profileForm.phone.trim() } : {}),
        ...(profileForm.email.trim() ? { email: profileForm.email.trim() } : {}),
        subjectVersion: currentProfile.subjectVersion
      }, authenticationId);
      account.displayName = profileForm.displayName.trim();
      message.success(t("accountManagement.profileUpdated"));
      await queryState.reload();
    } else if (action.type === "sessions") {
      if (tenantId.value) {
        const api = currentTenantScope.value ? tenantAccountManagementApi : platformTenantAccountApi;
        await api.endSessions(tenantId.value, account.accountId, account.version, authenticationId);
      } else {
        await platformAccountApi.endSessions(account.accountId, account.version, authenticationId);
      }
      sessions.value = [];
      message.success(t("accountManagement.allSessionsRevoked"));
    } else {
      if (tenantId.value) {
        const api = currentTenantScope.value ? tenantAccountManagementApi : platformTenantAccountApi;
        await api.endSession(tenantId.value, account.accountId, action.sessionId, account.version, authenticationId);
      } else {
        await platformAccountApi.endSession(
          account.accountId, action.sessionId, account.version, authenticationId);
      }
      sessions.value = sessions.value.filter((session) => session.sessionId !== action.sessionId);
      message.success(t("accountManagement.sessionRevoked"));
    }
    secondStepOpen.value = false;
    securityAction.value = null;
    await openSecurity(account);
  } catch (error) {
    authentication.forgetStepUp(authenticationId);
    message.error(errorMessage(error, "accountManagement.securityUpdateFailed"));
  } finally {
    secondStepSubmitting.value = false;
  }
}

function closeRecoveryCodes(): void {
  recoveryCodesOpen.value = false;
  issuedRecoveryCodes.value = [];
  recoveryCodeAccountTitle.value = "";
}

function applyQuery(): void {
  query.page = 1;
  filterOpen.value = false;
  void queryState.apply();
}

function resetQuery(): void {
  filterOpen.value = false;
  void queryState.reset();
}

function changePage(page: number, size: number): void {
  void queryState.update({ page, size });
}

function closeSecurity(): void {
  securityOpen.value = false;
  selectedAccount.value = null;
  if (typeof route.params.accountId === "string") {
    void router.push({
      name: listRouteName.value,
      params: tenantId.value ? { tenantId: tenantId.value } : {},
      query: route.query
    });
  }
}

onBeforeUnmount(() => {
  listController?.abort();
  securityController?.abort();
});
</script>

<template>
  <div>
    <PageHeader
      :title="pageTitle"
      :description="tenantScope ? t('accountManagement.tenantDescription') : t('accountManagement.description')"
    >
      <template #actions>
        <a-button
          class="mobile-only"
          @click="filterOpen = true"
        >
          <Filter :size="16" />{{ t("common.filter") }}
        </a-button>
        <a-button
          v-if="canCreate"
          type="primary"
          @click="openCreate"
        >
          <CirclePlus :size="16" />{{ t("accountManagement.create") }}
        </a-button>
      </template>
    </PageHeader>

    <section class="page-panel management-panel">
      <QueryToolbar class="desktop-only">
        <template #search>
          <a-input
            v-model:value="query.search"
            allow-clear
            :placeholder="t('accountManagement.searchPlaceholder')"
            @press-enter="applyQuery"
          >
            <template #prefix>
              <Search :size="15" />
            </template>
          </a-input>
        </template>
        <template #filters>
          <a-select
            v-model:value="query.status"
            allow-clear
            :placeholder="t('common.allStatuses')"
          >
            <a-select-option value="ACTIVE">
              {{ t("common.active") }}
            </a-select-option>
            <a-select-option value="DISABLED">
              {{ t("common.disabled") }}
            </a-select-option>
          </a-select>
        </template>
        <template #actions>
          <a-button @click="resetQuery">
            <RotateCcw :size="16" />{{ t("common.reset") }}
          </a-button>
          <a-button
            type="primary"
            @click="applyQuery"
          >
            <Search :size="16" />{{ t("common.query") }}
          </a-button>
        </template>
      </QueryToolbar>

      <a-table
        row-key="accountId"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 720 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'account'">
            <div class="account-cell">
              <DefaultProfileAvatar
                :name="record.displayName"
                :src="avatarUrl(record)"
                :size="38"
              />
              <div class="account-cell__text">
                <button
                  class="account-link"
                  type="button"
                  @click="openSecurity(record)"
                >
                  {{ record.displayName }}
                </button>
                <span>{{ record.primaryIdentifier || t("accountManagement.noUsername") }} · {{ t("accountManagement.accountNumber", { id: record.accountId }) }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'security'">
            <a-tag :color="record.secondFactorRequired ? 'processing' : 'default'">
              {{ record.secondFactorRequired ? t("accountManagement.secondFactorRequired") : t("accountManagement.secondFactorOptional") }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space :size="4">
              <RowActionButton
                v-if="canReadSecurity || canReadSessions"
                :label="t('accountManagement.details')"
                @click="openSecurity(record)"
              >
                <ShieldCheck :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canEditGlobal"
                :label="statusLabel(record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')"
                :danger="record.status === 'ACTIVE'"
                @click="changeStatus(record)"
              >
                <Power :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canDeleteGlobal"
                :label="t('common.delete')"
                danger
                @click="deleteAccount(record)"
              >
                <Trash2 :size="17" />
              </RowActionButton>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          {{ t("accountManagement.empty") }}
        </template>
      </a-table>
      <a-pagination
        class="table-pagination"
        :current="query.page"
        :page-size="query.size"
        :total="total"
        show-size-changer
        :show-total="(count: number) => t('common.total', { total: count })"
        @change="changePage"
        @show-size-change="changePage"
      />
    </section>

    <a-modal
      v-model:open="createOpen"
      :title="t('accountManagement.create')"
      :confirm-loading="submitting"
      @ok="create"
    >
      <a-form
        ref="createFormRef"
        :model="createForm"
        layout="vertical"
      >
        <a-form-item
          :label="t('accountManagement.identifier')"
          name="identifier"
          :rules="[{ required: true, whitespace: true, message: t('accountManagement.usernameRequired') }]"
        >
          <a-input
            v-model:value="createForm.identifier"
            autocomplete="username"
          />
        </a-form-item>
        <a-form-item
          :label="t('accountManagement.initialPassword')"
          name="password"
          :rules="[{ required: true, message: t('accountManagement.passwordRequired') }]"
        >
          <a-input-password
            v-model:value="createForm.password"
            autocomplete="new-password"
          />
        </a-form-item>
        <a-form-item
          :label="t('accountManagement.displayName')"
          name="displayName"
          :rules="[{ required: true, whitespace: true, message: t('accountManagement.displayNameRequired') }]"
        >
          <a-input v-model:value="createForm.displayName" />
        </a-form-item>
        <a-form-item :label="t('accountManagement.phone')">
          <a-input
            v-model:value="createForm.phone"
            autocomplete="tel"
          />
        </a-form-item>
        <a-form-item
          :label="t('accountManagement.email')"
          name="email"
          :rules="[{ type: 'email', message: t('accountManagement.emailInvalid') }]"
        >
          <a-input
            v-model:value="createForm.email"
            autocomplete="email"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-drawer
      v-model:open="filterOpen"
      :title="t('accountManagement.filterTitle')"
      placement="right"
      width="100%"
      :root-style="{ width: '100dvw' }"
      :content-wrapper-style="{ maxWidth: '360px' }"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('accountManagement.searchLabel')">
          <a-input
            v-model:value="query.search"
            allow-clear
            :placeholder="t('accountManagement.searchPlaceholder')"
          />
        </a-form-item>
        <a-form-item :label="t('common.status')">
          <a-select
            v-model:value="query.status"
            allow-clear
            :placeholder="t('common.allStatuses')"
          >
            <a-select-option value="ACTIVE">
              {{ t("common.active") }}
            </a-select-option>
            <a-select-option value="DISABLED">
              {{ t("common.disabled") }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="resetQuery">
            {{ t("common.reset") }}
          </a-button>
          <a-button
            type="primary"
            @click="applyQuery"
          >
            {{ t("common.query") }}
          </a-button>
        </a-space>
      </template>
    </a-drawer>

    <a-drawer
      :open="securityOpen"
      width="min(680px, 100vw)"
      :title="selectedAccount ? t('accountManagement.detailTitle', { name: selectedAccount.displayName }) : t('accountManagement.details')"
      class="responsive-drawer"
      @close="closeSecurity"
    >
      <a-spin :spinning="securityLoading">
        <div
          v-if="selectedAccount"
          class="detail-identity"
        >
          <DefaultProfileAvatar
            :name="selectedAccount.displayName"
            :src="profileAvatarUrl"
            :size="48"
          />
          <div>
            <strong>{{ selectedAccount.displayName }}</strong>
            <span>{{ selectedAccount.primaryIdentifier || t("accountManagement.noUsername") }}</span>
          </div>
          <a-tag :color="statusColor(selectedAccount.status)">
            {{ statusLabel(selectedAccount.status) }}
          </a-tag>
        </div>

        <a-tabs v-model:active-key="detailTab">
          <a-tab-pane
            key="profile"
            :tab="t('accountManagement.profileTab')"
          >
            <div
              v-if="profile && selectedAccount"
              class="profile-section"
            >
              <div class="profile-avatar-row">
                <AvatarCropper
                  v-if="canEditGlobal"
                  :subject-id="profile.subjectId"
                  :disabled="submitting"
                  @uploaded="replaceAvatar"
                >
                  <template #trigger>
                    <DefaultProfileAvatar
                      :name="profile.displayName"
                      :src="profileAvatarUrl"
                      :size="72"
                    />
                  </template>
                </AvatarCropper>
                <DefaultProfileAvatar
                  v-else
                  :name="profile.displayName"
                  :src="profileAvatarUrl"
                  :size="72"
                />
                <a-button
                  v-if="canEditGlobal"
                  @click="openProfileEditor"
                >
                  <Pencil :size="16" />{{ t("accountManagement.editProfile") }}
                </a-button>
              </div>
              <a-descriptions
                bordered
                size="small"
                :column="1"
              >
                <a-descriptions-item :label="t('accountManagement.displayName')">
                  {{ profile.displayName }}
                </a-descriptions-item>
                <a-descriptions-item :label="t('accountManagement.phone')">
                  {{ profile.phone || t("accountManagement.notProvided") }}
                </a-descriptions-item>
                <a-descriptions-item :label="t('accountManagement.email')">
                  {{ profile.email || t("accountManagement.notProvided") }}
                </a-descriptions-item>
                <a-descriptions-item :label="t('accountManagement.accountNumberLabel')">
                  {{ profile.accountId }}
                </a-descriptions-item>
              </a-descriptions>
            </div>
            <a-descriptions
              v-else-if="selectedAccount"
              bordered
              size="small"
              :column="1"
            >
              <a-descriptions-item :label="t('accountManagement.displayName')">
                {{ selectedAccount.displayName }}
              </a-descriptions-item>
              <a-descriptions-item :label="t('accountManagement.username')">
                {{ selectedAccount.primaryIdentifier || t("accountManagement.noUsername") }}
              </a-descriptions-item>
              <a-descriptions-item :label="t('accountManagement.accountNumberLabel')">
                {{ selectedAccount.accountId }}
              </a-descriptions-item>
            </a-descriptions>
          </a-tab-pane>

          <a-tab-pane
            key="security"
            :tab="t('accountManagement.securityTab')"
          >
            <template v-if="security">
              <section class="detail-section">
                <div class="detail-section__heading">
                  <div>
                    <h3>{{ t("accountManagement.loginProtection") }}</h3>
                    <p>{{ selectedAccount?.secondFactorRequired ? t("accountManagement.secondFactorRequiredDescription") : t("accountManagement.secondFactorOptionalDescription") }}</p>
                  </div>
                  <a-tag :color="selectedAccount?.secondFactorRequired ? 'processing' : 'default'">
                    {{ selectedAccount?.secondFactorRequired ? t("accountManagement.secondFactorRequired") : t("accountManagement.secondFactorOptional") }}
                  </a-tag>
                </div>
                <div
                  v-if="canManageMfa || canManageRecoveryCodes"
                  class="security-actions"
                >
                  <a-button
                    v-if="canManageMfa && !selectedAccount?.secondFactorRequired"
                    type="primary"
                    @click="openSecondStep({ type: 'mfa', required: true })"
                  >
                    {{ t("accountManagement.requireSecondFactor") }}
                  </a-button>
                  <a-button
                    v-if="canManageRecoveryCodes && selectedAccount?.secondFactorRequired"
                    @click="openSecondStep({ type: 'recovery' })"
                  >
                    {{ t("accountManagement.regenerateRecoveryCodes") }}
                  </a-button>
                  <a-button
                    v-if="canManageMfa && selectedAccount?.secondFactorRequired"
                    danger
                    @click="openSecondStep({ type: 'mfa', required: false })"
                  >
                    {{ t("accountManagement.removeSecondFactor") }}
                  </a-button>
                </div>
                <p class="recovery-summary">
                  {{ t("accountManagement.recoveryCodesRemaining", { count: security.recoveryCodesRemaining }) }}
                </p>
              </section>

              <section class="detail-section">
                <h3>{{ t("accountManagement.identifiers") }}</h3>
                <a-list
                  size="small"
                  :data-source="security.identifiers"
                >
                  <template #renderItem="{ item }">
                    <a-list-item>
                      <a-list-item-meta
                        :title="item.value"
                        :description="identifierTypeLabel(item.identifierType)"
                      />
                      <a-tag :color="item.status === 'ACTIVE' ? 'success' : 'default'">
                        {{ item.status === "ACTIVE" ? t("common.active") : t("common.disabled") }}
                      </a-tag>
                    </a-list-item>
                  </template>
                </a-list>
              </section>

              <section class="detail-section">
                <div class="detail-section__heading">
                  <h3>{{ t("accountManagement.credentials") }}</h3>
                  <a-button
                    v-if="canManageCredentials"
                    @click="openPassword"
                  >
                    <KeyRound :size="16" />{{ passwordCredential ? t("accountManagement.replacePassword") : t("accountManagement.bindPassword") }}
                  </a-button>
                </div>
                <a-list
                  size="small"
                  :data-source="security.credentials"
                >
                  <template #renderItem="{ item }">
                    <a-list-item>
                      <a-list-item-meta
                        :title="credentialTypeLabel(item.credentialType)"
                        :description="t('accountManagement.credentialUpdatedAt', { time: formatDateTime(item.updatedAt) })"
                      />
                      <a-tag :color="item.status === 'ACTIVE' ? 'success' : 'default'">
                        {{ item.status === "ACTIVE" ? t("common.active") : t("common.disabled") }}
                      </a-tag>
                    </a-list-item>
                  </template>
                  <template #emptyText>
                    {{ t("accountManagement.noCredentials") }}
                  </template>
                </a-list>
              </section>
            </template>
          </a-tab-pane>

          <a-tab-pane
            v-if="canReadSessions"
            key="sessions"
            :tab="t('accountManagement.sessionsTab')"
          >
            <div class="detail-section__heading session-heading">
              <div>
                <h3>{{ t("accountManagement.sessions") }}</h3>
                <p>{{ t("accountManagement.sessionsDescription") }}</p>
              </div>
              <a-button
                v-if="canRevokeSessions && sessions.length"
                danger
                @click="openSecondStep({ type: 'sessions' })"
              >
                <LogOut :size="16" />{{ t("accountManagement.revokeAllSessions") }}
              </a-button>
            </div>
            <a-list
              size="small"
              :data-source="sessions"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta
                    :title="item.terminalName || t('accountManagement.unknownDevice')"
                    :description="t('accountManagement.signedInAt', { time: formatDateTime(item.loginTime) })"
                  />
                  <template
                    v-if="canRevokeSessions"
                    #actions
                  >
                    <a-tooltip :title="t('accountManagement.revokeSession')">
                      <a-button
                        danger
                        type="text"
                        :aria-label="t('accountManagement.revokeSession')"
                        @click="openSecondStep({ type: 'session', sessionId: item.sessionId })"
                      >
                        <LogOut :size="16" />
                      </a-button>
                    </a-tooltip>
                  </template>
                </a-list-item>
              </template>
              <template #emptyText>
                {{ t("accountManagement.noSessions") }}
              </template>
            </a-list>
          </a-tab-pane>
        </a-tabs>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:open="profileOpen"
      :title="t('accountManagement.editProfile')"
      :ok-text="t('common.continue')"
      @ok="continueProfile"
    >
      <a-form layout="vertical">
        <a-form-item
          :label="t('accountManagement.displayName')"
          required
        >
          <a-input v-model:value="profileForm.displayName" />
        </a-form-item>
        <a-form-item :label="t('accountManagement.phone')">
          <a-input
            v-model:value="profileForm.phone"
            autocomplete="tel"
          />
        </a-form-item>
        <a-form-item :label="t('accountManagement.email')">
          <a-input
            v-model:value="profileForm.email"
            autocomplete="email"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="passwordOpen"
      :title="passwordCredential ? t('accountManagement.replacePassword') : t('accountManagement.bindPassword')"
      :ok-text="t('common.continue')"
      @ok="continuePassword"
    >
      <a-form layout="vertical">
        <a-form-item
          v-if="!passwordCredential"
          :label="t('accountManagement.identifier')"
          required
        >
          <a-input
            v-model:value="passwordForm.identifier"
            autocomplete="username"
          />
        </a-form-item>
        <a-form-item
          :label="t('accountManagement.newPassword')"
          required
        >
          <a-input-password
            v-model:value="passwordForm.password"
            autocomplete="new-password"
          />
        </a-form-item>
        <a-form-item
          :label="t('accountManagement.confirmPassword')"
          required
        >
          <a-input-password
            v-model:value="passwordForm.confirmPassword"
            autocomplete="new-password"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <SecondStepVerificationDialog
      v-model:open="secondStepOpen"
      :methods="availableSecondStepMethods"
      :password-identifier="activePasswordIdentifier"
      :title="secondStepTitle"
      :impact="secondStepImpact"
      :submit-label="t('accountManagement.confirmSecurityAction')"
      :danger="secondStepDanger"
      :submitting="secondStepSubmitting"
      @verified="completeSecondStepAction"
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
.account-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.account-cell__text {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.account-cell__text span {
  overflow: hidden;
  color: var(--app-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-link {
  overflow: hidden;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--app-primary);
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-link:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}

.detail-identity {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--app-border);
}

.detail-identity > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.detail-identity strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-identity span,
.detail-section p,
.session-heading p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.profile-section,
.detail-section {
  padding: 4px 0 20px;
}

.profile-avatar-row,
.detail-section__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.profile-avatar-row {
  margin-bottom: 20px;
  justify-content: flex-start;
}

.detail-section + .detail-section {
  padding-top: 20px;
  border-top: 1px solid var(--app-border);
}

.detail-section h3,
.detail-section__heading h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.security-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.recovery-summary {
  margin-top: 12px !important;
}

.session-heading {
  margin-bottom: 12px;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 640px) {
  .detail-section__heading,
  .session-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .detail-section__heading > .ant-btn,
  .session-heading > .ant-btn,
  .security-actions > .ant-btn {
    width: 100%;
    min-height: 44px;
  }
}
</style>
