<script setup lang="ts">
import type {
  FederatedProvider,
  FederatedProviderSettings,
  LocalizedFields,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import {
  Eye,
  Globe2,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
} from 'lucide-vue-next';

import { $t } from '#/locales';

import ResourceState from '../components/ResourceState.vue';
import { useI18n } from '../i18n';
import { useAdminStore } from '../store/business';

interface KeyValueEntry {
  key: string;
  value: string;
}

const store = useAdminStore();
const { language } = useI18n();
const editorOpen = ref(false);
const editorLoading = ref(false);
const editorSaving = ref(false);
const editorFormRef = ref<FormInstance>();
const editingProvider = ref<FederatedProvider | null>(null);
const statusSavingCode = ref<null | string>(null);
const deletingCode = ref<null | string>(null);
const settings = ref<FederatedProviderSettings | null>(null);
const profileMapping = ref<KeyValueEntry[]>([]);
const authorizationParameters = ref<KeyValueEntry[]>([]);

const providerForm = reactive({
  buttonText: '',
  clientAuthentication: 'CLIENT_SECRET_BASIC' as
    | 'CLIENT_SECRET_BASIC'
    | 'CLIENT_SECRET_POST'
    | 'NONE',
  clientId: '',
  clientSecret: '',
  code: '',
  displayOrder: 0,
  iconReference: '',
  issuer: '',
  loginPageVisible: true,
  name: '',
  protocol: 'OIDC' as 'OAUTH2' | 'OIDC',
  scopes: '',
  tokenEndpoint: '',
  userInfoEndpoint: '',
  authorizationEndpoint: '',
  jwksEndpoint: '',
});

const canManage = computed(
  () => store.session.capabilities.federatedProvidersManage,
);
const isCreate = computed(() => editingProvider.value === null);
const editorTitle = computed(() =>
  isCreate.value ? t('create') : t('edit'),
);
const settingsDisabled = computed(
  () => !canManage.value || editingProvider.value?.status === 'ACTIVE',
);
const providerColumns = computed(() => [
  { key: 'provider', title: t('provider'), width: 240 },
  { key: 'protocol', title: t('protocol'), width: 110 },
  { key: 'status', title: t('status'), width: 130 },
  { key: 'loginPage', title: t('loginPage'), width: 130 },
  { key: 'actions', title: t('actions'), width: 150 },
]);

onMounted(() => void reload());

/** 读取 Provider 列表并让 ResourceState 显示权限或网络失败。 */
async function reload(): Promise<void> {
  try {
    await store.loadFederatedProviders();
  } catch {
    // Controller 已保存统一错误和加载阶段。
  }
}

/** 打开新建 Provider 表单，并清除上一条配置草稿。 */
function openCreate(): void {
  editingProvider.value = null;
  settings.value = null;
  resetProviderForm();
  editorOpen.value = true;
}

/** 读取 Provider 的协议配置并打开编辑或只读窗口。 */
async function openEditor(provider: FederatedProvider): Promise<void> {
  editingProvider.value = provider;
  settings.value = null;
  resetProviderForm(provider);
  editorOpen.value = true;
  editorLoading.value = true;
  try {
    settings.value = await store.loadFederatedProviderSettings(provider.code);
    resetSettingsForm(settings.value);
    editorFormRef.value?.clearValidate();
  } catch {
    editorOpen.value = false;
    message.error(t('loadFailed'));
  } finally {
    editorLoading.value = false;
  }
}

/** 创建 Provider，或保存停用 Provider 的协议配置。 */
async function save(): Promise<void> {
  await editorFormRef.value?.validate();
  const input = settingsInput();
  if (!input) return;
  editorSaving.value = true;
  try {
    if (isCreate.value) {
      const created = await store.createFederatedProvider({
        code: providerForm.code.trim(),
        displayOrder: providerForm.displayOrder,
        iconReference: optional(providerForm.iconReference),
        i18n: providerI18n(),
        loginPageVisible: providerForm.loginPageVisible,
        protocol: providerForm.protocol,
      });
      await store.replaceFederatedProviderSettings(created, input);
      message.success(t('created'));
    } else if (editingProvider.value) {
      await store.replaceFederatedProviderSettings(editingProvider.value, input);
      message.success(t('updated'));
    }
    editorOpen.value = false;
  } catch {
    message.error(t('saveFailed'));
  } finally {
    editorSaving.value = false;
  }
}

/** 使用 Provider 当前版本启用或停用登录入口。 */
async function changeStatus(
  provider: FederatedProvider,
  active: boolean,
): Promise<void> {
  statusSavingCode.value = provider.code;
  try {
    await store.setFederatedProviderStatus(provider, active ? 'ACTIVE' : 'DISABLED');
    message.success(t('updated'));
  } catch {
    message.error(t('saveFailed'));
  } finally {
    statusSavingCode.value = null;
  }
}

/** 删除停用 Provider；后端会拒绝仍有身份绑定的 Provider。 */
async function remove(provider: FederatedProvider): Promise<void> {
  deletingCode.value = provider.code;
  try {
    await store.deleteFederatedProvider(provider);
    message.success(t('deleted'));
  } catch {
    message.error(t('deleteFailed'));
  } finally {
    deletingCode.value = null;
  }
}

/** 把配置摘要转换为编辑表单，Client Secret 永远不回填。 */
function resetSettingsForm(value: FederatedProviderSettings): void {
  providerForm.authorizationEndpoint = value.authorizationEndpoint ?? '';
  providerForm.clientAuthentication = value.clientAuthentication;
  providerForm.clientId = value.clientId ?? '';
  providerForm.clientSecret = '';
  providerForm.issuer = value.issuer ?? '';
  providerForm.jwksEndpoint = value.jwksEndpoint ?? '';
  providerForm.scopes = value.scopes.join(' ');
  providerForm.tokenEndpoint = value.tokenEndpoint ?? '';
  providerForm.userInfoEndpoint = value.userInfoEndpoint ?? '';
  profileMapping.value = entries(value.profileMapping);
  authorizationParameters.value = entries(value.authorizationParameters);
}

/** 建立新建表单的默认值，或填入当前语言下的 Provider 展示信息。 */
function resetProviderForm(provider?: FederatedProvider): void {
  providerForm.buttonText = provider?.buttonText ?? '';
  providerForm.code = provider?.code ?? '';
  providerForm.displayOrder = provider?.displayOrder ?? 0;
  providerForm.iconReference = provider?.iconReference ?? '';
  providerForm.loginPageVisible = provider?.loginPageVisible ?? true;
  providerForm.name = provider?.name ?? '';
  providerForm.protocol = provider?.protocol ?? 'OIDC';
  providerForm.clientAuthentication = 'CLIENT_SECRET_BASIC';
  providerForm.clientId = '';
  providerForm.clientSecret = '';
  providerForm.issuer = '';
  providerForm.authorizationEndpoint = '';
  providerForm.tokenEndpoint = '';
  providerForm.userInfoEndpoint = '';
  providerForm.jwksEndpoint = '';
  providerForm.scopes = '';
  profileMapping.value = [];
  authorizationParameters.value = [];
}

/** 将表单中的端点、作用域和 JSONPath 映射转换为后端请求。 */
function settingsInput() {
  const profile = entryMap(profileMapping.value);
  const parameters = entryMap(authorizationParameters.value);
  if (!profile || !parameters) return null;
  return {
    authorizationEndpoint: optional(providerForm.authorizationEndpoint),
    authorizationParameters: parameters,
    clientAuthentication: providerForm.clientAuthentication,
    clientId: optional(providerForm.clientId),
    clientSecret: optional(providerForm.clientSecret),
    issuer: optional(providerForm.issuer),
    jwksEndpoint: optional(providerForm.jwksEndpoint),
    profileMapping: profile,
    scopes: providerForm.scopes
      .split(/[\s,]+/u)
      .map((value) => value.trim())
      .filter(Boolean),
    tokenEndpoint: optional(providerForm.tokenEndpoint),
    userInfoEndpoint: optional(providerForm.userInfoEndpoint),
  };
}

/** 使用应用支持的 Locale 生成完整 Provider 文案，默认 Locale 缺失时回退到当前输入。 */
function providerI18n(): LocalizedFields {
  const catalog = store.federatedProviders.localeCatalog;
  const locales = new Set(catalog?.supportedLocales);
  locales.add(language.value);
  if (catalog?.defaultLocale) locales.add(catalog.defaultLocale);
  if (locales.size === 0) locales.add('zh-CN');
  return Object.fromEntries(
    [...locales].map((locale) => [
      locale,
      {
        buttonText: providerForm.buttonText.trim(),
        name: providerForm.name.trim(),
      },
    ]),
  );
}

/** 把键值行转换为对象并拒绝空键和重复键。 */
function entryMap(rows: readonly KeyValueEntry[]): null | Record<string, string> {
  const result: Record<string, string> = {};
  for (const row of rows) {
    const key = row.key.trim();
    const value = row.value.trim();
    if (!key && !value) continue;
    if (!key || !value || key in result) {
      message.error(t('invalidMapping'));
      return null;
    }
    result[key] = value;
  }
  return result;
}

function entries(value: Readonly<Record<string, string>>): KeyValueEntry[] {
  return Object.entries(value).map(([key, entryValue]) => ({
    key,
    value: entryValue,
  }));
}

function optional(value: string): null | string {
  const result = value.trim();
  return result || null;
}

function addEntry(target: KeyValueEntry[]): void {
  target.push({ key: '', value: '' });
}

function removeEntry(target: KeyValueEntry[], index: number): void {
  target.splice(index, 1);
}

function t(key: string): string {
  return $t(`page.federatedProviders.${key}`);
}
</script>

<template>
  <Page auto-content-height>
    <ResourceState
      :error="store.federatedProviders.error"
      :phase="store.federatedProviders.phase"
      @retry="reload"
    >
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-2 text-base font-medium">
          <Globe2 :size="19" aria-hidden="true" />
          <span>{{ t('title') }}</span>
        </div>
        <div class="flex items-center gap-2">
          <a-tooltip :title="t('refresh')">
            <a-button :aria-label="t('refresh')" @click="reload">
              <RefreshCw :size="17" aria-hidden="true" />
            </a-button>
          </a-tooltip>
          <a-button v-if="canManage" type="primary" @click="openCreate">
            <Plus :size="17" aria-hidden="true" />{{ t('create') }}
          </a-button>
        </div>
      </div>

      <a-table
        :columns="providerColumns"
        :data-source="store.federatedProviders.providers"
        :pagination="false"
        row-key="code"
        :scroll="{ x: 800 }"
      >
        <template #bodyCell="{ column, record }">
          <div v-if="column.key === 'provider'" class="flex min-w-0 flex-col">
            <strong class="truncate">{{ record.name }}</strong>
            <span class="text-muted-foreground truncate text-xs">
              {{ record.code }} · {{ record.buttonText }}
            </span>
          </div>
          <a-tag v-else-if="column.key === 'protocol'" color="blue">
            {{ record.protocol === 'OIDC' ? t('oidc') : t('oauth2') }}
          </a-tag>
          <template v-else-if="column.key === 'status'">
            <a-switch
              v-if="canManage"
              :checked="record.status === 'ACTIVE'"
              :checked-children="t('active')"
              :disabled="statusSavingCode !== null"
              :loading="statusSavingCode === record.code"
              :un-checked-children="t('disabled')"
              :aria-label="`${record.name} ${record.status === 'ACTIVE' ? t('active') : t('disabled')}`"
              @change="changeStatus(record, $event as boolean)"
            />
            <a-tag v-else :color="record.status === 'ACTIVE' ? 'green' : 'default'">
              {{ record.status === 'ACTIVE' ? t('active') : t('disabled') }}
            </a-tag>
          </template>
          <a-tag v-else-if="column.key === 'loginPage'" :color="record.loginPageVisible ? 'cyan' : 'default'">
            {{ record.loginPageVisible ? t('visible') : t('hidden') }}
          </a-tag>
          <div v-else-if="column.key === 'actions'" class="flex justify-center gap-1">
            <a-tooltip :title="canManage ? t('edit') : t('view')">
              <a-button
                type="text"
                :aria-label="canManage ? t('edit') : t('view')"
                @click="openEditor(record)"
              >
                <Pencil v-if="canManage" :size="17" aria-hidden="true" />
                <Eye v-else :size="17" aria-hidden="true" />
              </a-button>
            </a-tooltip>
            <a-popconfirm
              v-if="canManage"
              :cancel-text="t('cancel')"
              :disabled="record.status === 'ACTIVE'"
              :ok-text="t('delete')"
              :title="t('confirmDelete')"
              @confirm="remove(record)"
            >
              <a-tooltip :title="t('delete')">
                <a-button
                  danger
                  type="text"
                  :aria-label="t('delete')"
                  :disabled="record.status === 'ACTIVE' || deletingCode !== null"
                  :loading="deletingCode === record.code"
                >
                  <Trash2 :size="17" aria-hidden="true" />
                </a-button>
              </a-tooltip>
            </a-popconfirm>
          </div>
        </template>
      </a-table>
    </ResourceState>

    <a-drawer
      v-model:open="editorOpen"
      :title="editorTitle"
      width="min(720px, 100vw)"
    >
      <a-spin :spinning="editorLoading">
        <a-form
          ref="editorFormRef"
          layout="vertical"
          :model="providerForm"
          :disabled="!canManage || settingsDisabled"
        >
          <a-alert
            v-if="editingProvider?.status === 'ACTIVE'"
            class="mb-4"
            :message="t('activeReadOnly')"
            show-icon
            type="info"
          />
          <div class="grid grid-cols-1 gap-x-4 sm:grid-cols-[1fr_140px]">
            <a-form-item
              name="code"
              :label="t('code')"
              :rules="[
                { required: true, whitespace: true, message: t('required') },
                { pattern: /^[a-z][a-z0-9-]{1,63}$/u, message: t('invalidCode') },
              ]"
            >
              <a-input v-model:value="providerForm.code" :disabled="!isCreate" :maxlength="64" />
            </a-form-item>
            <a-form-item name="protocol" :label="t('protocol')">
              <a-select v-model:value="providerForm.protocol" :disabled="!isCreate">
                <a-select-option value="OIDC">{{ t('oidc') }}</a-select-option>
                <a-select-option value="OAUTH2">{{ t('oauth2') }}</a-select-option>
              </a-select>
            </a-form-item>
          </div>
          <div class="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
            <a-form-item
              name="name"
              :label="t('name')"
              :rules="[{ required: true, whitespace: true, message: t('required') }]"
            >
              <a-input v-model:value="providerForm.name" :disabled="!isCreate" :maxlength="128" />
            </a-form-item>
            <a-form-item
              name="buttonText"
              :label="t('buttonText')"
              :rules="[{ required: true, whitespace: true, message: t('required') }]"
            >
              <a-input v-model:value="providerForm.buttonText" :disabled="!isCreate" :maxlength="128" />
            </a-form-item>
          </div>
          <div class="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
            <a-form-item name="iconReference" :label="t('iconReference')">
              <a-input v-model:value="providerForm.iconReference" :disabled="!isCreate" :placeholder="t('iconPlaceholder')" />
            </a-form-item>
            <a-form-item name="displayOrder" :label="t('displayOrder')">
              <a-input-number v-model:value="providerForm.displayOrder" class="w-full" :disabled="!isCreate" :min="0" :max="9999" />
            </a-form-item>
          </div>
          <a-form-item name="clientId" :label="t('clientId')" :rules="[{ required: true, whitespace: true, message: t('required') }]">
            <a-input v-model:value="providerForm.clientId" :autocomplete="isCreate ? 'off' : undefined" />
          </a-form-item>
          <a-form-item name="clientSecret" :label="t('clientSecret')">
            <a-input-password v-model:value="providerForm.clientSecret" :placeholder="settings?.clientSecretConfigured ? t('secretKeepHint') : ''" autocomplete="new-password" />
          </a-form-item>
          <a-form-item name="clientAuthentication" :label="t('clientAuthentication')">
            <a-select v-model:value="providerForm.clientAuthentication">
              <a-select-option value="CLIENT_SECRET_BASIC">{{ t('clientSecretBasic') }}</a-select-option>
              <a-select-option value="CLIENT_SECRET_POST">{{ t('clientSecretPost') }}</a-select-option>
              <a-select-option value="NONE">{{ t('clientAuthenticationNone') }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item name="scopes" :label="t('scopes')" :extra="t('scopesHint')">
            <a-textarea v-model:value="providerForm.scopes" :auto-size="{ minRows: 2, maxRows: 4 }" />
          </a-form-item>
          <a-form-item v-if="providerForm.protocol === 'OIDC'" name="issuer" :label="t('issuer')">
            <a-input v-model:value="providerForm.issuer" :placeholder="t('issuerPlaceholder')" />
          </a-form-item>
          <template v-else>
            <a-form-item name="authorizationEndpoint" :label="t('authorizationEndpoint')">
              <a-input v-model:value="providerForm.authorizationEndpoint" />
            </a-form-item>
            <a-form-item name="tokenEndpoint" :label="t('tokenEndpoint')">
              <a-input v-model:value="providerForm.tokenEndpoint" />
            </a-form-item>
            <a-form-item name="userInfoEndpoint" :label="t('userInfoEndpoint')">
              <a-input v-model:value="providerForm.userInfoEndpoint" />
            </a-form-item>
            <a-form-item name="profileMapping" :label="t('profileMapping')" :extra="t('profileMappingHint')">
              <div class="flex flex-col gap-2">
                <div v-for="(entry, index) in profileMapping" :key="index" class="flex gap-2">
                  <a-input v-model:value="entry.key" :placeholder="t('mappingKey')" />
                  <a-input v-model:value="entry.value" :placeholder="t('mappingValue')" />
                  <a-button type="text" :aria-label="t('removeMapping')" @click="removeEntry(profileMapping, index)">{{ t('remove') }}</a-button>
                </div>
                <a-button class="self-start" @click="addEntry(profileMapping)"><Plus :size="16" aria-hidden="true" />{{ t('addMapping') }}</a-button>
              </div>
            </a-form-item>
          </template>
          <a-form-item name="authorizationParameters" :label="t('authorizationParameters')" :extra="t('authorizationParametersHint')">
            <div class="flex flex-col gap-2">
              <div v-for="(entry, index) in authorizationParameters" :key="index" class="flex gap-2">
                <a-input v-model:value="entry.key" :placeholder="t('mappingKey')" />
                <a-input v-model:value="entry.value" :placeholder="t('mappingValue')" />
                <a-button type="text" :aria-label="t('removeMapping')" @click="removeEntry(authorizationParameters, index)">{{ t('remove') }}</a-button>
              </div>
              <a-button class="self-start" @click="addEntry(authorizationParameters)"><Plus :size="16" aria-hidden="true" />{{ t('addMapping') }}</a-button>
            </div>
          </a-form-item>
        </a-form>
      </a-spin>
      <template #footer>
        <div class="flex justify-end gap-2">
          <a-button @click="editorOpen = false">{{ t('cancel') }}</a-button>
          <a-button v-if="canManage && !settingsDisabled" type="primary" :loading="editorSaving" @click="save">{{ t('save') }}</a-button>
        </div>
      </template>
    </a-drawer>
  </Page>
</template>
