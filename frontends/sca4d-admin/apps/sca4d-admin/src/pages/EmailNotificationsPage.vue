<script setup lang="ts">
import type { EmailSender, EmailTemplate, Int64String } from '@sca4d/admin';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

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

import {
  SjDataGrid,
  SjPage,
  SjResourceReference,
  SjResourceState,
  SjTabs,
} from '#/components/sj';
import { SjRowActions } from '#/components/sj';
import { SjConfirmSwitch } from '#/components/sj';
import EmailSenderEditor from '#/features/email/EmailSenderEditor.vue';
import EmailTemplateEditor from '#/features/email/EmailTemplateEditor.vue';
import EmailTemplatePreview from '#/features/email/EmailTemplatePreview.vue';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { $t as sjTranslate } from '#/locales';
import { businessApplication } from '#/platform/business-application';

import {
  resourceQueryId,
  resourceReferenceRoute,
} from '../platform/resource-reference-routes';

const senderEditor = ref<InstanceType<typeof EmailSenderEditor>>();
const templateEditor = ref<InstanceType<typeof EmailTemplateEditor>>();
const previewEditor = ref<InstanceType<typeof EmailTemplatePreview>>();

const { errorText } = useSjPresentation();

const emailSenders = useSjControllerState(businessApplication.emailSenders);
const emailTemplates = useSjControllerState(businessApplication.emailTemplates);
const session = useSjAccess();
const route = useRoute();
const activeTab = ref('senders');
const canManage = computed(() => session.value.capabilities.emailManage);
const senderStatusSavingId = ref<Int64String | null>(null);
const senderTestingId = ref<Int64String | null>(null);
const templateStatusSavingId = ref<Int64String | null>(null);

const senderColumns = computed(() => [
  { field: 'name', title: t('name'), width: 190 },
  { field: 'connection', title: t('connection'), width: 220 },
  { field: 'from', title: t('senderIdentity'), width: 240 },
  { field: 'authentication', title: t('authentication'), width: 140 },
  { field: 'enabled', title: t('status'), width: 110 },
  { field: 'actions', title: t('actions'), width: 190 },
]);

const templateColumns = computed(() => [
  {
    field: 'notificationCode',
    title: t('notificationCode'),
    width: 260,
  },
  { field: 'locales', title: t('locales'), width: 210 },
  { field: 'variables', title: t('variables'), width: 260 },
  { field: 'enabled', title: t('status'), width: 110 },
  { field: 'actions', title: t('actions'), width: 210 },
]);

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
    businessApplication.emailSenders.load(),
    businessApplication.emailTemplates.load(),
  ]);
}

/** 读取指定页的通知模板。 */
async function changeTemplatePage(page: number, size: number): Promise<void> {
  await businessApplication.emailTemplates.load(page, size);
}

/** 使用最新 ETag 修改 SMTP 配置状态。 */
async function changeSenderStatus(
  sender: EmailSender,
  enabled: boolean,
): Promise<void> {
  senderStatusSavingId.value = sender.senderId;
  try {
    await businessApplication.emailSenders.setEnabled(sender.senderId, enabled);
    message.success(t('updated'));
  } catch {
    message.error(errorText(emailSenders.value.error));
  } finally {
    senderStatusSavingId.value = null;
  }
}

/** 测试 SMTP 配置能否建立连接。 */
async function testSender(sender: EmailSender): Promise<void> {
  senderTestingId.value = sender.senderId;
  try {
    await businessApplication.emailSenders.testConnection(sender.senderId);
    message.success(t('connectionSucceeded'));
  } catch {
    message.error(errorText(emailSenders.value.error));
  } finally {
    senderTestingId.value = null;
  }
}

/** 删除一个停用 SMTP 配置。 */
async function removeSender(sender: EmailSender): Promise<void> {
  try {
    await businessApplication.emailSenders.delete(sender.senderId);
    message.success(t('deleted'));
  } catch {
    message.error(errorText(emailSenders.value.error));
  }
}

/** 按 URL 中的资源标识打开对应 Email 页签和现有详情抽屉。 */
async function openLinkedEmailResource(
  tab: 'senders' | 'templates',
  id: Int64String,
): Promise<void> {
  activeTab.value = tab;
  await nextTick();
  await (tab === 'senders'
    ? senderEditor.value?.open(id)
    : templateEditor.value?.open(id));
}

/** 返回 Email 资源在当前管理端中的统一深链接。 */
function emailResourceRoute(
  resourceType: 'NOTIFICATION/EMAIL/SENDERS' | 'NOTIFICATION/EMAIL/TEMPLATES',
  id: Int64String,
) {
  return (
    resourceReferenceRoute(
      { id, name: null, resourceType, typeLabel: null },
      {
        accounts: session.value.capabilities.accountsRead,
        email: session.value.capabilities.emailRead,
        roles: session.value.capabilities.rolesRead,
      },
    ) ?? undefined
  );
}

