<script setup lang="ts">
import type {
  EmailSender,
  EmailTemplate,
  Int64String,
  NotificationRichTextNode,
  NotificationTemplateContent,
  SmtpSecurity,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import {
  Eye,
  FileText,
  Mail,
  Pencil,
  PlugZap,
  Plus,
  RefreshCw,
  Trash2,
} from 'lucide-vue-next';

import { $t } from '#/locales';

import NotificationDocumentEditor from '../components/NotificationDocumentEditor.vue';
import ResourceReference from '../components/ResourceReference.vue';
import ResourceState from '../components/ResourceState.vue';
import { useI18n } from '../i18n';
import {
  resourceQueryId,
  resourceReferenceRoute,
  withoutResourceQuery,
} from '../platform/resource-reference-routes';
import { useAdminStore } from '../store/business';

type SenderPasswordMode = 'clear' | 'keep' | 'none' | 'replace';

interface TemplateLocaleForm {
  body: NotificationRichTextNode;
  title: NotificationRichTextNode;
}

interface VariableRow {
  key: string;
  value: string;
}

const store = useAdminStore();
const route = useRoute();
const router = useRouter();
const { errorText } = useI18n();
const activeTab = ref('senders');
const canManage = computed(() => store.session.capabilities.emailManage);

const senderDrawerOpen = ref(false);
const senderLoading = ref(false);
const senderSaving = ref(false);
const senderFormRef = ref<FormInstance>();
const editingSender = ref<EmailSender | null>(null);
const senderPasswordMode = ref<SenderPasswordMode>('none');
const senderStatusSavingId = ref<Int64String | null>(null);
const senderTestingId = ref<Int64String | null>(null);
const senderForm = reactive({
  fromAddress: '',
  fromName: '',
  host: '',
  name: '',
  password: '',
  port: 1025,
  replyTo: '',
  security: 'NONE' as SmtpSecurity,
  username: '',
});

const templateDrawerOpen = ref(false);
const templateLoading = ref(false);
const templateSaving = ref(false);
const templateFormRef = ref<FormInstance>();
const editingTemplate = ref<EmailTemplate | null>(null);
const activeTemplateLocale = ref('');
const newTemplateLocale = ref<string>();
const templateLocaleSearch = ref('');
const templateStatusSavingId = ref<Int64String | null>(null);
const templateForm = reactive<{
  locales: Record<string, TemplateLocaleForm>;
  notificationCode: string;
}>({ locales: {}, notificationCode: '' });

const previewOpen = ref(false);
const previewLoading = ref(false);
const previewLanguage = ref('');
const previewContent = ref<NotificationTemplateContent>({});
const previewResult = ref<null | {
  html: null | string;
  subject: string;
  text: string;
}>(null);
const previewVariables = ref<VariableRow[]>([]);

const supportedLocales = computed(
  () => store.emailTemplates.localeCatalog?.supportedLocales ?? [],
);
const defaultLocale = computed(
  () => store.emailTemplates.localeCatalog?.defaultLocale ?? '',
);
const templateLocales = computed(() => Object.keys(templateForm.locales));
const templateLocaleSuggestions = computed(() => {
  const values = supportedLocales.value.filter(
    (locale) => !templateLocales.value.includes(locale),
  );
  const searched = canonicalLanguageTag(templateLocaleSearch.value);
  if (
    searched &&
    !templateLocales.value.includes(searched) &&
    !values.includes(searched)
  ) {
    values.unshift(searched);
  }
  return values.map((value) => ({ label: value, value }));
});
const previewLocales = computed(() => Object.keys(previewContent.value));

const senderColumns = computed(() => [
  { dataIndex: 'name', key: 'name', title: t('name'), width: 190 },
  { key: 'connection', title: t('connection'), width: 220 },
  { key: 'from', title: t('senderIdentity'), width: 240 },
  { key: 'authentication', title: t('authentication'), width: 140 },
  { key: 'enabled', title: t('status'), width: 110 },
  { key: 'actions', title: t('actions'), width: 190 },
]);

const templateColumns = computed(() => [
  {
    dataIndex: 'notificationCode',
    key: 'notificationCode',
    title: t('notificationCode'),
    width: 260,
  },
  { key: 'locales', title: t('locales'), width: 210 },
  { key: 'variables', title: t('variables'), width: 260 },
  { key: 'enabled', title: t('status'), width: 110 },
  { key: 'actions', title: t('actions'), width: 210 },
]);

const senderPasswordOptions = computed(() => {
  const sender = editingSender.value;
  if (!sender) {
    return [
      { label: t('noAuthentication'), value: 'none' },
      { label: t('setPassword'), value: 'replace' },
    ];
  }
  if (!sender.passwordConfigured) {
    return [
      { label: t('noAuthentication'), value: 'none' },
      { label: t('setPassword'), value: 'replace' },
    ];
  }
  return [
    { label: t('keepPassword'), value: 'keep' },
    { label: t('replacePassword'), value: 'replace' },
    { label: t('clearAuthentication'), value: 'clear' },
  ];
});

onMounted(() => void reload());
watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'senders' || tab === 'templates') activeTab.value = tab;
  },
  { immediate: true },
);
watch(
  [
    () => resourceQueryId(route.query, 'senderId'),
    () => resourceQueryId(route.query, 'templateId'),
  ],
  ([senderId, templateId]) => {
    if (senderId) void openLinkedEmailResource('senders', senderId);
    else if (templateId) void openLinkedEmailResource('templates', templateId);
  },
  { immediate: true },
);

