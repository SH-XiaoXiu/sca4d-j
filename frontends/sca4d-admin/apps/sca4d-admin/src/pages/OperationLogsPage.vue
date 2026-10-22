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

import {
  operationLogActionText,
  operationLogTargetReference,
  resourceReferenceKey,
} from '@sca4d/admin';
import { message } from 'ant-design-vue';
import { CircleCheck, Eye, ShieldX, TriangleAlert } from 'lucide-vue-next';

import {
  SjPage,
  SjResourceEditor,
  SjResourceReference,
  SjResourceState,
  useSjVxeGrid,
} from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

import { resourceReferenceRoute } from '../platform/resource-reference-routes';

const { t, dateTime } = useSjPresentation();

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

const accounts = useSjControllerState(businessApplication.accounts);
const roles = useSjControllerState(businessApplication.roles);
const operationLogs = useSjControllerState(businessApplication.operationLogs);
const emailSenders = useSjControllerState(businessApplication.emailSenders);
const emailTemplates = useSjControllerState(businessApplication.emailTemplates);
const session = useSjAccess();
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
  accounts.value.page.items.forEach((account) => {
    const key = resourceReferenceKey('IAM/ACCOUNT', account.id);
    if (key) values.set(key, account.displayName);
  });
  roles.value.roles.forEach((role: PlatformRole) => {
    const key = resourceReferenceKey('IAM/ROLE', role.id);
    if (key) values.set(key, role.name);
  });
  emailSenders.value.senders.forEach((sender: EmailSender) => {
    const key = resourceReferenceKey(
      'NOTIFICATION/EMAIL/SENDERS',
      sender.senderId,
    );
    if (key) values.set(key, sender.name);
  });
  emailTemplates.value.templates.forEach((template: EmailTemplate) => {
    const key = resourceReferenceKey(
      'NOTIFICATION/EMAIL/TEMPLATES',
      template.templateId,
    );
    if (key) values.set(key, template.notificationCode);
  });
  return Object.fromEntries(values);
});
const operationLabels = computed<Readonly<Record<string, string>>>(() => ({
  'AUTHENTICATION/ACCOUNT/PASSKEY/DELETE': t(
    'auditOperationAuthenticationAccountPasskeyDelete',
  ),
  'AUTHENTICATION/ACCOUNT/PASSKEY/REGISTER': t(
    'auditOperationAuthenticationAccountPasskeyRegister',
  ),
  'AUTHENTICATION/ACCOUNT/PASSWORD/REPLACE': t(
    'auditOperationAuthenticationAccountPasswordReplace',
  ),
  'AUTHENTICATION/ACCOUNT/SETTINGS/REPLACE': t(
    'auditOperationAuthenticationAccountSettingsReplace',
  ),
  'AUTHENTICATION/ACCOUNT/TOTP/ACTIVATE': t(
    'auditOperationAuthenticationAccountTotpActivate',
  ),
  'AUTHENTICATION/ACCOUNT/TOTP/DELETE': t(
    'auditOperationAuthenticationAccountTotpDelete',
  ),
  'AUTHENTICATION/ACCOUNT/TOTP/REPLACE': t(
    'auditOperationAuthenticationAccountTotpReplace',
  ),
  'PLATFORM/ACCOUNT/AVATAR/DELETE': t(
    'auditOperationPlatformAccountAvatarDelete',
  ),
  'PLATFORM/ACCOUNT/AVATAR/REPLACE': t(
    'auditOperationPlatformAccountAvatarReplace',
  ),
  'PLATFORM/ACCOUNT/CREATE': t('auditOperationPlatformAccountCreate'),
  'PLATFORM/ACCOUNT/DELETE': t('auditOperationPlatformAccountDelete'),
  'PLATFORM/ACCOUNT/ROLES/REPLACE': t(
    'auditOperationPlatformAccountRolesReplace',
  ),
  'PLATFORM/ACCOUNT/STATUS/CHANGE': t(
    'auditOperationPlatformAccountStatusChange',
  ),
  'PLATFORM/PROFILE/AVATAR/DELETE': t(
    'auditOperationPlatformProfileAvatarDelete',
  ),
  'PLATFORM/PROFILE/AVATAR/REPLACE': t(
    'auditOperationPlatformProfileAvatarReplace',
  ),
  'PLATFORM/PROFILE/REPLACE': t('auditOperationPlatformProfileReplace'),
  'PLATFORM/ROLE/CREATE': t('auditOperationPlatformRoleCreate'),
  'PLATFORM/ROLE/DELETE': t('auditOperationPlatformRoleDelete'),
  'PLATFORM/ROLE/PERMISSIONS/REPLACE': t(
    'auditOperationPlatformRolePermissionsReplace',
  ),
  'PLATFORM/ROLE/UPDATE': t('auditOperationPlatformRoleUpdate'),
  'STORAGE/FILE/ACCESS_URL/CREATE': t(
    'auditOperationStorageFileAccessUrlCreate',
  ),
  'STORAGE/FILE/UPLOAD/COMPLETE': t('auditOperationStorageFileUploadComplete'),
  'STORAGE/FILE/UPLOAD/CREATE': t('auditOperationStorageFileUploadCreate'),
  'SYSTEM/DICTIONARY/CREATE': t('auditOperationSystemDictionaryCreate'),
  'SYSTEM/DICTIONARY/DELETE': t('auditOperationSystemDictionaryDelete'),
  'SYSTEM/DICTIONARY/OPTION/CREATE': t(
    'auditOperationSystemDictionaryOptionCreate',
  ),
  'SYSTEM/DICTIONARY/OPTION/DELETE': t(
    'auditOperationSystemDictionaryOptionDelete',
  ),
  'SYSTEM/DICTIONARY/OPTION/UPDATE': t(
    'auditOperationSystemDictionaryOptionUpdate',
  ),
  'SYSTEM/DICTIONARY/UPDATE': t('auditOperationSystemDictionaryUpdate'),
  'SYSTEM/EMAIL/SENDER/CONNECTION/TEST': t(
    'auditOperationSystemEmailSenderConnectionTest',
  ),
  'SYSTEM/EMAIL/SENDER/CREATE': t('auditOperationSystemEmailSenderCreate'),
  'SYSTEM/EMAIL/SENDER/DELETE': t('auditOperationSystemEmailSenderDelete'),
  'SYSTEM/EMAIL/SENDER/REPLACE': t('auditOperationSystemEmailSenderReplace'),
  'SYSTEM/EMAIL/SENDER/STATUS/CHANGE': t(
    'auditOperationSystemEmailSenderStatusChange',
  ),
  'SYSTEM/EMAIL/TEMPLATE/CREATE': t('auditOperationSystemEmailTemplateCreate'),
  'SYSTEM/EMAIL/TEMPLATE/DELETE': t('auditOperationSystemEmailTemplateDelete'),
  'SYSTEM/EMAIL/TEMPLATE/REPLACE': t(
    'auditOperationSystemEmailTemplateReplace',
  ),
  'SYSTEM/EMAIL/TEMPLATE/STATUS/CHANGE': t(
    'auditOperationSystemEmailTemplateStatusChange',
  ),
  'SYSTEM/EMAIL/TEMPLATE/TEST_MESSAGE/SEND': t(
    'auditOperationSystemEmailTemplateTestMessageSend',
  ),
  'SYSTEM/SETTING/REPLACE': t('auditOperationSystemSettingReplace'),
  'SYSTEM/SETTING/RESET': t('auditOperationSystemSettingReset'),
}));
const targetTypeLabels = computed<Readonly<Record<string, string>>>(() => ({
  'IAM/ACCOUNT': t('auditTargetAccount'),
  'STORAGE/FILE': t('auditTargetFile'),
  'NOTIFICATION/EMAIL/SENDERS': t('auditTargetEmailSender'),
  'NOTIFICATION/EMAIL/TEMPLATES': t('auditTargetEmailTemplate'),
  'AUTHENTICATION/PASSKEY/CREDENTIALS': t('auditTargetPasskeyCredential'),
  'PLATFORM:USERS:PROFILES': t('auditTargetPlatformUserProfile'),
  'IAM/ROLE': t('auditTargetRole'),
}));

