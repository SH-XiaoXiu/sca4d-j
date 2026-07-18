<script setup lang="ts">
import {
  type ExternalIdentityView,
  type IdentityProviderProtocol,
  type LdapAvailableProvider,
  type OAuth2AvailableProvider,
  type OidcAvailableProvider
} from "@sca4d/admin-common";
import { Link2, Unlink } from "lucide-vue-next";
import { computed } from "vue";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  currentAccount: boolean;
  secondStepAvailable: boolean;
  identities: ExternalIdentityView[];
  loading: boolean;
  oidcProviders: OidcAvailableProvider[];
  oauth2Providers: OAuth2AvailableProvider[];
  ldapProviders: LdapAvailableProvider[];
}>();
const emit = defineEmits<{
  link: [protocol: IdentityProviderProtocol];
  unlink: [identity: ExternalIdentityView];
}>();
const { t } = useI18n();
const linkedProviderCodes = computed(
  () => new Set(props.identities.map((identity) => identity.providerCode))
);

function providerAvailable(providers: Array<{ code: string }>): boolean {
  return providers.some((provider) => !linkedProviderCodes.value.has(provider.code));
}
</script>

<template>
  <section class="security-section">
    <div class="section-heading section-heading--actions">
      <div>
        <Link2 :size="18" />
        <h3>{{ t("accountSecurity.externalIdentities") }}</h3>
      </div>
      <div
        v-if="currentAccount"
        class="credential-heading-actions"
      >
        <a-button
          v-if="providerAvailable(oidcProviders)"
          @click="emit('link', 'OIDC')"
        >
          <Link2 :size="16" />OIDC
        </a-button>
        <a-button
          v-if="providerAvailable(oauth2Providers)"
          @click="emit('link', 'OAUTH2')"
        >
          <Link2 :size="16" />OAuth2
        </a-button>
        <a-button
          v-if="providerAvailable(ldapProviders)"
          @click="emit('link', 'LDAP')"
        >
          <Link2 :size="16" />LDAP
        </a-button>
      </div>
    </div>
    <a-spin :spinning="loading">
      <a-empty
        v-if="!identities.length"
        :description="t('accountSecurity.externalIdentitiesEmpty')"
      />
      <div
        v-else
        class="fact-list"
      >
        <div
          v-for="identity in identities"
          :key="identity.identityId"
          class="fact-row"
        >
          <span class="passkey-name">
            <strong>{{ identity.providerName }}</strong>
            <small>{{ identity.protocol }} · {{ identity.externalSubject }}</small>
          </span>
          <strong>{{ identity.email || identity.displayName || t("common.unavailable") }}</strong>
          <div class="credential-actions">
            <a-tag :color="identity.providerStatus === 'ACTIVE' ? 'success' : 'default'">
              {{ identity.providerStatus === "ACTIVE" ? t("common.active") : t("common.disabled") }}
            </a-tag>
            <a-button
              type="text"
              danger
              :disabled="!secondStepAvailable"
              @click="emit('unlink', identity)"
            >
              <Unlink :size="16" />{{ t("accountSecurity.unlinkExternalIdentity") }}
            </a-button>
          </div>
        </div>
      </div>
    </a-spin>
  </section>
</template>