/** 读取 SMTP 配置、Email 模板和 Locale。 */
async function reload(): Promise<void> {
  await Promise.allSettled([
    store.loadEmailSenders(),
    store.loadEmailTemplates(),
  ]);
}

/** 读取指定页的通知模板。 */
async function changeTemplatePage(page: number, size: number): Promise<void> {
  await store.loadEmailTemplates(page, size);
}

/** 打开 SMTP 配置创建或详情抽屉。 */
async function openSender(
  sender: EmailSender | Int64String | null = null,
): Promise<void> {
  const senderId = typeof sender === 'string' ? sender : sender?.senderId;
  editingSender.value = typeof sender === 'string' ? null : sender;
  senderDrawerOpen.value = true;
  senderLoading.value = senderId !== undefined && senderId !== null;
  store.clearEmailSender();
  try {
    const detail = senderId ? await store.loadEmailSender(senderId) : null;
    editingSender.value = detail;
    senderForm.name = detail?.name ?? '';
    senderForm.host = detail?.host ?? '';
    senderForm.port = detail?.port ?? 1025;
    senderForm.security = detail?.security ?? 'NONE';
    senderForm.username = detail?.username ?? '';
    senderForm.password = '';
    senderForm.fromAddress = detail?.fromAddress ?? '';
    senderForm.fromName = detail?.fromName ?? '';
    senderForm.replyTo = detail?.replyTo ?? '';
    senderPasswordMode.value = detail?.passwordConfigured ? 'keep' : 'none';
    senderFormRef.value?.clearValidate();
  } catch {
    senderDrawerOpen.value = false;
    message.error(errorText(store.emailSenders.error));
  } finally {
    senderLoading.value = false;
  }
}

/** 校验并创建或完整替换 SMTP 配置。 */
async function saveSender(): Promise<void> {
  await senderFormRef.value?.validate();
  senderSaving.value = true;
  try {
    const mode = senderPasswordMode.value;
    const common = {
      fromAddress: senderForm.fromAddress.trim(),
      fromName: optional(senderForm.fromName),
      host: senderForm.host.trim(),
      name: senderForm.name.trim(),
      password: mode === 'replace' ? senderForm.password : null,
      port: senderForm.port,
      replyTo: optional(senderForm.replyTo),
      security: senderForm.security,
      username:
        mode === 'keep' || mode === 'replace'
          ? optional(senderForm.username)
          : null,
    };
    if (editingSender.value) {
      await store.replaceEmailSender({
        ...common,
        passwordChanged: mode === 'replace' || mode === 'clear',
      });
      message.success(t('updated'));
    } else {
      await store.createEmailSender(common);
      message.success(t('created'));
    }
    senderDrawerOpen.value = false;
    store.clearEmailSender();
  } catch {
    message.error(errorText(store.emailSenders.error));
  } finally {
    senderSaving.value = false;
  }
}

/** 使用最新 ETag 修改 SMTP 配置状态。 */
async function changeSenderStatus(
  sender: EmailSender,
  enabled: boolean,
): Promise<void> {
  senderStatusSavingId.value = sender.senderId;
  try {
    await store.setEmailSenderEnabled(sender.senderId, enabled);
    message.success(t('updated'));
  } catch {
    message.error(errorText(store.emailSenders.error));
  } finally {
    senderStatusSavingId.value = null;
  }
}

/** 测试 SMTP 配置能否建立连接。 */
async function testSender(sender: EmailSender): Promise<void> {
  senderTestingId.value = sender.senderId;
  try {
    await store.testEmailSenderConnection(sender.senderId);
    message.success(t('connectionSucceeded'));
  } catch {
    message.error(errorText(store.emailSenders.error));
  } finally {
    senderTestingId.value = null;
  }
}

/** 删除一个停用 SMTP 配置。 */
async function removeSender(sender: EmailSender): Promise<void> {
  try {
    await store.deleteEmailSender(sender.senderId);
    message.success(t('deleted'));
  } catch {
    message.error(errorText(store.emailSenders.error));
  }
}

