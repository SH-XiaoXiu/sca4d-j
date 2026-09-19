<script setup lang="ts">
import type {
  AuditResult,
  EmailSender,
  EmailTemplate,
  Int64String,
  OperationLog,
  OperationLogDisplayText,
  PlatformRole,
  ResourceReferenceData,
} from '@sca4d/admin';
import type { Dayjs } from 'dayjs';

import type { Component } from 'vue';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { computed, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  operationLogActionText,
  operationLogTargetReference,
  resourceReferenceKey,
} from '@sca4d/admin';
import { message } from 'ant-design-vue';
import { CircleCheck, Eye, ShieldX, TriangleAlert } from 'lucide-vue-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';

import ResourceState from '../components/ResourceState.vue';
import ResourceReference from '../components/ResourceReference.vue';
import { useI18n } from '../i18n';
import { resourceReferenceRoute } from '../platform/resource-reference-routes';
import { useAdminStore } from '../store/business';

interface OperationLogSearchValues {
  accountId?: string;
  actorPrincipalId?: string;
  occurredRange?: Dayjs[];
  operation?: string;
  requestId?: string;
  result?: AuditResult;
  targetId?: string;
  targetType?: string;
}

const store = useAdminStore();
const { t, dateTime } = useI18n();
const detailOpen = ref(false);
const detailId = ref<Int64String | null>(null);
const fileAccessUrls = ref(new Map<Int64String, string>());
let fileAccessRequest = 0;