watch(
  () => operationLogs.value.page.items,
  (items) => void loadFileAccessUrls(items),
  { immediate: true },
);

const [Grid] = useSjVxeGrid(() => ({
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
          if (!filters) return operationLogs.value.page;
          await businessApplication.operationLogs.load(filters);
          return operationLogs.value.page;
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
}));

/** 打开抽屉并读取选中日志的完整详情。 */
async function openDetail(id: Int64String): Promise<void> {
  detailId.value = id;
  detailOpen.value = true;
  try {
    await businessApplication.operationLogs.require(id);
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
  businessApplication.operationLogs.clearSelection();
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
    ? accounts.value.page.items.find((item) => item.id === log.accountId)
    : undefined;
  return {
    id: log.accountId,
    name: account?.displayName ?? null,
    resourceType: 'IAM/ACCOUNT',
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
    accounts: session.value.capabilities.accountsRead,
    email: session.value.capabilities.emailRead,
    roles: session.value.capabilities.rolesRead,
  };
}

/** 返回当前账号有权读取的头像文件短期地址。 */
function targetExternalUrl(log: OperationLog): null | string {
  const reference = targetReference(log);
  return reference.resourceType === 'STORAGE/FILE' && reference.id
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
        item.targetType === 'STORAGE/FILE' && item.targetId
          ? [item.targetId]
          : [],
      ),
    ),
  ];
  if (fileIds.length === 0) {
    fileAccessUrls.value = new Map();
    return;
  }
  try {
    const resolved = await businessApplication.files.resolveAccessUrls(fileIds);
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
  <SjPage auto-content-height>
    <Grid class="h-full" :table-title="t('operationLogs')">
      <template #table-title>
        <div class="flex min-w-0 flex-col items-start gap-0.5">
          <span>{{ t('operationLogs') }}</span>
          <span class="text-muted-foreground text-xs font-normal">
            {{ t('totalItems', { count: operationLogs.page.total }) }}
          </span>
        </div>
      </template>
      <template #occurredAt="{ row }">
        <time :datetime="row.occurredAt">
          {{ dateTime(row.occurredAt) }}
        </time>
      </template>
      <template #actor="{ row }">
        <SjResourceReference
          :empty-text="displayText.empty"
          :id="actorReference(row).id"
          :name="actorReference(row).name"
        />
      </template>
      <template #operation="{ row }">
        <div class="flex min-w-0 flex-col gap-0.5 text-left">
          <strong class="truncate">{{ actionText(row) }}</strong>
          <code class="text-muted-foreground truncate text-xs">
            {{ row.operation }}
          </code>
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
        <SjResourceReference
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

    <SjResourceEditor
      v-model:open="detailOpen"
      :title="
        operationLogs.selected
          ? actionText(operationLogs.selected)
          : t('operationLogDetails')
      "
      @closed="detailVisibilityChanged(false)"
      :create="false"
      size="lg"
      read-only
    >
      <SjResourceState
        :phase="operationLogs.detailPhase"
        :error="operationLogs.detailError"
        @retry="retryDetail"
      >
        <template v-if="operationLogs.selected">
          <a-descriptions bordered size="small" :column="1">
            <a-descriptions-item :label="t('occurredAt')">
              {{ dateTime(operationLogs.selected.occurredAt) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('operator')">
              <SjResourceReference
                :empty-text="displayText.empty"
                :id="actorReference(operationLogs.selected).id"
                :name="actorReference(operationLogs.selected).name"
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('subjectId')">
              <SjResourceReference
                :empty-text="displayText.empty"
                :id="subjectReference(operationLogs.selected).id"
                :type-label="subjectReference(operationLogs.selected).typeLabel"
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('accountId')">
              <SjResourceReference
                :empty-text="displayText.empty"
                :id="accountReference(operationLogs.selected).id"
                :name="accountReference(operationLogs.selected).name"
                :to="
                  accountReference(operationLogs.selected).id
                    ? (accountRoute(operationLogs.selected) ?? undefined)
                    : undefined
                "
                :type-label="accountReference(operationLogs.selected).typeLabel"
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('principalId')">
              <SjResourceReference
                :empty-text="displayText.empty"
                :id="principalReference(operationLogs.selected).id"
                :name="principalReference(operationLogs.selected).name"
                :type-label="
                  principalReference(operationLogs.selected).typeLabel
                "
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('operation')">
              {{ actionText(operationLogs.selected) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('operationCode')">
              <code class="log-code">
                {{ operationLogs.selected.operation }}
              </code>
            </a-descriptions-item>
            <a-descriptions-item :label="t('result')">
              <a-tag :color="resultColor(operationLogs.selected.result)">
                {{ resultText(operationLogs.selected.result) }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item :label="t('targetResource')">
              <SjResourceReference
                :empty-text="displayText.empty"
                :external-url="targetExternalUrl(operationLogs.selected)"
                :id="targetReference(operationLogs.selected).id"
                :name="targetReference(operationLogs.selected).name"
                :to="targetRoute(operationLogs.selected) ?? undefined"
                :type-label="targetReference(operationLogs.selected).typeLabel"
              />
            </a-descriptions-item>
            <a-descriptions-item :label="t('targetType')">
              <code class="log-code">
                {{ operationLogs.selected.targetType || '-' }}
              </code>
            </a-descriptions-item>
            <a-descriptions-item :label="t('requestId')">
              <a-typography-text copyable>
                {{ operationLogs.selected.requestId }}
              </a-typography-text>
            </a-descriptions-item>
            <a-descriptions-item :label="t('httpRequest')">
              <code class="log-code">
                {{ operationLogs.selected.httpMethod || '-' }}
                {{ operationLogs.selected.httpPath || '' }}
              </code>
            </a-descriptions-item>
            <a-descriptions-item :label="t('clientIp')">
              {{ operationLogs.selected.clientIp || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('userAgent')">
              <span class="break-all">
                {{ operationLogs.selected.userAgent || '-' }}
              </span>
            </a-descriptions-item>
          </a-descriptions>

          <section class="mt-5" aria-labelledby="audit-payload-heading">
            <h2 id="audit-payload-heading" class="mb-2 text-sm font-semibold">
              {{ t('auditPayload') }}
            </h2>
            <pre class="audit-payload border-border border">{{
              payloadText(operationLogs.selected)
            }}</pre>
          </section>
        </template>
      </SjResourceState>
    </SjResourceEditor>
  </SjPage>
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
