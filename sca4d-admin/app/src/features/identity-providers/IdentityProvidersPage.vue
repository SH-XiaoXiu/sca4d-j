<script setup lang="ts">
defineOptions({ name: "IdentityProvidersPage" });

import {
  createIdempotencyKey,
  isApiError,
  type IdentityProviderProtocol,
  type IdentityProviderRegistrationPolicy,
  type IdentityProviderStatus,
  type IdentityProviderView,
  type LdapTransportSecurity,
  type OAuth2ProfilePreviewView
} from "@sca4d/admin-common";
import { Eye, Network, Pencil, PlugZap, Plus, Power, Trash2 } from "lucide-vue-next";
import { message, Modal, type FormInstance } from "ant-design-vue";
import { computed, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute } from "vue-router";
import { systemIdentityProviderApi, tenantIdentityProviderApi } from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const auth = useSessionStore();
const { t } = useI18n();
const formRef = ref<FormInstance>();
const loading = ref(false);
const submitting = ref(false);
const testing = ref(false);
const editorOpen = ref(false);
const editing = ref<IdentityProviderView | null>(null);
const createKey = ref<string | null>(null);
const providers = ref<IdentityProviderView[]>([]);
const preview = ref<OAuth2ProfilePreviewView | null>(null);
const protocolConfigured = ref(false);
const tenantId = computed(() => typeof route.params.tenantId === "string" ? route.params.tenantId : null);
const api = computed(() => tenantId.value
  ? tenantIdentityProviderApi(tenantId.value)
  : systemIdentityProviderApi);
const canManage = computed(() => auth.hasPermission(
  tenantId.value ? "tenant:identity-providers:manage" : "system:identity-providers:manage"
));
const title = computed(() => tenantId.value
  ? t("identityProvider.tenantTitle")
  : t("identityProvider.systemTitle"));
const columns = computed(() => [
  { title: t("identityProvider.provider"), key: "provider", width: 260 },
  { title: t("identityProvider.protocol"), dataIndex: "protocol", width: 120 },
  { title: t("identityProvider.registration"), dataIndex: "registrationPolicy", width: 140 },
  { title: t("common.status"), key: "status", width: 120 },
  { title: t("common.actions"), key: "actions", width: 190, fixed: "right" as const }
]);
const form = reactive({
  code: "",
  name: "",
  protocol: "OIDC" as IdentityProviderProtocol,
  registrationPolicy: "DISABLED" as IdentityProviderRegistrationPolicy,
  emailAutoLink: false,
  issuer: "",
  authorizationUri: "",
  tokenUri: "",
  profileUri: "",
  auxiliaryProfileUri: "",
  clientId: "",
  clientSecret: "",
  scopes: "",
  subjectPath: "$.id",
  displayNamePath: "",
  emailPath: "",
  emailVerifiedPath: "",
  avatarPath: "",
  profileSample: "{}",
  auxiliaryProfileSample: "",
  ldapUrl: "",
  baseDn: "",
  serviceAccountDn: "",
  serviceAccountPassword: "",
  userSearchBase: "",
  userSearchFilter: "(uid={0})",
  externalSubjectAttribute: "entryUUID",
  displayNameAttribute: "displayName",
  emailAttribute: "mail",
  transportSecurity: "STARTTLS" as LdapTransportSecurity
});

function resetForm(): void {
  Object.assign(form, {
    code: "",
    name: "",
    protocol: "OIDC" as IdentityProviderProtocol,
    registrationPolicy: "DISABLED" as IdentityProviderRegistrationPolicy,
    emailAutoLink: false,
    issuer: "",
    authorizationUri: "",
    tokenUri: "",
    profileUri: "",
    auxiliaryProfileUri: "",
    clientId: "",
    clientSecret: "",
    scopes: "",
    subjectPath: "$.id",
    displayNamePath: "",
    emailPath: "",
    emailVerifiedPath: "",
    avatarPath: "",
    profileSample: "{}",
    auxiliaryProfileSample: "",
    ldapUrl: "",
    baseDn: "",
    serviceAccountDn: "",
    serviceAccountPassword: "",
    userSearchBase: "",
    userSearchFilter: "(uid={0})",
    externalSubjectAttribute: "entryUUID",
    displayNameAttribute: "displayName",
    emailAttribute: "mail",
    transportSecurity: "STARTTLS" as LdapTransportSecurity
  });
  preview.value = null;
  protocolConfigured.value = false;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    providers.value = await api.value.providers();
  } catch (error) {
    message.error(isApiError(error) ? error.message : t("identityProvider.loadFailed"));
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editing.value = null;
  createKey.value = createIdempotencyKey();
  resetForm();
  editorOpen.value = true;
}