const resultOptions = computed(() => [
  { label: t('auditSuccess'), value: 'SUCCESS' },
  { label: t('auditDenied'), value: 'DENIED' },
  { label: t('auditFailed'), value: 'FAILED' },
]);
const operationOptions = computed(() =>
  Object.entries(operationLabels.value).map(([value, label]) => ({
    label,
    value,
  })),
);
const displayText = computed<OperationLogDisplayText>(() => ({
  empty: '-',
  operationLabels: operationLabels.value,
  targetNames: targetNames.value,
  targetTypeLabels: targetTypeLabels.value,
}));
const targetNames = computed<Readonly<Record<string, string>>>(() => {
  const values = new Map<string, string>();
  store.accounts.page.items.forEach((account) => {
    const key = resourceReferenceKey('account', account.id);
    if (key) values.set(key, account.displayName);
  });
  store.roles.roles.forEach((role: PlatformRole) => {
    const key = resourceReferenceKey('role', role.id);
    if (key) values.set(key, role.name);
  });
  store.emailSenders.senders.forEach((sender: EmailSender) => {
    const key = resourceReferenceKey(
      'notification:email:sender',
      sender.senderId,
    );
    if (key) values.set(key, sender.name);
  });
  store.emailTemplates.templates.forEach((template: EmailTemplate) => {
    const key = resourceReferenceKey(
      'notification:email:template',
      template.templateId,
    );
    if (key) values.set(key, template.notificationCode);
  });
  return Object.fromEntries(values);
});
const operationLabels = computed<Readonly<Record<string, string>>>(() => ({
  'authentication/account/passkey/delete': t(
    'auditOperationAuthenticationAccountPasskeyDelete',
  ),
  'authentication/account/passkey/register': t(
    'auditOperationAuthenticationAccountPasskeyRegister',
  ),
  'authentication/account/password/replace': t(
    'auditOperationAuthenticationAccountPasswordReplace',
  ),
  'authentication/account/settings/replace': t(
    'auditOperationAuthenticationAccountSettingsReplace',
  ),
  'authentication/account/totp/activate': t(
    'auditOperationAuthenticationAccountTotpActivate',
  ),
  'authentication/account/totp/delete': t(
    'auditOperationAuthenticationAccountTotpDelete',
  ),
  'authentication/account/totp/replace': t(
    'auditOperationAuthenticationAccountTotpReplace',
  ),
  'platform/account/avatar/delete': t(
    'auditOperationPlatformAccountAvatarDelete',
  ),
  'platform/account/avatar/replace': t(
    'auditOperationPlatformAccountAvatarReplace',
  ),
  'platform/account/create': t('auditOperationPlatformAccountCreate'),
  'platform/account/delete': t('auditOperationPlatformAccountDelete'),
  'platform/account/roles/replace': t(
    'auditOperationPlatformAccountRolesReplace',
  ),
  'platform/account/status/change': t(
    'auditOperationPlatformAccountStatusChange',
  ),
  'platform/profile/avatar/delete': t(
    'auditOperationPlatformProfileAvatarDelete',
  ),
  'platform/profile/avatar/replace': t(
    'auditOperationPlatformProfileAvatarReplace',
  ),
  'platform/profile/replace': t('auditOperationPlatformProfileReplace'),
  'platform/role/create': t('auditOperationPlatformRoleCreate'),
  'platform/role/delete': t('auditOperationPlatformRoleDelete'),
  'platform/role/permissions/replace': t(
    'auditOperationPlatformRolePermissionsReplace',
  ),
  'platform/role/update': t('auditOperationPlatformRoleUpdate'),
  'storage/file/access-url/create': t(
    'auditOperationStorageFileAccessUrlCreate',
  ),
  'storage/file/upload/complete': t('auditOperationStorageFileUploadComplete'),
  'storage/file/upload/create': t('auditOperationStorageFileUploadCreate'),
  'system/dictionary/create': t('auditOperationSystemDictionaryCreate'),
  'system/dictionary/delete': t('auditOperationSystemDictionaryDelete'),
  'system/dictionary/option/create': t(
    'auditOperationSystemDictionaryOptionCreate',
  ),
  'system/dictionary/option/delete': t(
    'auditOperationSystemDictionaryOptionDelete',
  ),
  'system/dictionary/option/update': t(
    'auditOperationSystemDictionaryOptionUpdate',
  ),
  'system/dictionary/update': t('auditOperationSystemDictionaryUpdate'),
  'system/email/sender/connection/test': t(
    'auditOperationSystemEmailSenderConnectionTest',
  ),
  'system/email/sender/create': t('auditOperationSystemEmailSenderCreate'),
  'system/email/sender/delete': t('auditOperationSystemEmailSenderDelete'),
  'system/email/sender/replace': t('auditOperationSystemEmailSenderReplace'),
  'system/email/sender/status/change': t(
    'auditOperationSystemEmailSenderStatusChange',
  ),
  'system/email/template/create': t('auditOperationSystemEmailTemplateCreate'),
  'system/email/template/delete': t('auditOperationSystemEmailTemplateDelete'),
  'system/email/template/replace': t(
    'auditOperationSystemEmailTemplateReplace',
  ),
  'system/email/template/status/change': t(
    'auditOperationSystemEmailTemplateStatusChange',
  ),
  'system/email/template/test-message/send': t(
    'auditOperationSystemEmailTemplateTestMessageSend',
  ),
  'system/setting/replace': t('auditOperationSystemSettingReplace'),
  'system/setting/reset': t('auditOperationSystemSettingReset'),
}));
const targetTypeLabels = computed<Readonly<Record<string, string>>>(() => ({
  account: t('auditTargetAccount'),
  file: t('auditTargetFile'),
  'notification:email:sender': t('auditTargetEmailSender'),
  'notification:email:template': t('auditTargetEmailTemplate'),
  'passkey:credential': t('auditTargetPasskeyCredential'),
  'platform:user:profile': t('auditTargetPlatformUserProfile'),
  role: t('auditTargetRole'),
}));

