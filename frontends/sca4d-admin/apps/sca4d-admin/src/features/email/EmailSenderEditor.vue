<script setup lang="ts">
import type { EmailSender, Int64String, SmtpSecurity } from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { onScopeDispose as disposeEditor, watch as watchEditor } from 'vue';
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { message } from 'ant-design-vue';

import { SjResourceEditor } from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { businessApplication } from '#/platform/business-application';
import {
  resourceQueryId,
  withoutResourceQuery,
} from '#/platform/resource-reference-routes';

const { errorText } = useSjPresentation();

let editorGeneration = 0;
disposeEditor(() => {
  editorGeneration++;
});

type SenderPasswordMode = 'clear' | 'keep' | 'none' | 'replace';

const emailSenders = useSjControllerState(businessApplication.emailSenders);
const session = useSjAccess();
const route = useRoute();
const router = useRouter();
const canManage = computed(() => session.value.capabilities.emailManage);

const senderDrawerOpen = ref(false);
const senderLoading = ref(false);
const senderSaving = ref(false);
const senderFormRef = ref<FormInstance>();
const editingSender = ref<EmailSender | null>(null);
const senderPasswordMode = ref<SenderPasswordMode>('none');
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

/** 打开 SMTP 配置创建或详情抽屉。 */
async function openSender(
  sender: EmailSender | Int64String | null = null,
): Promise<void> {
  const read = ++editorGeneration;
  const senderId = typeof sender === 'string' ? sender : sender?.senderId;
  editingSender.value = typeof sender === 'string' ? null : sender;
  senderDrawerOpen.value = true;
  senderLoading.value = senderId !== undefined && senderId !== null;
  businessApplication.emailSenders.clearDetail();
  try {
    const detail = senderId
      ? await businessApplication.emailSenders.require(senderId)
      : null;
    if (read !== editorGeneration) return;
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
    if (read !== editorGeneration) return;
    senderDrawerOpen.value = false;
    message.error(errorText(emailSenders.value.error));
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
      await businessApplication.emailSenders.replace({
        ...common,
        passwordChanged: mode === 'replace' || mode === 'clear',
      });
      message.success(t('updated'));
    } else {
      await businessApplication.emailSenders.create(common);
      message.success(t('created'));
    }
    senderDrawerOpen.value = false;
    businessApplication.emailSenders.clearDetail();
  } catch {
    message.error(errorText(emailSenders.value.error));
  } finally {
    senderSaving.value = false;
  }
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
/** 提供 SMTP 创建与编辑 的独立工作区入口。 */
defineExpose({ open: openSender });

watchEditor(senderDrawerOpen, (open) => {
  if (!open) editorGeneration++;
});
</script>
<template>
  <SjResourceEditor
    :read-only="!canManage"
    v-model:open="senderDrawerOpen"
    :title="editingSender ? t('senderDetails') : t('createSender')"
    @closed="
      businessApplication.emailSenders.clearDetail();
      emailDrawerVisibilityChanged(false, 'senderId');
      senderForm.password = '';
    "
    :create="editingSender === null"
    size="lg"
    :saving="senderSaving"
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
          :rules="[{ type: 'email', message: t('invalidEmail') }, { max: 320 }]"
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
  </SjResourceEditor>
</template>