async function openEdit(provider: IdentityProviderView): Promise<void> {
  editing.value = provider;
  createKey.value = null;
  resetForm();
  Object.assign(form, {
    code: provider.code,
    name: provider.name,
    protocol: provider.protocol,
    registrationPolicy: provider.registrationPolicy,
    emailAutoLink: provider.emailAutoLink
  });
  editorOpen.value = true;
  try {
    if (provider.protocol === "OIDC") {
      const configuration = await api.value.oidcConfiguration(provider.providerId);
      Object.assign(form, {
        issuer: configuration.issuer,
        clientId: configuration.clientId,
        scopes: configuration.scopes.join(" ")
      });
      protocolConfigured.value = configuration.clientSecretConfigured;
    } else if (provider.protocol === "OAUTH2") {
      const configuration = await api.value.oauth2Configuration(provider.providerId);
      Object.assign(form, {
        authorizationUri: configuration.authorizationUri,
        tokenUri: configuration.tokenUri,
        profileUri: configuration.profileUri,
        auxiliaryProfileUri: configuration.auxiliaryProfileUri ?? "",
        clientId: configuration.clientId,
        scopes: configuration.scopes.join(" "),
        subjectPath: configuration.profileMapping.subjectPath,
        displayNamePath: configuration.profileMapping.displayNamePath ?? "",
        emailPath: configuration.profileMapping.emailPath ?? "",
        emailVerifiedPath: configuration.profileMapping.emailVerifiedPath ?? "",
        avatarPath: configuration.profileMapping.avatarPath ?? ""
      });
      protocolConfigured.value = configuration.clientSecretConfigured;
    } else {
      const configuration = await api.value.ldapConfiguration(provider.providerId);
      Object.assign(form, {
        ldapUrl: configuration.url,
        baseDn: configuration.baseDn,
        serviceAccountDn: configuration.serviceAccountDn ?? "",
        userSearchBase: configuration.userSearchBase,
        userSearchFilter: configuration.userSearchFilter,
        externalSubjectAttribute: configuration.externalSubjectAttribute,
        displayNameAttribute: configuration.displayNameAttribute ?? "",
        emailAttribute: configuration.emailAttribute ?? "",
        transportSecurity: configuration.transportSecurity
      });
      protocolConfigured.value = configuration.serviceAccountPasswordConfigured;
    }
  } catch (error) {
    if (!isApiError(error) || error.httpStatus !== 404) {
      message.error(isApiError(error) ? error.message : t("identityProvider.configurationLoadFailed"));
    }
  }
}

function scopes(): string[] {
  return [...new Set(form.scopes.split(/\s+/).map((scope) => scope.trim()).filter(Boolean))];
}

function registrationLabel(policy: IdentityProviderRegistrationPolicy): string {
  return policy === "JIT"
    ? t("identityProvider.registrationJit")
    : t("identityProvider.registrationDisabled");
}