watch(
  () => store.operationLogs.page.items,
  (items) => void loadFileAccessUrls(items),
  { immediate: true },
);

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: t('traceIdPlaceholder'),
        },
        fieldName: 'requestId',
        label: t('requestId'),
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          optionFilterProp: 'label',
          options: operationOptions.value,
          placeholder: t('operationPlaceholder'),
          showSearch: true,
        },
        fieldName: 'operation',
        label: t('operation'),
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: resultOptions.value,
          placeholder: t('allResults'),
        },
        fieldName: 'result',
        label: t('result'),
      },
      {
        component: 'Input',
        componentProps: {
          allowClear: true,
          inputmode: 'numeric',
          placeholder: t('accountIdPlaceholder'),
        },
        fieldName: 'accountId',
        label: t('accountId'),
      },
      {
        component: 'Input',
        componentProps: {
          allowClear: true,
          inputmode: 'numeric',
          placeholder: t('principalIdPlaceholder'),
        },
        fieldName: 'actorPrincipalId',
        label: t('principalId'),
      },
      {
        component: 'RangePicker',
        componentProps: {
          class: 'w-full',
          showTime: true,
        },
        fieldName: 'occurredRange',
        label: t('occurredRange'),
      },
      {
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: t('targetTypePlaceholder'),
        },
        fieldName: 'targetType',
        label: t('targetType'),
      },
      {
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: t('targetIdPlaceholder'),
        },
        fieldName: 'targetId',
        label: t('targetId'),
      },
    ],
    submitOnEnter: true,
  },
  gridOptions: {
    columns: [
      {
        field: 'occurredAt',
        minWidth: 178,
        slots: { default: 'occurredAt' },
        title: t('occurredAt'),
      },
      {
        field: 'actor',
        minWidth: 190,
        slots: { default: 'actor' },
        title: t('operator'),
      },
      {
        field: 'operation',
        minWidth: 250,
        slots: { default: 'operation' },
        title: t('operation'),
      },
      {
        field: 'result',
        slots: { default: 'result' },
        title: t('result'),
        width: 110,
      },
      {
        field: 'target',
        minWidth: 230,
        slots: { default: 'target' },
        title: t('targetResource'),
      },
      {
        field: 'requestId',
        minWidth: 220,
        slots: { default: 'requestId' },
        title: t('requestId'),
      },
      {
        field: 'actions',
        fixed: 'right',
        slots: { default: 'actions' },
        title: t('actions'),
        width: 72,
      },
    ],
    height: 'auto',
    pagerConfig: {},
    proxyConfig: {
      ajax: {
        query: async ({ page }, values: OperationLogSearchValues) => {
          const filters = operationLogFilters(
            values,
            page.currentPage,
            page.pageSize,
          );
          if (!filters) return store.operationLogs.page;
          await store.loadOperationLogs(filters);
          return store.operationLogs.page;
        },
      },
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      search: true,
      zoom: true,
    },
  } as VxeTableGridOptions<OperationLog>,
});

/** 打开抽屉并读取选中日志的完整详情。 */
async function openDetail(id: Int64String): Promise<void> {
  detailId.value = id;
  detailOpen.value = true;
  try {
    await store.loadOperationLog(id);
  } catch {
    // 详情抽屉使用独立状态展示错误。
  }
}

/** 重试当前详情请求。 */
async function retryDetail(): Promise<void> {
  if (detailId.value) await openDetail(detailId.value);
}

/** 抽屉关闭后清除上一次详情，避免再次打开时显示旧数据。 */
function detailVisibilityChanged(open: boolean): void {
  if (open) return;
  detailId.value = null;
  store.clearOperationLog();
}