/** 打开 Email 模板创建或详情抽屉，并按默认 Locale 和已保存内容建立表单。 */
async function openTemplate(
  template: EmailTemplate | Int64String | null = null,
): Promise<void> {
  const templateId =
    typeof template === 'string' ? template : template?.templateId;
  editingTemplate.value = typeof template === 'string' ? null : template;
  templateDrawerOpen.value = true;
  templateLoading.value = templateId !== undefined && templateId !== null;
  store.clearEmailTemplate();
  try {
    if (supportedLocales.value.length === 0) await store.loadEmailTemplates();
    const detail = templateId
      ? await store.loadEmailTemplate(templateId)
      : null;
    editingTemplate.value = detail;
    templateForm.notificationCode = detail?.notificationCode ?? '';
    const locales = [
      defaultLocale.value,
      ...Object.keys(detail?.content ?? {}),
    ].filter(
      (locale, index, values) => locale && values.indexOf(locale) === index,
    );
    templateForm.locales = Object.fromEntries(
      locales.map((locale) => [
        locale,
        {
          body: cloneDocument(detail?.content[locale]?.body),
          title: cloneDocument(detail?.content[locale]?.title),
        },
      ]),
    );
    activeTemplateLocale.value = defaultLocale.value;
    newTemplateLocale.value = undefined;
    templateLocaleSearch.value = '';
    templateFormRef.value?.clearValidate();
  } catch {
    templateDrawerOpen.value = false;
    message.error(errorText(store.emailTemplates.error));
  } finally {
    templateLoading.value = false;
  }
}

/** 按 URL 中的资源标识打开对应 Email 页签和现有详情抽屉。 */
async function openLinkedEmailResource(
  tab: 'senders' | 'templates',
  id: Int64String,
): Promise<void> {
  activeTab.value = tab;
  await (tab === 'senders' ? openSender(id) : openTemplate(id));
}

/** Email 详情抽屉关闭后清除已经消费的资源标识。 */
function emailDrawerVisibilityChanged(
  open: boolean,
  key: 'senderId' | 'templateId',
): void {
  if (!open && resourceQueryId(route.query, key)) {
    void router.replace({ query: withoutResourceQuery(route.query, key) });
  }
}

/** 返回 Email 资源在当前管理端中的统一深链接。 */
function emailResourceRoute(
  resourceType: 'notification:email:sender' | 'notification:email:template',
  id: Int64String,
) {
  return (
    resourceReferenceRoute(
      { id, name: null, resourceType, typeLabel: null },
      {
        accounts: store.session.capabilities.accountsRead,
        email: store.session.capabilities.emailRead,
        roles: store.session.capabilities.rolesRead,
      },
    ) ?? undefined
  );
}

/** 规范化并添加一个任意合法 BCP 47 语言标签。 */
async function addTemplateLocale(value: string): Promise<void> {
  const locale = canonicalLanguageTag(value);
  if (!locale) {
    message.error(t('invalidLocale'));
    return;
  }
  if (templateLocales.value.includes(locale)) {
    message.error(t('duplicateLocale'));
    return;
  }
  templateForm.locales[locale] = {
    body: emptyDocument(),
    title: emptyDocument(),
  };
  activeTemplateLocale.value = locale;
  newTemplateLocale.value = undefined;
  templateLocaleSearch.value = '';
  await nextTick();
  templateFormRef.value?.clearValidate();
}

/** 删除一个非默认模板 Locale 及其当前草稿内容。 */
function removeTemplateLocale(locale: string): void {
  if (locale === defaultLocale.value) return;
  delete templateForm.locales[locale];
  activeTemplateLocale.value = defaultLocale.value;
  templateFormRef.value?.clearValidate();
}

/** 校验并创建或完整替换多语言 Email 模板。 */
async function saveTemplate(): Promise<void> {
  await templateFormRef.value?.validate();
  if (!validateTemplateDocuments()) return;
  templateSaving.value = true;
  try {
    const input = {
      content: templateContent(),
      notificationCode: templateForm.notificationCode.trim(),
    };
    if (editingTemplate.value) {
      await store.replaceEmailTemplate(input);
      message.success(t('updated'));
    } else {
      await store.createEmailTemplate(input);
      message.success(t('created'));
    }
    templateDrawerOpen.value = false;
    store.clearEmailTemplate();
  } catch {
    message.error(errorText(store.emailTemplates.error));
  } finally {
    templateSaving.value = false;
  }
}

/** 使用最新 ETag 修改 Email 模板状态。 */
async function changeTemplateStatus(
  template: EmailTemplate,
  enabled: boolean,
): Promise<void> {
  templateStatusSavingId.value = template.templateId;
  try {
    await store.setEmailTemplateEnabled(template.templateId, enabled);
    message.success(t('updated'));
  } catch {
    message.error(errorText(store.emailTemplates.error));
  } finally {
    templateStatusSavingId.value = null;
  }
}