async function save(): Promise<void> {
  if (!canManage.value) return;
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  submitting.value = true;
  try {
    let provider: IdentityProviderView;
    if (editing.value) {
      provider = (await api.value.update(
        editing.value.providerId,
        {
          name: form.name.trim(),
          registrationPolicy: form.registrationPolicy,
          emailAutoLink: form.emailAutoLink
        },
        editing.value.version
      )).data;
    } else {
      createKey.value ??= createIdempotencyKey();
      provider = (await api.value.create({
        code: form.code.trim(),
        name: form.name.trim(),
        protocol: form.protocol,
        registrationPolicy: form.registrationPolicy,
        emailAutoLink: form.emailAutoLink
      }, createKey.value)).data;
    }
    editing.value = provider;
    createKey.value = null;
    if (provider.protocol === "OIDC") {
      await api.value.replaceOidcConfiguration(provider.providerId, {
        issuer: form.issuer.trim(),
        clientId: form.clientId.trim(),
        ...(form.clientSecret ? { clientSecret: form.clientSecret } : {}),
        scopes: scopes()
      }, provider.version);
    } else if (provider.protocol === "OAUTH2") {
      await api.value.replaceOAuth2Configuration(provider.providerId, {
        authorizationUri: form.authorizationUri.trim(),
        tokenUri: form.tokenUri.trim(),
        profileUri: form.profileUri.trim(),
        ...(form.auxiliaryProfileUri.trim() ? { auxiliaryProfileUri: form.auxiliaryProfileUri.trim() } : {}),
        clientId: form.clientId.trim(),
        ...(form.clientSecret ? { clientSecret: form.clientSecret } : {}),
        scopes: scopes(),
        profileMapping: {
          subjectPath: form.subjectPath.trim(),
          ...(form.displayNamePath.trim() ? { displayNamePath: form.displayNamePath.trim() } : {}),
          ...(form.emailPath.trim() ? { emailPath: form.emailPath.trim() } : {}),
          ...(form.emailVerifiedPath.trim() ? { emailVerifiedPath: form.emailVerifiedPath.trim() } : {}),
          ...(form.avatarPath.trim() ? { avatarPath: form.avatarPath.trim() } : {})
        }
      }, provider.version);
    } else {
      await api.value.replaceLdapConfiguration(provider.providerId, {
        url: form.ldapUrl.trim(),
        baseDn: form.baseDn.trim(),
        ...(form.serviceAccountDn.trim() ? { serviceAccountDn: form.serviceAccountDn.trim() } : {}),
        ...(form.serviceAccountPassword ? { serviceAccountPassword: form.serviceAccountPassword } : {}),
        userSearchBase: form.userSearchBase.trim(),
        userSearchFilter: form.userSearchFilter.trim(),
        externalSubjectAttribute: form.externalSubjectAttribute.trim(),
        ...(form.displayNameAttribute.trim() ? { displayNameAttribute: form.displayNameAttribute.trim() } : {}),
        ...(form.emailAttribute.trim() ? { emailAttribute: form.emailAttribute.trim() } : {}),
        transportSecurity: form.transportSecurity
      }, provider.version);
    }
    form.clientSecret = "";
    form.serviceAccountPassword = "";
    editorOpen.value = false;
    editing.value = null;
    message.success(t("identityProvider.saveSuccess"));
    await load();
  } catch (error) {
    if (isApiError(error) && (error.httpStatus === 409 || error.httpStatus === 412)) {
      const providerId = editing.value?.providerId;
      await load();
      if (providerId) {
        const latest = providers.value.find((provider) => provider.providerId === providerId);
        if (!latest) {
          editorOpen.value = false;
          editing.value = null;
        } else {
          editing.value = latest;
        }
      }
      message.warning(t("identityProvider.conflict"));
      return;
    }
    message.error(isApiError(error) ? error.message : t("identityProvider.saveFailed"));
  } finally {
    submitting.value = false;
  }
}

async function testProtocol(): Promise<void> {
  const provider = editing.value;
  if (!provider) return;
  testing.value = true;
  try {
    if (provider.protocol === "OIDC") {
      await api.value.discoverOidc(provider.providerId);
    } else if (provider.protocol === "LDAP") {
      await api.value.testLdapConnection(provider.providerId);
    }
    message.success(t("identityProvider.testSuccess"));
  } catch (error) {
    message.error(isApiError(error) ? error.message : t("identityProvider.testFailed"));
  } finally {
    testing.value = false;
  }
}