/** 校验 Vben 搜索表单并转换成后端仍然使用的操作日志查询参数。 */
function operationLogFilters(
  values: OperationLogSearchValues,
  page: number,
  size: number,
): null | {
  accountId: string;
  actorPrincipalId: string;
  occurredFrom: string;
  occurredTo: string;
  operation: string;
  page: number;
  requestId: string;
  result: '' | AuditResult;
  size: number;
  targetId: string;
  targetType: string;
} {
  const targetType = (values.targetType ?? '').trim();
  const targetId = (values.targetId ?? '').trim();
  const accountId = (values.accountId ?? '').trim();
  const actorPrincipalId = (values.actorPrincipalId ?? '').trim();
  if (Boolean(targetType) !== Boolean(targetId)) {
    message.warning(t('targetFilterPair'));
    return null;
  }
  if (
    [accountId, actorPrincipalId].some(
      (value) => value && !isPositiveInteger(value),
    )
  ) {
    message.warning(t('positiveIdentifierRequired'));
    return null;
  }
  return {
    accountId,
    actorPrincipalId,
    occurredFrom: values.occurredRange?.[0]?.toISOString() ?? '',
    occurredTo: values.occurredRange?.[1]?.toISOString() ?? '',
    operation: values.operation ?? '',
    page,
    requestId: (values.requestId ?? '').trim(),
    result: values.result ?? '',
    size,
    targetId,
    targetType,
  };
}

/** 返回操作人在列表中使用的名称和 Principal 标识。 */
function actorReference(log: OperationLog): ResourceReferenceData {
  return {
    id: log.principalId,
    name: log.principalDisplayName,
    resourceType: 'principal',
    typeLabel: null,
  };
}

function actionText(log: OperationLog): string {
  return operationLogActionText(log, displayText.value);
}

/** 返回日志目标当前可以显示的名称、类型和原始标识。 */
function targetReference(log: OperationLog): ResourceReferenceData {
  return operationLogTargetReference(log, displayText.value);
}

/** 返回日志 Account 在已加载账号页中可以匹配到的名称。 */
function accountReference(log: OperationLog): ResourceReferenceData {
  const account = log.accountId
    ? store.accounts.page.items.find((item) => item.id === log.accountId)
    : undefined;
  return {
    id: log.accountId,
    name: account?.displayName ?? null,
    resourceType: 'account',
    typeLabel: t('auditTargetAccount'),
  };
}

/** 返回没有独立管理页的历史 Subject 引用。 */
function subjectReference(log: OperationLog): ResourceReferenceData {
  return {
    id: log.subjectId,
    name: null,
    resourceType: 'subject',
    typeLabel: t('subjectId'),
  };
}

/** 返回日志详情中的 Principal 引用和操作时记录的展示名称。 */
function principalReference(log: OperationLog): ResourceReferenceData {
  return {
    id: log.principalId,
    name: log.principalDisplayName,
    resourceType: 'principal',
    typeLabel: t('principalId'),
  };
}

/** 返回目标资源已有管理页面的深链接。 */
function targetRoute(log: OperationLog) {
  return resourceReferenceRoute(targetReference(log), resourceRouteAccess());
}

/** 返回日志 Account 在当前 Session 有权访问时使用的管理页面链接。 */
function accountRoute(log: OperationLog) {
  return resourceReferenceRoute(accountReference(log), resourceRouteAccess());
}

/** 返回当前 Session 对资源管理页的读取权限。 */
function resourceRouteAccess() {
  return {
    accounts: store.session.capabilities.accountsRead,
    email: store.session.capabilities.emailRead,
    roles: store.session.capabilities.rolesRead,
  };
}

/** 返回当前账号有权读取的头像文件短期地址。 */
function targetExternalUrl(log: OperationLog): null | string {
  const reference = targetReference(log);
  return reference.resourceType === 'file' && reference.id
    ? (fileAccessUrls.value.get(reference.id) ?? null)
    : null;
}