/** 删除一个停用 Email 模板。 */
async function removeTemplate(template: EmailTemplate): Promise<void> {
  try {
    await store.deleteEmailTemplate(template.templateId);
    message.success(t('deleted'));
  } catch {
    message.error(errorText(store.emailTemplates.error));
  }
}

/** 使用当前草稿或已保存模板打开预览抽屉。 */
function openPreview(template: EmailTemplate | null = null): void {
  const content = template ? template.content : templateContent();
  const variables =
    template?.variables ?? editingTemplate.value?.variables ?? [];
  previewContent.value = content;
  previewLanguage.value =
    (defaultLocale.value in content
      ? defaultLocale.value
      : Object.keys(content)[0]) ?? '';
  previewVariables.value = variables.map((key) => ({ key, value: '' }));
  previewResult.value = null;
  previewOpen.value = true;
}

/** 给草稿预览增加一个变量输入。 */
function addPreviewVariable(): void {
  previewVariables.value.push({ key: '', value: '' });
}

/** 删除一个草稿预览变量。 */
function removePreviewVariable(index: number): void {
  previewVariables.value.splice(index, 1);
}

/** 调用后端渲染草稿并显示安全结果。 */
async function generatePreview(): Promise<void> {
  const variables = variableMap(previewVariables.value);
  if (!variables) return;
  previewLoading.value = true;
  try {
    previewResult.value = await store.previewEmailTemplate({
      content: previewContent.value,
      languageTag: previewLanguage.value,
      variables,
    });
  } catch {
    message.error(errorText(store.emailTemplates.error));
  } finally {
    previewLoading.value = false;
  }
}

/** 返回一个 Locale 是否已经输入任意模板字段。 */
function localeHasContent(locale: string): boolean {
  const fields = templateForm.locales[locale];
  return Boolean(
    fields &&
    (documentHasContent(fields.title) || documentHasContent(fields.body)),
  );
}

/** 返回已经在当前模板草稿中创建的 Locale 表单。 */
function localeFields(locale: string): TemplateLocaleForm {
  const fields = templateForm.locales[locale];
  if (!fields) throw new Error(`Missing Email template locale: ${locale}`);
  return fields;
}

/** 把模板表单转换为后端接受的平台无关富文本内容。 */
function templateContent(): NotificationTemplateContent {
  return Object.fromEntries(
    templateLocales.value.flatMap((locale) => {
      const fields = templateForm.locales[locale];
      if (
        !fields ||
        (!localeHasContent(locale) && locale !== defaultLocale.value)
      ) {
        return [];
      }
      return [
        [
          locale,
          {
            body: fields.body,
            title: fields.title,
          },
        ],
      ];
    }),
  );
}

/** 校验每个保存的 Locale 同时具有标题和正文。 */
function validateTemplateDocuments(): boolean {
  for (const locale of templateLocales.value) {
    const fields = localeFields(locale);
    if (
      (locale === defaultLocale.value || localeHasContent(locale)) &&
      (!documentHasContent(fields.title) || !documentHasContent(fields.body))
    ) {
      activeTemplateLocale.value = locale;
      message.error(t('titleAndBodyRequired'));
      return false;
    }
  }
  return true;
}

/** 创建一个可以直接交给 Tiptap 的空文档。 */
function emptyDocument(): NotificationRichTextNode {
  return {
    content: [{ type: 'paragraph' }],
    type: 'doc',
  };
}

/** 复制后端冻结的模板文档，避免编辑器修改 Controller 状态。 */
function cloneDocument(
  value: NotificationRichTextNode | undefined,
): NotificationRichTextNode {
  return value ? structuredClone(value) : emptyDocument();
}

/** 判断文档是否含有可渲染文本、变量、图片或分隔线。 */
function documentHasContent(node: NotificationRichTextNode): boolean {
  if (node.text?.trim()) return true;
  if (['horizontalRule', 'image', 'notificationVariable'].includes(node.type)) {
    return true;
  }
  return node.content?.some((child) => documentHasContent(child)) ?? false;
}

/** 使用浏览器原生 Locale 实现校验并返回规范 BCP 47 标签。 */
function canonicalLanguageTag(value: string): null | string {
  const candidate = value.trim();
  if (!candidate) return null;
  try {
    return Intl.getCanonicalLocales(candidate)[0] ?? null;
  } catch {
    return null;
  }
}

/** 校验预览变量名称、重复项和值长度并转换为 Map。 */
function variableMap(
  rows: readonly VariableRow[],
): null | Record<string, string> {
  const values: Record<string, string> = {};
  for (const row of rows) {
    const key = row.key.trim();
    if (!/^[a-z][a-zA-Z0-9_]{0,63}$/u.test(key) || key in values) {
      message.error(t('invalidVariable'));
      return null;
    }
    if (row.value.length > 4096) {
      message.error(t('variableValueTooLong'));
      return null;
    }
    values[key] = row.value;
  }
  return values;
}