async function previewProfile(): Promise<void> {
  const provider = editing.value;
  if (!provider || provider.protocol !== "OAUTH2") return;
  testing.value = true;
  try {
    const profile = JSON.parse(form.profileSample) as unknown;
    const auxiliary = form.auxiliaryProfileSample.trim()
      ? JSON.parse(form.auxiliaryProfileSample) as unknown
      : undefined;
    preview.value = await api.value.previewOAuth2Profile(provider.providerId, profile, auxiliary);
  } catch (error) {
    message.error(isApiError(error) ? error.message : t("identityProvider.previewFailed"));
  } finally {
    testing.value = false;
  }
}

function changeStatus(provider: IdentityProviderView): void {
  const next: IdentityProviderStatus = provider.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  Modal.confirm({
    title: t("identityProvider.statusTitle", { name: provider.name }),
    okText: next === "ACTIVE" ? t("common.active") : t("common.disabled"),
    cancelText: t("common.cancel"),
    onOk: async () => {
      try {
        await api.value.changeStatus(provider.providerId, next, provider.version);
        await load();
        message.success(t("identityProvider.statusSuccess"));
      } catch (error) {
        if (isApiError(error) && (error.httpStatus === 409 || error.httpStatus === 412)) {
          await load();
          message.warning(t("identityProvider.conflict"));
          return;
        }
        message.error(isApiError(error) ? error.message : t("identityProvider.statusFailed"));
      }
    }
  });
}

function remove(provider: IdentityProviderView): void {
  Modal.confirm({
    title: t("identityProvider.deleteTitle", { name: provider.name }),
    content: t("identityProvider.deleteWarning"),
    okText: t("common.delete"),
    cancelText: t("common.cancel"),
    okType: "danger",
    onOk: async () => {
      try {
        await api.value.delete(provider.providerId, provider.version);
        await load();
        message.success(t("identityProvider.deleteSuccess"));
      } catch (error) {
        if (isApiError(error) && (error.httpStatus === 409 || error.httpStatus === 412)) {
          await load();
          message.warning(t("identityProvider.conflict"));
          return;
        }
        message.error(isApiError(error) ? error.message : t("identityProvider.deleteFailed"));
      }
    }
  });
}

onMounted(load);
</script>