/** 为当前日志页中的头像文件解析短期地址，失败时继续保留文件标识。 */
async function loadFileAccessUrls(
  items: readonly OperationLog[],
): Promise<void> {
  const request = ++fileAccessRequest;
  const fileIds = [
    ...new Set(
      items.flatMap((item) =>
        item.targetType === 'file' && item.targetId ? [item.targetId] : [],
      ),
    ),
  ];
  if (fileIds.length === 0) {
    fileAccessUrls.value = new Map();
    return;
  }
  try {
    const resolved = await store.resolveAvatarUrls(fileIds);
    if (request === fileAccessRequest) {
      fileAccessUrls.value = new Map(resolved);
    }
  } catch {
    if (request === fileAccessRequest) fileAccessUrls.value = new Map();
  }
}

function resultColor(result: AuditResult): string {
  if (result === 'SUCCESS') return 'green';
  if (result === 'DENIED') return 'orange';
  return 'red';
}

function resultIcon(result: AuditResult): Component {
  if (result === 'SUCCESS') return CircleCheck;
  if (result === 'DENIED') return ShieldX;
  return TriangleAlert;
}

function resultText(result: AuditResult): string {
  if (result === 'SUCCESS') return t('auditSuccess');
  if (result === 'DENIED') return t('auditDenied');
  return t('auditFailed');
}

function payloadText(log: OperationLog): string {
  if (!log.payloadJson) return '-';
  try {
    return JSON.stringify(JSON.parse(log.payloadJson), null, 2);
  } catch {
    return log.payloadJson;
  }
}

function isPositiveInteger(value: string): boolean {
  return /^[1-9][0-9]*$/.test(value.trim());
}
</script>

