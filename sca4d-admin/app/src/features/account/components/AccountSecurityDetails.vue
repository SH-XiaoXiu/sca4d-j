<script setup lang="ts">
import {
  type AccountCredentialView,
  type AccountSecurityView,
  type AccountSessionView,
  type ExternalIdentityView,
  type IdentityProviderProtocol,
  type LdapAvailableProvider,
  type OAuth2AvailableProvider,
  type OidcAvailableProvider,
  type PasskeyCredentialView
} from "@sca4d/admin-common";
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import LinkedAccountsSection from "./LinkedAccountsSection.vue";
import LoginDevicesSection from "./LoginDevicesSection.vue";
import TwoStepVerificationSection from "./TwoStepVerificationSection.vue";
import VerificationMethodsSection from "./VerificationMethodsSection.vue";

defineProps<{
  account: AccountSecurityView;
  accountTitle: string;
  currentAccountId: string | null;
  currentSessionId: string | null;
  secondStepAvailable: boolean;
  webAuthnAvailable: boolean;
  sessions: AccountSessionView[];
  sessionsLoading: boolean;
  sessionsError: boolean;
  externalIdentities: ExternalIdentityView[];
  externalIdentitiesLoading: boolean;
  oidcProviders: OidcAvailableProvider[];
  oauth2Providers: OAuth2AvailableProvider[];
  ldapProviders: LdapAvailableProvider[];
}>();
const emit = defineEmits<{
  addPasskey: [];
  setupTotp: [credential: AccountCredentialView | null];
  deleteTotp: [credential: AccountCredentialView];
  deletePasskey: [credential: PasskeyCredentialView];
  linkIdentity: [protocol: IdentityProviderProtocol];
  unlinkIdentity: [identity: ExternalIdentityView];
  changeSecondFactor: [required: boolean];
  refreshSessions: [];
  endSession: [session: AccountSessionView];
}>();
const { t } = useI18n();
const activeSection = ref<"methods" | "two-step" | "identities" | "sessions">("methods");
</script>

<template>
  <main class="account-detail page-panel">
    <header class="account-detail__header">
      <div>
        <h2>{{ accountTitle }}</h2>
        <p>{{ t("accountSecurity.accountSource", { accountId: account.accountId }) }}</p>
      </div>
      <a-tag
        v-if="account.accountId === currentAccountId"
        color="blue"
      >
        {{ t("accountSecurity.currentAccount") }}
      </a-tag>
    </header>

    <a-segmented
      v-model:value="activeSection"
      block
      class="security-navigation"
      :options="[
        { value: 'methods', label: t('accountSecurity.verificationMethods') },
        { value: 'two-step', label: t('accountSecurity.twoFactor') },
        { value: 'identities', label: t('accountSecurity.linkedAccounts') },
        { value: 'sessions', label: t('accountSecurity.loginDevices') }
      ]"
    />

    <VerificationMethodsSection
      v-if="activeSection === 'methods'"
      :account="account"
      :second-step-available="secondStepAvailable"
      :web-authn-available="webAuthnAvailable"
      @add-passkey="emit('addPasskey')"
      @setup-totp="emit('setupTotp', $event)"
      @delete-totp="emit('deleteTotp', $event)"
      @delete-passkey="emit('deletePasskey', $event)"
    />
    <TwoStepVerificationSection
      v-else-if="activeSection === 'two-step'"
      :account="account"
      :second-step-available="secondStepAvailable"
      @change="emit('changeSecondFactor', $event)"
    />
    <LinkedAccountsSection
      v-else-if="activeSection === 'identities'"
      :current-account="account.accountId === currentAccountId"
      :second-step-available="secondStepAvailable"
      :identities="externalIdentities"
      :loading="externalIdentitiesLoading"
      :oidc-providers="oidcProviders"
      :oauth2-providers="oauth2Providers"
      :ldap-providers="ldapProviders"
      @link="emit('linkIdentity', $event)"
      @unlink="emit('unlinkIdentity', $event)"
    />
    <LoginDevicesSection
      v-else
      :current-session-id="currentSessionId"
      :second-step-available="secondStepAvailable"
      :sessions="sessions"
      :loading="sessionsLoading"
      :error="sessionsError"
      @refresh="emit('refreshSessions')"
      @end="emit('endSession', $event)"
    />
  </main>
</template>

<style src="./account-security-details.css"></style>
