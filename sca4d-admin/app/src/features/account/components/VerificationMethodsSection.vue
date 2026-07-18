<script setup lang="ts">
import {
  formatDateTime,
  type AccountCredentialView,
  type AccountIdentifierView,
  type AccountSecurityView,
  type PasskeyCredentialView
} from "@sca4d/admin-common";
import { Fingerprint, KeyRound, Smartphone, Trash2, UserRound } from "lucide-vue-next";
import { computed } from "vue";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  account: AccountSecurityView;
  secondStepAvailable: boolean;
  webAuthnAvailable: boolean;
}>();
const emit = defineEmits<{
  addPasskey: [];
  setupTotp: [credential: AccountCredentialView | null];
  deleteTotp: [credential: AccountCredentialView];
  deletePasskey: [credential: PasskeyCredentialView];
}>();
const { t } = useI18n();
const totpCredential = computed(
  () => props.account.credentials.find((credential) => credential.credentialType === "TOTP") ?? null
);
const genericCredentials = computed(
  () => props.account.credentials.filter((credential) => credential.credentialType !== "PASSKEY")
);

function identifierType(type: AccountIdentifierView["identifierType"]): string {
  return type === "USERNAME" ? t("accountSecurity.username") : t("accountSecurity.email");
}

function credentialType(type: AccountCredentialView["credentialType"]): string {
  if (type === "PASSWORD") return t("accountSecurity.password");
  if (type === "TOTP") return t("accountSecurity.totp");
  return t("accountSecurity.passkey");
}
</script>

<template>
  <div class="verification-methods">
    <section class="security-section">
      <div class="section-heading">
        <UserRound :size="18" />
        <h3>{{ t("accountSecurity.identifiers") }}</h3>
      </div>
      <a-empty
        v-if="!account.identifiers.length"
        :description="t('accountSecurity.identifiersEmpty')"
      />
      <div
        v-else
        class="fact-list"
      >
        <div
          v-for="identifier in account.identifiers"
          :key="identifier.identifierId"
          class="fact-row"
        >
          <span>{{ identifierType(identifier.identifierType) }}</span>
          <strong>{{ identifier.value }}</strong>
          <a-tag :color="identifier.status === 'ACTIVE' ? 'success' : 'default'">
            {{ identifier.status === "ACTIVE" ? t("common.active") : t("common.disabled") }}
          </a-tag>
        </div>
      </div>
    </section>

    <section class="security-section">
      <div class="section-heading section-heading--actions">
        <div>
          <KeyRound :size="18" />
          <h3>{{ t("accountSecurity.credentials") }}</h3>
        </div>
        <div class="credential-heading-actions">
          <a-button
            :disabled="!secondStepAvailable || !webAuthnAvailable"
            @click="emit('addPasskey')"
          >
            <Fingerprint :size="16" />{{ t("accountSecurity.addPasskey") }}
          </a-button>
          <a-button
            :disabled="!secondStepAvailable"
            @click="emit('setupTotp', totpCredential)"
          >
            <Smartphone :size="16" />
            {{ totpCredential ? t("accountSecurity.replaceTotp") : t("accountSecurity.setupTotp") }}
          </a-button>
        </div>
      </div>
      <a-alert
        v-if="!webAuthnAvailable"
        type="warning"
        show-icon
        :message="t('accountSecurity.passkeyUnavailable')"
        class="credential-notice"
      />
      <a-empty
        v-if="!genericCredentials.length && !account.passkeyCredentials.length"
        :description="t('accountSecurity.credentialsEmpty')"
      />
      <div
        v-if="genericCredentials.length"
        class="fact-list"
      >
        <div
          v-for="credential in genericCredentials"
          :key="credential.credentialId"
          class="fact-row"
        >
          <span>{{ credentialType(credential.credentialType) }}</span>
          <strong>{{ formatDateTime(credential.updatedAt) }}</strong>
          <div class="credential-actions">
            <a-tag :color="credential.status === 'ACTIVE' ? 'success' : 'default'">
              {{ credential.status === "ACTIVE" ? t("common.active") : t("common.disabled") }}
            </a-tag>
            <a-button
              v-if="credential.credentialType === 'TOTP'"
              type="text"
              danger
              :disabled="!secondStepAvailable"
              @click="emit('deleteTotp', credential)"
            >
              <Trash2 :size="16" />{{ t("accountSecurity.deleteTotp") }}
            </a-button>
          </div>
        </div>
      </div>
      <div
        v-if="account.passkeyCredentials.length"
        class="fact-list passkey-list"
      >
        <div
          v-for="credential in account.passkeyCredentials"
          :key="credential.credentialId"
          class="fact-row"
        >
          <span class="passkey-name">
            <strong>{{ credential.displayName }}</strong>
            <small>{{ credential.backupState ? t("accountSecurity.passkeyBackedUp") : t("accountSecurity.passkeyNotBackedUp") }}</small>
          </span>
          <strong>{{ credential.lastUsedAt ? formatDateTime(credential.lastUsedAt) : t("accountSecurity.passkeyNeverUsed") }}</strong>
          <div class="credential-actions">
            <a-tag :color="credential.status === 'ACTIVE' ? 'success' : 'default'">
              {{ credential.status === "ACTIVE" ? t("common.active") : t("common.disabled") }}
            </a-tag>
            <a-button
              type="text"
              danger
              :disabled="!secondStepAvailable"
              @click="emit('deletePasskey', credential)"
            >
              <Trash2 :size="16" />{{ t("accountSecurity.deletePasskey") }}
            </a-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