<template>
  <Page auto-content-height>
    <Grid class="h-full" :table-title="t('operationLogs')">
      <template #table-title>
        <div class="flex min-w-0 flex-col items-start gap-0.5">
          <span>{{ t('operationLogs') }}</span>
          <span class="text-muted-foreground text-xs font-normal">
            {{ t('totalItems', { count: store.operationLogs.page.total }) }}
          </span>
        </div>
      </template>
      <template #occurredAt="{ row }">
        <time :datetime="row.occurredAt">
          {{ dateTime(row.occurredAt) }}
        </time>
      </template>
      <template #actor="{ row }">
        <ResourceReference
          :empty-text="displayText.empty"
          :id="actorReference(row).id"
          :name="actorReference(row).name"
        />
      </template>
      <template #operation="{ row }">
        <div class="flex min-w-0 flex-col gap-0.5 text-left">
          <strong class="truncate">{{ actionText(row) }}</strong>
          <code class="text-muted-foreground truncate text-xs">{{
            row.operation
          }}</code>
        </div>
      </template>
      <template #result="{ row }">
        <a-tag :color="resultColor(row.result)">
          <span class="inline-flex items-center gap-1">
            <component
              :is="resultIcon(row.result)"
              :size="13"
              aria-hidden="true"
            />
            {{ resultText(row.result) }}
          </span>
        </a-tag>
      </template>
      <template #target="{ row }">
        <ResourceReference
          :empty-text="displayText.empty"
          :external-url="targetExternalUrl(row)"
          :id="targetReference(row).id"
          :name="targetReference(row).name"
          :to="targetRoute(row) ?? undefined"
          :type-label="targetReference(row).typeLabel"
        />
      </template>
      <template #requestId="{ row }">
        <span class="block truncate text-left" :title="row.requestId">
          {{ row.requestId }}
        </span>
      </template>
      <template #actions="{ row }">
        <a-tooltip :title="t('viewDetails')">
          <a-button
            type="text"
            :aria-label="t('viewDetails')"
            @click="openDetail(row.id)"
          >
            <Eye :size="17" aria-hidden="true" />
          </a-button>
        </a-tooltip>
      </template>
    </Grid>

    <a-drawer
      v-model:open="detailOpen"
      width="min(620px, 100vw)"
      :title="
        store.operationLogs.selected
          ? actionText(store.operationLogs.selected)
          : t('operationLogDetails')
      "
      @after-open-change="detailVisibilityChanged"
    >
      <ResourceState
        :phase="store.operationLogs.detailPhase"
        :error="store.operationLogs.detailError"
        @retry="retryDetail"
      >
        <template v-if="store.operationLogs.selected">
          <a-descriptions bordered size="small" :column="1">
            <a-descriptions-item :label="t('occurredAt')">
              {{ dateTime(store.operationLogs.selected.occurredAt) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('operator')">
              <ResourceReference
                :empty-text="displayText.empty"
                :id="actorReference(store.operationLogs.selected).id"
                :name="actorReference(store.operationLogs.selected).name"
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('subjectId')">
              <ResourceReference
                :empty-text="displayText.empty"
                :id="subjectReference(store.operationLogs.selected).id"
                :type-label="
                  subjectReference(store.operationLogs.selected).typeLabel
                "
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('accountId')">
              <ResourceReference
                :empty-text="displayText.empty"
                :id="accountReference(store.operationLogs.selected).id"
                :name="accountReference(store.operationLogs.selected).name"
                :to="
                  accountReference(store.operationLogs.selected).id
                    ? (accountRoute(store.operationLogs.selected) ?? undefined)
                    : undefined
                "
                :type-label="
                  accountReference(store.operationLogs.selected).typeLabel
                "
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('principalId')">
              <ResourceReference
                :empty-text="displayText.empty"
                :id="principalReference(store.operationLogs.selected).id"
                :name="principalReference(store.operationLogs.selected).name"
                :type-label="
                  principalReference(store.operationLogs.selected).typeLabel
                "
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('operation')">
              {{ actionText(store.operationLogs.selected) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('operationCode')">
              <code class="log-code">{{
                store.operationLogs.selected.operation
              }}</code>
            </a-descriptions-item>
            <a-descriptions-item :label="t('result')">
              <a-tag :color="resultColor(store.operationLogs.selected.result)">
                {{ resultText(store.operationLogs.selected.result) }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item :label="t('targetResource')">
              <ResourceReference
                :empty-text="displayText.empty"
                :external-url="targetExternalUrl(store.operationLogs.selected)"
                :id="targetReference(store.operationLogs.selected).id"
                :name="targetReference(store.operationLogs.selected).name"
                :to="targetRoute(store.operationLogs.selected) ?? undefined"
                :type-label="
                  targetReference(store.operationLogs.selected).typeLabel
                "
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('targetType')">
              <code class="log-code">{{
                store.operationLogs.selected.targetType || '-'
              }}</code>
            </a-descriptions-item>
            <a-descriptions-item :label="t('requestId')">
              <a-typography-text copyable>
                {{ store.operationLogs.selected.requestId }}
              </a-typography-text>
            </a-descriptions-item>
            <a-descriptions-item :label="t('httpRequest')">
              <code class="log-code">
                {{ store.operationLogs.selected.httpMethod || '-' }}
                {{ store.operationLogs.selected.httpPath || '' }}
              </code>
            </a-descriptions-item>
            <a-descriptions-item :label="t('clientIp')">
              {{ store.operationLogs.selected.clientIp || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('userAgent')">
              <span class="break-all">
                {{ store.operationLogs.selected.userAgent || '-' }}
              </span>
            </a-descriptions-item>
          </a-descriptions>

          <section class="mt-5" aria-labelledby="audit-payload-heading">
            <h2 id="audit-payload-heading" class="mb-2 text-sm font-semibold">
              {{ t('auditPayload') }}
            </h2>
            <pre class="audit-payload border-border border">{{
              payloadText(store.operationLogs.selected)
            }}</pre>
          </section>
        </template>
      </ResourceState>
    </a-drawer>
  </Page>
</template>

<style scoped>
.log-code {
  overflow-wrap: anywhere;
  white-space: normal;
}

.audit-payload {
  background: hsl(var(--muted) / 45%);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
  max-height: 360px;
  overflow: auto;
  overflow-wrap: anywhere;
  padding: 12px;
  white-space: pre-wrap;
}
</style>