<template>
  <div>
    <PageHeader :title="title">
      <template #actions>
        <a-button
          v-if="canManage"
          type="primary"
          @click="openCreate"
        >
          <Plus :size="16" />{{ t("identityProvider.create") }}
        </a-button>
      </template>
    </PageHeader>

    <div class="page-panel provider-list">
      <a-table
        class="desktop-only"
        row-key="providerId"
        :columns="columns"
        :data-source="providers"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 860 }"
      >
        <template #emptyText>
          <a-empty :description="t('identityProvider.empty')">
            <a-button
              v-if="canManage"
              type="primary"
              @click="openCreate"
            >
              <Plus :size="16" />{{ t("identityProvider.create") }}
            </a-button>
          </a-empty>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'provider'">
            <button
              type="button"
              class="provider-name"
              @click="openEdit(record as IdentityProviderView)"
            >
              <Network :size="18" />
              <span><strong>{{ record.name }}</strong><small>{{ record.code }}</small></span>
            </button>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
              {{ record.status === "ACTIVE" ? t("common.active") : t("common.disabled") }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'registration'">
            {{ registrationLabel(record.registrationPolicy) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <div class="row-actions">
              <RowActionButton
                :label="t('common.edit')"
                @click="openEdit(record as IdentityProviderView)"
              >
                <Pencil :size="16" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                :label="record.status === 'ACTIVE' ? t('common.disabled') : t('common.active')"
                @click="changeStatus(record as IdentityProviderView)"
              >
                <Power :size="16" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage && record.status === 'DISABLED'"
                danger
                :label="t('common.delete')"
                @click="remove(record as IdentityProviderView)"
              >
                <Trash2 :size="16" />
              </RowActionButton>
            </div>
          </template>
        </template>
      </a-table>

      <div class="mobile-only">
        <a-spin :spinning="loading">
          <a-empty
            v-if="!loading && providers.length === 0"
            :description="t('identityProvider.empty')"
          >
            <a-button
              v-if="canManage"
              type="primary"
              @click="openCreate"
            >
              <Plus :size="16" />{{ t("identityProvider.create") }}
            </a-button>
          </a-empty>
          <div
            v-else
            class="mobile-card-list"
          >
            <article
              v-for="provider in providers"
              :key="provider.providerId"
              class="mobile-record"
            >
              <div class="mobile-record__header">
                <button
                  type="button"
                  class="provider-name mobile-provider-name"
                  @click="openEdit(provider)"
                >
                  <Network :size="18" />
                  <span><strong>{{ provider.name }}</strong><small>{{ provider.code }}</small></span>
                </button>
                <a-tag :color="provider.status === 'ACTIVE' ? 'success' : 'default'">
                  {{ provider.status === "ACTIVE" ? t("common.active") : t("common.disabled") }}
                </a-tag>
              </div>
              <div class="mobile-record__meta">
                <span>{{ t("identityProvider.protocol") }}</span><strong>{{ provider.protocol }}</strong>
                <span>{{ t("identityProvider.registration") }}</span><strong>{{ registrationLabel(provider.registrationPolicy) }}</strong>
              </div>
              <div class="mobile-record__actions">
                <RowActionButton
                  :label="t('common.edit')"
                  @click="openEdit(provider)"
                >
                  <Pencil :size="16" />
                </RowActionButton>
                <RowActionButton
                  v-if="canManage"
                  :label="provider.status === 'ACTIVE' ? t('common.disabled') : t('common.active')"
                  @click="changeStatus(provider)"
                >
                  <Power :size="16" />
                </RowActionButton>
                <RowActionButton
                  v-if="canManage && provider.status === 'DISABLED'"
                  danger
                  :label="t('common.delete')"
                  @click="remove(provider)"
                >
                  <Trash2 :size="16" />
                </RowActionButton>
              </div>
            </article>
          </div>
        </a-spin>
      </div>
    </div>

    <a-drawer
      v-model:open="editorOpen"
      :title="editing ? t('identityProvider.edit') : t('identityProvider.create')"
      width="min(760px, 100vw)"
      :destroy-on-close="false"
    >
      <a-form
        ref="formRef"
        layout="vertical"
        :model="form"
        :disabled="!canManage"
        @finish="save"
      >
        <div class="form-grid">
          <a-form-item
            name="code"
            :label="t('identityProvider.code')"
            :rules="[{ required: true, pattern: /^[a-z](?:[a-z0-9-]{0,62}[a-z0-9])?$/, message: t('identityProvider.codeInvalid') }]"
          >
            <a-input
              v-model:value="form.code"
              :disabled="Boolean(editing)"
            />
          </a-form-item>
          <a-form-item
            name="name"
            :label="t('identityProvider.name')"
            :rules="[{ required: true, whitespace: true, message: t('identityProvider.nameRequired') }]"
          >
            <a-input
              v-model:value="form.name"
              :maxlength="128"
            />
          </a-form-item>
          <a-form-item
            name="protocol"
            :label="t('identityProvider.protocol')"
          >
            <a-segmented
              v-model:value="form.protocol"
              block
              :disabled="Boolean(editing)"
              :options="['OIDC', 'OAUTH2', 'LDAP']"
            />
          </a-form-item>
          <a-form-item
            name="registrationPolicy"
            :label="t('identityProvider.registration')"
          >
            <a-select v-model:value="form.registrationPolicy">
              <a-select-option value="DISABLED">
                {{ t("identityProvider.registrationDisabled") }}
              </a-select-option>
              <a-select-option value="JIT">
                JIT
              </a-select-option>
            </a-select>
          </a-form-item>
        </div>
        <a-form-item>
          <a-checkbox v-model:checked="form.emailAutoLink">
            {{ t("identityProvider.emailAutoLink") }}
          </a-checkbox>
        </a-form-item>

        <a-divider>{{ form.protocol }}</a-divider>
        <template v-if="form.protocol === 'OIDC'">
          <a-form-item
            name="issuer"
            label="Issuer"
            :rules="[{ required: true }]"
          >
            <a-input
              v-model:value="form.issuer"
              placeholder="https://idp.example.com"
            />
          </a-form-item>
          <div class="form-grid">
            <a-form-item
              name="clientId"
              label="Client ID"
              :rules="[{ required: true }]"
            >
              <a-input v-model:value="form.clientId" />
            </a-form-item>
            <a-form-item
              name="clientSecret"
              label="Client Secret"
              :rules="[{ required: !protocolConfigured }]"
            >
              <a-input-password
                v-model:value="form.clientSecret"
                autocomplete="new-password"
              />
            </a-form-item>
          </div>
          <a-form-item
            name="scopes"
            label="Scopes"
            :rules="[{ required: true }]"
          >
            <a-input
              v-model:value="form.scopes"
              placeholder="openid profile email"
            />
          </a-form-item>
        </template>

        <template v-else-if="form.protocol === 'OAUTH2'">
          <a-form-item
            name="authorizationUri"
            label="Authorization URI"
            :rules="[{ required: true }]"
          >
            <a-input v-model:value="form.authorizationUri" />
          </a-form-item>
          <a-form-item
            name="tokenUri"
            label="Token URI"
            :rules="[{ required: true }]"
          >
            <a-input v-model:value="form.tokenUri" />
          </a-form-item>
          <a-form-item
            name="profileUri"
            label="Profile URI"
            :rules="[{ required: true }]"
          >
            <a-input v-model:value="form.profileUri" />
          </a-form-item>
          <a-form-item
            name="auxiliaryProfileUri"
            :label="t('identityProvider.auxiliaryProfileUri')"
          >
            <a-input v-model:value="form.auxiliaryProfileUri" />
          </a-form-item>
          <div class="form-grid">
            <a-form-item
              name="clientId"
              label="Client ID"
              :rules="[{ required: true }]"
            >
              <a-input v-model:value="form.clientId" />
            </a-form-item>
            <a-form-item
              name="clientSecret"
              label="Client Secret"
              :rules="[{ required: !protocolConfigured }]"
            >
              <a-input-password
                v-model:value="form.clientSecret"
                autocomplete="new-password"
              />
            </a-form-item>
          </div>
          <a-form-item
            name="scopes"
            label="Scopes"
            :rules="[{ required: true }]"
          >
            <a-input v-model:value="form.scopes" />
          </a-form-item>
          <div class="form-grid mapping-grid">
            <a-form-item
              name="subjectPath"
              label="subject JSONPath"
              :rules="[{ required: true }]"
            >
              <a-input v-model:value="form.subjectPath" />
            </a-form-item>
            <a-form-item
              name="displayNamePath"
              label="displayName JSONPath"
            >
              <a-input v-model:value="form.displayNamePath" />
            </a-form-item>
            <a-form-item
              name="emailPath"
              label="email JSONPath"
            >
              <a-input v-model:value="form.emailPath" />
            </a-form-item>
            <a-form-item
              name="emailVerifiedPath"
              label="emailVerified JSONPath"
            >
              <a-input v-model:value="form.emailVerifiedPath" />
            </a-form-item>
            <a-form-item
              name="avatarPath"
              label="avatar JSONPath"
            >
              <a-input v-model:value="form.avatarPath" />
            </a-form-item>
          </div>
          <a-form-item :label="t('identityProvider.profileSample')">
            <a-textarea
              v-model:value="form.profileSample"
              :rows="5"
            />
          </a-form-item>
          <a-form-item :label="t('identityProvider.auxiliaryProfileSample')">
            <a-textarea
              v-model:value="form.auxiliaryProfileSample"
              :rows="3"
            />
          </a-form-item>
          <a-descriptions
            v-if="preview"
            bordered
            size="small"
            :column="1"
            class="preview-result"
          >
            <a-descriptions-item label="subject">
              {{ preview.subject ?? "-" }}
            </a-descriptions-item>
            <a-descriptions-item label="displayName">
              {{ preview.displayName ?? "-" }}
            </a-descriptions-item>
            <a-descriptions-item label="email">
              {{ preview.email ?? "-" }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('identityProvider.missingFields')">
              {{ preview.missingFields.map((field) => field.value).join(", ") || "-" }}
            </a-descriptions-item>
          </a-descriptions>
        </template>

        <template v-else>
          <div class="form-grid">
            <a-form-item
              name="ldapUrl"
              label="LDAP URI"
              :rules="[{ required: true }]"
            >
              <a-input
                v-model:value="form.ldapUrl"
                placeholder="ldap://directory.example.com:389"
              />
            </a-form-item>
            <a-form-item
              name="transportSecurity"
              :label="t('identityProvider.transportSecurity')"
            >
              <a-segmented
                v-model:value="form.transportSecurity"
                block
                :options="['STARTTLS', 'LDAPS']"
              />
            </a-form-item>
          </div>
          <a-form-item
            name="baseDn"
            label="Base DN"
            :rules="[{ required: true }]"
          >
            <a-input
              v-model:value="form.baseDn"
              placeholder="dc=example,dc=com"
            />
          </a-form-item>
          <div class="form-grid">
            <a-form-item
              name="serviceAccountDn"
              :label="t('identityProvider.serviceAccountDn')"
            >
              <a-input v-model:value="form.serviceAccountDn" />
            </a-form-item>
            <a-form-item
              name="serviceAccountPassword"
              :label="t('identityProvider.serviceAccountPassword')"
              :rules="[{ required: Boolean(form.serviceAccountDn) && !protocolConfigured }]"
            >
              <a-input-password
                v-model:value="form.serviceAccountPassword"
                autocomplete="new-password"
              />
            </a-form-item>
          </div>
          <div class="form-grid">
            <a-form-item
              name="userSearchBase"
              :label="t('identityProvider.userSearchBase')"
            >
              <a-input v-model:value="form.userSearchBase" />
            </a-form-item>
            <a-form-item
              name="userSearchFilter"
              :label="t('identityProvider.userSearchFilter')"
              :rules="[{ required: true }]"
            >
              <a-input v-model:value="form.userSearchFilter" />
            </a-form-item>
          </div>
          <div class="form-grid mapping-grid">
            <a-form-item
              name="externalSubjectAttribute"
              :label="t('identityProvider.externalSubjectAttribute')"
              :rules="[{ required: true }]"
            >
              <a-input v-model:value="form.externalSubjectAttribute" />
            </a-form-item>
            <a-form-item
              name="displayNameAttribute"
              :label="t('identityProvider.displayNameAttribute')"
            >
              <a-input v-model:value="form.displayNameAttribute" />
            </a-form-item>
            <a-form-item
              name="emailAttribute"
              :label="t('identityProvider.emailAttribute')"
            >
              <a-input v-model:value="form.emailAttribute" />
            </a-form-item>
          </div>
        </template>

        <div class="drawer-actions">
          <a-button
            v-if="editing && form.protocol !== 'OAUTH2'"
            :loading="testing"
            @click="testProtocol"
          >
            <PlugZap :size="16" />{{ t("identityProvider.test") }}
          </a-button>
          <a-button
            v-if="editing && form.protocol === 'OAUTH2'"
            :loading="testing"
            @click="previewProfile"
          >
            <Eye :size="16" />{{ t("identityProvider.preview") }}
          </a-button>
          <a-button @click="editorOpen = false">
            {{ t("common.cancel") }}
          </a-button>
          <a-button
            v-if="canManage"
            type="primary"
            html-type="submit"
            :loading="submitting"
          >
            {{ t("common.save") }}
          </a-button>
        </div>
      </a-form>
    </a-drawer>
  </div>
</template>

<style scoped>
.provider-list {
  overflow: hidden;
}

.provider-name {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 0;
  padding: 0;
  color: var(--app-text);
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.provider-name span {
  display: grid;
  gap: 2px;
}

.provider-name small {
  color: var(--app-text-secondary);
}

.mobile-provider-name {
  min-height: 40px;
}

.row-actions,
.drawer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.mapping-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.preview-result {
  margin-bottom: 20px;
}

.drawer-actions {
  position: sticky;
  bottom: -24px;
  z-index: 2;
  min-height: 64px;
  margin: 8px -24px -24px;
  padding: 10px 24px;
  border-top: 1px solid var(--app-border);
  background: var(--app-surface);
}

@media (max-width: 600px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .drawer-actions {
    flex-wrap: wrap;
  }

  .drawer-actions button {
    min-height: 44px;
  }
}
</style>