/** 使用最新 ETag 修改 Email 模板状态。 */
async function changeTemplateStatus(
  template: EmailTemplate,
  enabled: boolean,
): Promise<void> {
  templateStatusSavingId.value = template.templateId;
  try {
    await businessApplication.emailTemplates.setEnabled(
      template.templateId,
      enabled,
    );
    message.success(t('updated'));
  } catch {
    message.error(errorText(emailTemplates.value.error));
  } finally {
    templateStatusSavingId.value = null;
  }
}

/** 删除一个停用 Email 模板。 */
async function removeTemplate(template: EmailTemplate): Promise<void> {
  try {
    await businessApplication.emailTemplates.delete(template.templateId);
    message.success(t('deleted'));
  } catch {
    message.error(errorText(emailTemplates.value.error));
  }
}

/** 读取邮件通知页面文案。 */
function t(key: string, values?: Record<string, unknown>): string {
  return values
    ? $t(`page.emailNotifications.${key}`, values)
    : $t(`page.emailNotifications.${key}`);
}
</script>

<template>
  <SjPage auto-content-height>
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

    <SjTabs v-model:active-key="activeTab" :animated="false">
      <a-tab-pane key="senders" :tab="t('senders')">
        <SjResourceState
          :has-data="emailSenders.senders.length > 0"
          :error="emailSenders.error"
          :phase="emailSenders.phase"
          @retry="businessApplication.emailSenders.load"
        >
          <div class="mb-3 flex justify-end">
            <a-button
              v-if="canManage"
              type="primary"
              @click="senderEditor?.open()"
            >
              <Plus :size="17" aria-hidden="true" />
              {{ t('createSender') }}
            </a-button>
          </div>
          <SjDataGrid
            :columns="senderColumns"
            :items="emailSenders.senders"
            key-field="senderId"
          >
            <template #cell="{ column, row, value }">
              <div v-if="column.field === 'name'" class="flex min-w-0 flex-col">
                <SjResourceReference
                  :id="row.senderId"
                  :name="row.name"
                  :to="
                    emailResourceRoute(
                      'NOTIFICATION/EMAIL/SENDERS',
                      row.senderId,
                    )
                  "
                />
              </div>
              <div
                v-else-if="column.field === 'connection'"
                class="flex flex-col"
              >
                <span>{{ row.host }}:{{ row.port }}</span>
                <a-tag class="mt-1 w-fit" color="blue">
                  {{ row.security }}
                </a-tag>
              </div>
              <div
                v-else-if="column.field === 'from'"
                class="flex min-w-0 flex-col"
              >
                <span class="truncate">{{ row.fromName || '-' }}</span>
                <span class="text-muted-foreground truncate text-xs">
                  {{ row.fromAddress }}
                </span>
              </div>
              <a-tag
                v-else-if="column.field === 'authentication'"
                :color="row.passwordConfigured ? 'cyan' : 'default'"
              >
                {{
                  row.passwordConfigured ? t('configured') : t('notConfigured')
                }}
              </a-tag>
              <template v-else-if="column.field === 'enabled'">
                <SjConfirmSwitch
                  v-if="canManage"
                  :checked="row.enabled"
                  :checked-children="t('active')"
                  :un-checked-children="t('disabled')"
                  :loading="senderStatusSavingId === row.senderId"
                  :disabled="senderStatusSavingId !== null"
                  :aria-label="`${row.name} ${row.enabled ? t('active') : t('disabled')}`"
                  @confirm="changeSenderStatus(row, $event as boolean)"
                  :confirm-title="`${row.name} ${row.enabled ? t('active') : t('disabled')}`"
                  :ok-text="
                    row.enabled
                      ? sjTranslate('sj.disabled')
                      : sjTranslate('sj.active')
                  "
                  :cancel-text="sjTranslate('sj.cancel')"
                />
                <a-tag v-else :color="row.enabled ? 'green' : 'default'">
                  {{ row.enabled ? t('active') : t('disabled') }}
                </a-tag>
              </template>
              <SjRowActions
                v-else-if="column.field === 'actions'"
                :actions="[
                  {
                    key: 'action-0',
                    label: canManage ? t('edit') : t('view'),
                    icon: canManage ? Pencil : Eye,
                    run: async () => {
                      await senderEditor?.open(row);
                    },
                  },
                  {
                    key: 'action-1',
                    label: t('testConnection'),
                    icon: PlugZap,
                    run: async () => {
                      await testSender(row);
                    },
                    visible: canManage,
                    disabled: senderTestingId !== null,
                    disabledReason: sjTranslate('sj.actionUnavailable'),
                    loading: senderTestingId === row.senderId,
                  },
                  {
                    key: 'action-2',
                    label: t('delete'),
                    icon: Trash2,
                    run: async () => {
                      await removeSender(row);
                    },
                    visible: canManage,
                    disabled: row.enabled,
                    disabledReason: sjTranslate('sj.actionUnavailable'),
                    danger: true,
                    confirm: {
                      title: t('confirmDeleteSender'),
                      content: row.name,
                      okText: t('delete'),
                    },
                  },
                ]"
              />
              <span
                v-if="
                  ![
                    'actions',
                    'authentication',
                    'connection',
                    'enabled',
                    'from',
                    'name',
                  ].includes(column.field)
                "
              >
                {{ value ?? '-' }}
              </span>
            </template>
          </SjDataGrid>
        </SjResourceState>
      </a-tab-pane>

      <a-tab-pane key="templates" :tab="t('templates')">
        <SjResourceState
          :has-data="emailTemplates.templates.length > 0"
          :error="emailTemplates.error"
          :phase="emailTemplates.phase"
          @retry="businessApplication.emailTemplates.load"
        >
          <div class="mb-3 flex justify-end">
            <a-button
              v-if="canManage"
              type="primary"
              @click="templateEditor?.open()"
            >
              <Plus :size="17" aria-hidden="true" />
              {{ t('createTemplate') }}
            </a-button>
          </div>
          <SjDataGrid
            :columns="templateColumns"
            :pagination="{
              currentPage: emailTemplates.page,
              pageSize: emailTemplates.size,
              total: emailTemplates.total,
            }"
            @page-change="changeTemplatePage"
            :items="emailTemplates.templates"
            key-field="templateId"
          >
            <template #cell="{ column, row, value }">
              <div
                v-if="column.field === 'notificationCode'"
                class="flex min-w-0 flex-col"
              >
                <SjResourceReference
                  :id="row.templateId"
                  :name="row.notificationCode"
                  :to="
                    emailResourceRoute(
                      'NOTIFICATION/EMAIL/TEMPLATES',
                      row.templateId,
                    )
                  "
                />
              </div>
              <div
                v-else-if="column.field === 'locales'"
                class="flex flex-wrap gap-1"
              >
                <a-tag v-for="locale in Object.keys(row.locales)" :key="locale">
                  {{ locale }}
                </a-tag>
              </div>
              <div
                v-else-if="column.field === 'variables'"
                class="flex flex-wrap gap-1"
              >
                <a-tag
                  v-for="variable in row.variables"
                  :key="variable"
                  color="blue"
                >
                  {{ variable }}
                </a-tag>
                <span v-if="row.variables.length === 0">-</span>
              </div>
              <template v-else-if="column.field === 'enabled'">
                <SjConfirmSwitch
                  v-if="canManage"
                  :checked="row.enabled"
                  :checked-children="t('active')"
                  :un-checked-children="t('disabled')"
                  :loading="templateStatusSavingId === row.templateId"
                  :disabled="templateStatusSavingId !== null"
                  :aria-label="`${row.notificationCode} ${row.enabled ? t('active') : t('disabled')}`"
                  @confirm="changeTemplateStatus(row, $event as boolean)"
                  :confirm-title="`${row.notificationCode} ${row.enabled ? t('active') : t('disabled')}`"
                  :ok-text="
                    row.enabled
                      ? sjTranslate('sj.disabled')
                      : sjTranslate('sj.active')
                  "
                  :cancel-text="sjTranslate('sj.cancel')"
                />
                <a-tag v-else :color="row.enabled ? 'green' : 'default'">
                  {{ row.enabled ? t('active') : t('disabled') }}
                </a-tag>
              </template>
              <SjRowActions
                v-else-if="column.field === 'actions'"
                :actions="[
                  {
                    key: 'action-0',
                    label: canManage ? t('edit') : t('view'),
                    icon: canManage ? Pencil : Eye,
                    run: async () => {
                      await templateEditor?.open(row);
                    },
                  },
                  {
                    key: 'action-1',
                    label: t('preview'),
                    icon: FileText,
                    run: async () => {
                      await previewEditor?.open({
                        locales: row.locales,
                        variables: row.variables,
                      });
                    },
                    visible: canManage,
                  },
                  {
                    key: 'action-2',
                    label: t('delete'),
                    icon: Trash2,
                    run: async () => {
                      await removeTemplate(row);
                    },
                    visible: canManage,
                    disabled: row.enabled,
                    disabledReason: sjTranslate('sj.actionUnavailable'),
                    danger: true,
                    confirm: {
                      title: t('confirmDeleteTemplate'),
                      content: row.notificationCode,
                      okText: t('delete'),
                    },
                  },
                ]"
              />
              <span
                v-if="
                  ![
                    'actions',
                    'enabled',
                    'locales',
                    'notificationCode',
                    'variables',
                  ].includes(column.field)
                "
              >
                {{ value ?? '-' }}
              </span>
            </template>
          </SjDataGrid>
        </SjResourceState>
      </a-tab-pane>
    </SjTabs>
  </SjPage>

  <EmailSenderEditor ref="senderEditor" />

  <EmailTemplateEditor
    @preview="previewEditor?.open($event)"
    ref="templateEditor"
  />

  <EmailTemplatePreview ref="previewEditor" />
</template>

<!-- SMTP 创建与编辑 -->

<!-- 多语言通知模板编辑 -->

<!-- 通知模板服务端预览 -->