/** 把可选 SMTP 文本的空白值转换为 null。 */
function optional(value: string): null | string {
  const trimmed = value.trim();
  return trimmed || null;
}

/** 读取邮件通知页面文案。 */
function t(key: string, values?: Record<string, unknown>): string {
  return values
    ? $t(`page.emailNotifications.${key}`, values)
    : $t(`page.emailNotifications.${key}`);
}
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-2 text-base font-medium">
        <Mail :size="19" aria-hidden="true" />
        <span>{{ t('title') }}</span>
      </div>
      <a-tooltip :title="t('refresh')">
        <a-button :aria-label="t('refresh')" @click="reload">
          <RefreshCw :size="17" aria-hidden="true" />
        </a-button>
      </a-tooltip>
    </div>

    <a-tabs v-model:active-key="activeTab" :animated="false">
      <a-tab-pane key="senders" :tab="t('senders')">
        <ResourceState
          :error="store.emailSenders.error"
          :phase="store.emailSenders.phase"
          @retry="store.loadEmailSenders"
        >
          <div class="mb-3 flex justify-end">
            <a-button v-if="canManage" type="primary" @click="openSender()">
              <Plus :size="17" aria-hidden="true" />{{ t('createSender') }}
            </a-button>
          </div>
          <a-table
            :columns="senderColumns"
            :data-source="store.emailSenders.senders"
            :pagination="false"
            row-key="senderId"
            :scroll="{ x: 1050 }"
          >
            <template #bodyCell="{ column, record }">
              <div v-if="column.key === 'name'" class="flex min-w-0 flex-col">
                <ResourceReference
                  :id="record.senderId"
                  :name="record.name"
                  :to="
                    emailResourceRoute(
                      'notification:email:sender',
                      record.senderId,
                    )
                  "
                />
              </div>
              <div
                v-else-if="column.key === 'connection'"
                class="flex flex-col"
              >
                <span>{{ record.host }}:{{ record.port }}</span>
                <a-tag class="mt-1 w-fit" color="blue">
                  {{ record.security }}
                </a-tag>
              </div>
              <div
                v-else-if="column.key === 'from'"
                class="flex min-w-0 flex-col"
              >
                <span class="truncate">{{ record.fromName || '-' }}</span>
                <span class="text-muted-foreground truncate text-xs">{{
                  record.fromAddress
                }}</span>
              </div>
              <a-tag
                v-else-if="column.key === 'authentication'"
                :color="record.passwordConfigured ? 'cyan' : 'default'"
              >
                {{
                  record.passwordConfigured
                    ? t('configured')
                    : t('notConfigured')
                }}
              </a-tag>
              <template v-else-if="column.key === 'enabled'">
                <a-switch
                  v-if="canManage"
                  :checked="record.enabled"
                  :checked-children="t('active')"
                  :un-checked-children="t('disabled')"
                  :loading="senderStatusSavingId === record.senderId"
                  :disabled="senderStatusSavingId !== null"
                  :aria-label="`${record.name} ${record.enabled ? t('active') : t('disabled')}`"
                  @change="changeSenderStatus(record, $event as boolean)"
                />
                <a-tag v-else :color="record.enabled ? 'green' : 'default'">
                  {{ record.enabled ? t('active') : t('disabled') }}
                </a-tag>
              </template>
              <div
                v-else-if="column.key === 'actions'"
                class="flex justify-center gap-1"
              >
                <a-tooltip :title="canManage ? t('edit') : t('view')">
                  <a-button
                    type="text"
                    :aria-label="canManage ? t('edit') : t('view')"
                    @click="openSender(record)"
                  >
                    <Pencil v-if="canManage" :size="17" aria-hidden="true" />
                    <Eye v-else :size="17" aria-hidden="true" />
                  </a-button>
                </a-tooltip>
                <a-tooltip v-if="canManage" :title="t('testConnection')">
                  <a-button
                    type="text"
                    :aria-label="t('testConnection')"
                    :loading="senderTestingId === record.senderId"
                    :disabled="senderTestingId !== null"
                    @click="testSender(record)"
                  >
                    <PlugZap :size="17" aria-hidden="true" />
                  </a-button>
                </a-tooltip>
                <a-popconfirm
                  v-if="canManage"
                  :title="t('confirmDeleteSender')"
                  :ok-text="t('delete')"
                  :cancel-text="t('cancel')"
                  @confirm="removeSender(record)"
                >
                  <a-tooltip :title="t('delete')">
                    <a-button
                      type="text"
                      danger
                      :aria-label="t('delete')"
                      :disabled="record.enabled"
                    >
                      <Trash2 :size="17" aria-hidden="true" />
                    </a-button>
                  </a-tooltip>
                </a-popconfirm>
              </div>
            </template>
          </a-table>
          <div class="mt-4 flex justify-end">
            <a-pagination
              :current="store.emailTemplates.page"
              :page-size="store.emailTemplates.size"
              :total="store.emailTemplates.total"
              :show-size-changer="true"
              :page-size-options="['10', '20', '50', '100']"
              @change="changeTemplatePage"
            />
          </div>
        </ResourceState>
      </a-tab-pane>

      <a-tab-pane key="templates" :tab="t('templates')">
        <ResourceState
          :error="store.emailTemplates.error"
          :phase="store.emailTemplates.phase"
          @retry="store.loadEmailTemplates"
        >
          <div class="mb-3 flex justify-end">
            <a-button v-if="canManage" type="primary" @click="openTemplate()">
              <Plus :size="17" aria-hidden="true" />{{ t('createTemplate') }}
            </a-button>
          </div>
          <a-table
            :columns="templateColumns"
            :data-source="store.emailTemplates.templates"
            :pagination="false"
            row-key="templateId"
            :scroll="{ x: 1050 }"
          >
            <template #bodyCell="{ column, record }">
              <div
                v-if="column.key === 'notificationCode'"
                class="flex min-w-0 flex-col"
              >
                <ResourceReference
                  :id="record.templateId"
                  :name="record.notificationCode"
                  :to="
                    emailResourceRoute(
                      'notification:email:template',
                      record.templateId,
                    )
                  "
                />
              </div>
              <div
                v-else-if="column.key === 'locales'"
                class="flex flex-wrap gap-1"
              >
                <a-tag
                  v-for="locale in Object.keys(record.content)"
                  :key="locale"
                >
                  {{ locale }}
                </a-tag>
              </div>
              <div
                v-else-if="column.key === 'variables'"
                class="flex flex-wrap gap-1"
              >
                <a-tag
                  v-for="variable in record.variables"
                  :key="variable"
                  color="blue"
                >
                  {{ variable }}
                </a-tag>
                <span v-if="record.variables.length === 0">-</span>
              </div>
              <template v-else-if="column.key === 'enabled'">
                <a-switch
                  v-if="canManage"
                  :checked="record.enabled"
                  :checked-children="t('active')"
                  :un-checked-children="t('disabled')"
                  :loading="templateStatusSavingId === record.templateId"
                  :disabled="templateStatusSavingId !== null"
                  :aria-label="`${record.notificationCode} ${record.enabled ? t('active') : t('disabled')}`"
                  @change="changeTemplateStatus(record, $event as boolean)"
                />
                <a-tag v-else :color="record.enabled ? 'green' : 'default'">
                  {{ record.enabled ? t('active') : t('disabled') }}
                </a-tag>
              </template>
              <div
                v-else-if="column.key === 'actions'"
                class="flex justify-center gap-1"
              >
                <a-tooltip :title="canManage ? t('edit') : t('view')">
                  <a-button
                    type="text"
                    :aria-label="canManage ? t('edit') : t('view')"
                    @click="openTemplate(record)"
                  >
                    <Pencil v-if="canManage" :size="17" aria-hidden="true" />
                    <Eye v-else :size="17" aria-hidden="true" />
                  </a-button>
                </a-tooltip>
                <a-tooltip v-if="canManage" :title="t('preview')">
                  <a-button
                    type="text"
                    :aria-label="t('preview')"
                    @click="openPreview(record)"
                  >
                    <FileText :size="17" aria-hidden="true" />
                  </a-button>
                </a-tooltip>
                <a-popconfirm
                  v-if="canManage"
                  :title="t('confirmDeleteTemplate')"
                  :ok-text="t('delete')"
                  :cancel-text="t('cancel')"
                  @confirm="removeTemplate(record)"
                >
                  <a-tooltip :title="t('delete')">
                    <a-button
                      type="text"
                      danger
                      :aria-label="t('delete')"
                      :disabled="record.enabled"
                    >
                      <Trash2 :size="17" aria-hidden="true" />
                    </a-button>
                  </a-tooltip>
                </a-popconfirm>
              </div>
            </template>
          </a-table>
        </ResourceState>
      </a-tab-pane>
    </a-tabs>

    <a-drawer
      v-model:open="senderDrawerOpen"
      :title="editingSender ? t('senderDetails') : t('createSender')"
      width="min(640px, 100vw)"
      @close="store.clearEmailSender"
      @after-open-change="emailDrawerVisibilityChanged($event, 'senderId')"
    >
      <a-spin :spinning="senderLoading">
        <a-form
          ref="senderFormRef"
          layout="vertical"
          :model="senderForm"
          :disabled="!canManage"
        >
          <a-form-item
            name="name"
            :label="t('name')"
            :rules="[
              { required: true, whitespace: true, message: t('required') },
              { max: 128 },
            ]"
          >
            <a-input v-model:value="senderForm.name" :maxlength="128" />
          </a-form-item>
          <div class="grid grid-cols-1 gap-x-4 sm:grid-cols-[1fr_140px]">
            <a-form-item
              name="host"
              :label="t('host')"
              :rules="[
                { required: true, whitespace: true, message: t('required') },
                { max: 253 },
              ]"
            >
              <a-input v-model:value="senderForm.host" :maxlength="253" />
            </a-form-item>
            <a-form-item
              name="port"
              :label="t('port')"
              :rules="[{ required: true, type: 'number', min: 1, max: 65535 }]"
            >
              <a-input-number
                v-model:value="senderForm.port"
                class="w-full"
                :min="1"
                :max="65535"
              />
            </a-form-item>
          </div>
          <a-form-item
            name="security"
            :label="t('security')"
            :rules="[{ required: true }]"
          >
            <a-select
              v-model:value="senderForm.security"
              :options="
                ['NONE', 'STARTTLS', 'TLS'].map((value) => ({
                  label: value,
                  value,
                }))
              "
            />
          </a-form-item>
          <a-form-item :label="t('authentication')">
            <a-segmented
              v-model:value="senderPasswordMode"
              block
              :options="senderPasswordOptions"
            />
          </a-form-item>
          <template
            v-if="
              senderPasswordMode === 'keep' || senderPasswordMode === 'replace'
            "
          >
            <a-form-item
              name="username"
              :label="t('username')"
              :rules="[
                { required: true, whitespace: true, message: t('required') },
                { max: 320 },
              ]"
            >
              <a-input
                v-model:value="senderForm.username"
                autocomplete="off"
                :maxlength="320"
              />
            </a-form-item>
            <a-form-item
              v-if="senderPasswordMode === 'replace'"
              name="password"
              :label="t('password')"
              :rules="[
                { required: true, whitespace: true, message: t('required') },
                { max: 4096 },
              ]"
            >
              <a-input-password
                v-model:value="senderForm.password"
                autocomplete="new-password"
                :maxlength="4096"
              />
            </a-form-item>
          </template>
          <a-divider />
          <a-form-item
            name="fromAddress"
            :label="t('fromAddress')"
            :rules="[
              { required: true, type: 'email', message: t('invalidEmail') },
              { max: 320 },
            ]"
          >
            <a-input
              v-model:value="senderForm.fromAddress"
              autocomplete="email"
              type="email"
              :maxlength="320"
            />
          </a-form-item>
          <a-form-item
            name="fromName"
            :label="t('fromName')"
            :rules="[{ max: 256 }]"
          >
            <a-input v-model:value="senderForm.fromName" :maxlength="256" />
          </a-form-item>
          <a-form-item
            name="replyTo"
            :label="t('replyTo')"
            :rules="[
              { type: 'email', message: t('invalidEmail') },
              { max: 320 },
            ]"
          >
            <a-input
              v-model:value="senderForm.replyTo"
              autocomplete="email"
              type="email"
              :maxlength="320"
            />
          </a-form-item>
        </a-form>
      </a-spin>
      <template #footer>
        <div class="flex justify-end gap-2">
          <a-button @click="senderDrawerOpen = false">
            {{ t('cancel') }}
          </a-button>
          <a-button
            v-if="canManage"
            type="primary"
            :loading="senderSaving"
            :disabled="senderLoading"
            @click="saveSender"
          >
            {{ t('save') }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="templateDrawerOpen"
      :title="editingTemplate ? t('templateDetails') : t('createTemplate')"
      width="min(760px, 100vw)"
      @close="store.clearEmailTemplate"
      @after-open-change="emailDrawerVisibilityChanged($event, 'templateId')"
    >
      <a-spin :spinning="templateLoading">
        <a-form
          ref="templateFormRef"
          layout="vertical"
          :model="templateForm"
          :disabled="!canManage"
        >
          <a-form-item
            name="notificationCode"
            :label="t('notificationCode')"
            :rules="[
              { required: true, whitespace: true, message: t('required') },
              {
                pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/u,
                message: t('invalidNotificationCode'),
              },
            ]"
          >
            <a-input
              v-model:value="templateForm.notificationCode"
              class="font-mono"
              :maxlength="128"
            />
          </a-form-item>
          <div v-if="canManage" class="mb-4">
            <a-form-item
              class="mb-0"
              html-for="email-template-language-tag"
              :label="t('languageTag')"
            >
              <a-select
                v-model:value="newTemplateLocale"
                allow-clear
                id="email-template-language-tag"
                :filter-option="false"
                :options="templateLocaleSuggestions"
                show-search
                @search="templateLocaleSearch = $event"
                @select="addTemplateLocale"
              />
            </a-form-item>
          </div>
          <a-tabs v-model:active-key="activeTemplateLocale" :animated="false">
            <a-tab-pane v-for="locale in templateLocales" :key="locale">
              <template #tab>
                <span class="inline-flex items-center gap-1">
                  {{ locale }}
                  <a-tag v-if="locale === defaultLocale" color="blue">{{
                    t('defaultLocale')
                  }}</a-tag>
                </span>
              </template>
              <div
                v-if="canManage && locale !== defaultLocale"
                class="mb-2 flex justify-end"
              >
                <a-popconfirm
                  :title="t('confirmRemoveLocale', { locale })"
                  :ok-text="t('removeLocale')"
                  :cancel-text="t('cancel')"
                  @confirm="removeTemplateLocale(locale)"
                >
                  <a-button type="text" danger>
                    <Trash2 :size="17" aria-hidden="true" />{{
                      t('removeLocale')
                    }}
                  </a-button>
                </a-popconfirm>
              </div>
              <a-form-item :label="t('subject')">
                <NotificationDocumentEditor
                  v-model="localeFields(locale).title"
                  :editable="canManage"
                  :insert-label="t('insertVariable')"
                  :placeholder="t('subjectPlaceholder')"
                  :variable-placeholder="t('variableName')"
                  :variables="editingTemplate?.variables ?? []"
                />
              </a-form-item>
              <a-form-item :label="t('body')">
                <NotificationDocumentEditor
                  v-model="localeFields(locale).body"
                  :editable="canManage"
                  :insert-label="t('insertVariable')"
                  :placeholder="t('bodyPlaceholder')"
                  :variable-placeholder="t('variableName')"
                  :variables="editingTemplate?.variables ?? []"
                />
              </a-form-item>
            </a-tab-pane>
          </a-tabs>
        </a-form>
      </a-spin>
      <template #footer>
        <div class="flex flex-wrap justify-end gap-2">
          <a-button
            v-if="canManage"
            :disabled="templateLoading"
            @click="openPreview()"
          >
            <FileText :size="17" aria-hidden="true" />{{ t('preview') }}
          </a-button>
          <a-button @click="templateDrawerOpen = false">
            {{ t('cancel') }}
          </a-button>
          <a-button
            v-if="canManage"
            type="primary"
            :loading="templateSaving"
            :disabled="templateLoading"
            @click="saveTemplate"
          >
            {{ t('save') }}
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer
      v-model:open="previewOpen"
      :title="t('preview')"
      width="min(760px, 100vw)"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('locale')">
          <a-select
            v-model:value="previewLanguage"
            :options="previewLocales.map((value) => ({ label: value, value }))"
          />
        </a-form-item>
        <div
          v-for="(variable, index) in previewVariables"
          :key="index"
          class="grid grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_40px] gap-2"
        >
          <a-form-item :label="index === 0 ? t('variable') : undefined">
            <a-input
              v-model:value="variable.key"
              class="font-mono"
              :maxlength="64"
            />
          </a-form-item>
          <a-form-item :label="index === 0 ? t('value') : undefined">
            <a-input v-model:value="variable.value" :maxlength="4096" />
          </a-form-item>
          <a-button
            class="mt-7"
            type="text"
            danger
            :aria-label="t('deleteVariable')"
            @click="removePreviewVariable(index)"
          >
            <Trash2 :size="17" aria-hidden="true" />
          </a-button>
        </div>
        <a-button type="dashed" block @click="addPreviewVariable">
          <Plus :size="17" aria-hidden="true" />{{ t('addVariable') }}
        </a-button>
        <a-button
          class="mt-4 w-full"
          type="primary"
          :loading="previewLoading"
          @click="generatePreview"
        >
          {{ t('generatePreview') }}
        </a-button>
      </a-form>
      <a-divider />
      <a-empty v-if="!previewResult" :description="t('noPreview')" />
      <div v-else class="space-y-4">
        <section>
          <h3 class="mb-1 text-sm font-medium">{{ t('subject') }}</h3>
          <div class="rounded border p-3">{{ previewResult.subject }}</div>
        </section>
        <a-tabs :animated="false">
          <a-tab-pane key="text" :tab="t('textBody')">
            <pre
              class="max-h-80 overflow-auto whitespace-pre-wrap rounded border p-3 text-sm"
              >{{ previewResult.text }}</pre>
          </a-tab-pane>
          <a-tab-pane v-if="previewResult.html" key="html" :tab="t('htmlBody')">
            <iframe
              class="h-80 w-full rounded border bg-white"
              sandbox=""
              :srcdoc="previewResult.html"
              :title="t('htmlPreview')"
            ></iframe>
          </a-tab-pane>
        </a-tabs>
      </div>
    </a-drawer>
  </Page>
</template>
