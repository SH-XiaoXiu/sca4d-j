<script setup lang="ts">
defineOptions({ name: "MailSendersPage" });

import {
  createIdempotencyKey,
  isApiError,
  type Id,
  type MailSenderStatus,
  type MailSenderView,
  type SmtpSecurity
} from "@sca4d/admin-common";
import { MailCheck, Pencil, PlugZap, Plus, Power, RotateCcw, Trash2 } from "lucide-vue-next";
import { message, Modal, type FormInstance } from "ant-design-vue";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import { mailSenderApi } from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const auth = useSessionStore();
const { t } = useI18n();
const loading = ref(false);
const submitting = ref(false);
const testingId = ref<Id | null>(null);
const editorOpen = ref(false);
const formRef = ref<FormInstance>();
const editing = ref<MailSenderView | null>(null);
const senders = ref<MailSenderView[]>([]);
const createKey = ref<string | null>(null);
const form = reactive({
  name: "",
  host: "",
  port: 587,
  security: "STARTTLS" as SmtpSecurity,
  authenticated: true,
  username: "",
  password: "",
  clearPassword: false,
  fromAddress: "",
  fromName: "",
  replyTo: ""
});

const canManage = computed(() => auth.hasPermission("system:mail-senders:manage"));
const editorTitle = computed(() => editing.value
  ? t(canManage.value ? "mailSender.edit" : "mailSender.details")
  : t("mailSender.create"));
const columns = computed(() => [
  { title: t("mailSender.sender"), key: "sender", width: 250 },
  { title: t("mailSender.server"), key: "server", width: 230 },
  { title: t("mailSender.security"), key: "security", width: 130 },
  { title: t("mailSender.fromAddress"), key: "from", width: 240 },
  { title: t("common.status"), key: "status", width: 110 },
  { title: t("common.actions"), key: "actions", width: 190, fixed: "right" as const }
]);
const securityOptions = computed(() => [
  { value: "NONE", label: t("mailSender.securityOptions.NONE") },
  { value: "STARTTLS", label: t("mailSender.securityOptions.STARTTLS") },
  { value: "IMPLICIT_TLS", label: t("mailSender.securityOptions.IMPLICIT_TLS") }
]);

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function findSender(senderId: Id): MailSenderView | null {
  return senders.value.find((sender) => sender.senderId === senderId) ?? null;
}

function statusColor(status: MailSenderStatus): string {
  return status === "ACTIVE" ? "green" : "default";
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    senders.value = await mailSenderApi.senders();
  } catch (error) {
    message.error(errorMessage(error, "mailSender.loadFailed"));
  } finally {
    loading.value = false;
  }
}

function resetForm(): void {
  Object.assign(form, {
    name: "",
    host: "",
    port: 587,
    security: "STARTTLS" as SmtpSecurity,
    authenticated: true,
    username: "",
    password: "",
    clearPassword: false,
    fromAddress: "",
    fromName: "",
    replyTo: ""
  });
}

function openCreate(): void {
  editing.value = null;
  createKey.value = createIdempotencyKey();
  resetForm();
  editorOpen.value = true;
}

function openEdit(sender: MailSenderView): void {
  editing.value = sender;
  createKey.value = null;
  Object.assign(form, {
    name: sender.name,
    host: sender.host,
    port: sender.port,
    security: sender.security,
    authenticated: sender.username !== null,
    username: sender.username ?? "",
    password: "",
    clearPassword: false,
    fromAddress: sender.fromAddress,
    fromName: sender.fromName ?? "",
    replyTo: sender.replyTo ?? ""
  });
  editorOpen.value = true;
  if (String(route.params.senderId ?? "") !== sender.senderId) {
    void router.push(`/system/mail-senders/${sender.senderId}`);
  }
}

function closeEditor(): void {
  editorOpen.value = false;
  editing.value = null;
  createKey.value = null;
  if (route.params.senderId) void router.push("/system/mail-senders");
}

function changeAuthentication(enabled: boolean): void {
  form.authenticated = enabled;
  if (!enabled) {
    form.username = "";
    form.password = "";
    form.clearPassword = Boolean(editing.value?.passwordConfigured);
  } else {
    form.clearPassword = false;
  }
}

async function save(): Promise<void> {
  if (!canManage.value) return;
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  submitting.value = true;
  const input = {
    name: form.name.trim(),
    host: form.host.trim(),
    port: form.port,
    security: form.security,
    ...(form.authenticated ? { username: form.username.trim() } : {}),
    ...(form.password ? { password: form.password } : {}),
    fromAddress: form.fromAddress.trim(),
    ...(form.fromName.trim() ? { fromName: form.fromName.trim() } : {}),
    ...(form.replyTo.trim() ? { replyTo: form.replyTo.trim() } : {})
  };
  try {
    if (editing.value) {
      await mailSenderApi.update(
        editing.value.senderId,
        { ...input, clearPassword: form.clearPassword },
        editing.value.version
      );
      message.success(t("mailSender.updateSuccess"));
    } else {
      createKey.value ??= createIdempotencyKey();
      await mailSenderApi.create(input, createKey.value);
      message.success(t("mailSender.createSuccess"));
    }
    form.password = "";
    closeEditor();
    await load();
  } catch (error) {
    if (isApiError(error) && (error.httpStatus === 409 || error.httpStatus === 412)) {
      const draft = { ...form, password: "" };
      const senderId = editing.value?.senderId;
      await load();
      if (senderId) {
        const latest = findSender(senderId);
        if (!latest) {
          closeEditor();
          message.error(t("mailSender.notFound"));
          return;
        }
        editing.value = latest;
      }
      Object.assign(form, draft);
      createKey.value = editing.value ? null : createIdempotencyKey();
      message.warning(t("mailSender.conflict"));
      return;
    }
    message.error(errorMessage(error, "mailSender.saveFailed"));
  } finally {
    submitting.value = false;
  }
}

async function testConnection(sender: MailSenderView): Promise<void> {
  testingId.value = sender.senderId;
  try {
    await mailSenderApi.testConnection(sender.senderId);
    message.success(t("mailSender.testSuccess"));
  } catch (error) {
    message.error(errorMessage(error, "mailSender.testFailed"));
  } finally {
    testingId.value = null;
  }
}

function changeStatus(sender: MailSenderView): void {
  const next: MailSenderStatus = sender.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  Modal.confirm({
    title: t(`mailSender.${next === "ACTIVE" ? "enableTitle" : "disableTitle"}`, { name: sender.name }),
    content: t(next === "ACTIVE" ? "mailSender.enableWarning" : "mailSender.disableWarning"),
    okText: t(next === "ACTIVE" ? "common.active" : "common.disabled"),
    cancelText: t("common.cancel"),
    onOk: async () => {
      try {
        await mailSenderApi.changeStatus(sender.senderId, next, sender.version);
        message.success(t("mailSender.statusSuccess"));
        await load();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 412) await load();
        message.error(errorMessage(error, "mailSender.statusFailed"));
      }
    }
  });
}

function remove(sender: MailSenderView): void {
  Modal.confirm({
    title: t("mailSender.deleteTitle", { name: sender.name }),
    content: t("mailSender.deleteWarning"),
    okText: t("common.delete"),
    cancelText: t("common.cancel"),
    okType: "danger",
    onOk: async () => {
      try {
        await mailSenderApi.delete(sender.senderId, sender.version);
        message.success(t("mailSender.deleteSuccess"));
        if (String(route.params.senderId ?? "") === sender.senderId) closeEditor();
        await load();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 412) await load();
        message.error(errorMessage(error, "mailSender.deleteFailed"));
      }
    }
  });
}

async function openRouteSender(senderId: string): Promise<void> {
  if (!senderId) return;
  const sender = findSender(senderId);
  if (!sender) {
    message.error(t("mailSender.notFound"));
    await router.replace("/system/mail-senders");
    return;
  }
  openEdit(sender);
}

onMounted(async () => {
  await load();
  await openRouteSender(String(route.params.senderId ?? ""));
});
watch(
  () => route.params.senderId,
  (senderId) => {
    if (senderId) void openRouteSender(String(senderId));
    else if (editing.value) {
      editorOpen.value = false;
      editing.value = null;
    }
  }
);
</script>

<template>
  <div>
    <PageHeader
      :title="t('route.mailSenders')"
      :description="t('mailSender.description')"
    >
      <template #actions>
        <a-button
          :loading="loading"
          @click="load"
        >
          <RotateCcw :size="16" />{{ t("common.refresh") }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          @click="openCreate"
        >
          <Plus :size="16" />{{ t("mailSender.create") }}
        </a-button>
      </template>
    </PageHeader>

    <section class="page-panel management-panel mail-sender-panel">
      <a-table
        class="desktop-only"
        row-key="senderId"
        :columns="columns"
        :data-source="senders"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1150 }"
      >
        <template #emptyText>
          {{ t("mailSender.empty") }}
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'sender'">
            <div class="sender-name">
              <span><MailCheck :size="18" /></span>
              <div><strong>{{ record.name }}</strong><small>{{ record.username ?? t("mailSender.anonymous") }}</small></div>
            </div>
          </template>
          <template v-else-if="column.key === 'server'">
            {{ record.host }}:{{ record.port }}
          </template>
          <template v-else-if="column.key === 'security'">
            {{ t(`mailSender.securityOptions.${record.security}`) }}
          </template>
          <template v-else-if="column.key === 'from'">
            {{ record.fromName ? `${record.fromName} <${record.fromAddress}>` : record.fromAddress }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ t(record.status === "ACTIVE" ? "common.active" : "common.disabled") }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space :size="2">
              <RowActionButton
                v-if="canManage"
                :label="t('mailSender.testConnection')"
                :disabled="testingId === record.senderId"
                @click="testConnection(record)"
              >
                <PlugZap :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                :label="t(record.status === 'ACTIVE' ? 'mailSender.disable' : 'mailSender.enable')"
                @click="changeStatus(record)"
              >
                <Power :size="17" />
              </RowActionButton>
              <RowActionButton
                :label="t(canManage ? 'common.edit' : 'common.details')"
                @click="openEdit(record)"
              >
                <Pencil :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                danger
                :disabled="record.status === 'ACTIVE'"
                :label="t('common.delete')"
                @click="remove(record)"
              >
                <Trash2 :size="17" />
              </RowActionButton>
            </a-space>
          </template>
        </template>
      </a-table>

      <div class="mobile-only">
        <a-spin :spinning="loading">
          <a-empty
            v-if="!loading && senders.length === 0"
            :description="t('mailSender.empty')"
          />
          <div
            v-else
            class="mobile-card-list"
          >
            <article
              v-for="sender in senders"
              :key="sender.senderId"
              class="mobile-record"
            >
              <div class="mobile-record__header">
                <strong class="mobile-record__title">{{ sender.name }}</strong>
                <a-tag :color="statusColor(sender.status)">
                  {{ t(sender.status === "ACTIVE" ? "common.active" : "common.disabled") }}
                </a-tag>
              </div>
              <div class="mobile-record__meta">
                <span>{{ t("mailSender.server") }}</span><strong>{{ sender.host }}:{{ sender.port }}</strong>
                <span>{{ t("mailSender.security") }}</span><strong>{{ t(`mailSender.securityOptions.${sender.security}`) }}</strong>
                <span>{{ t("mailSender.fromAddress") }}</span><strong>{{ sender.fromAddress }}</strong>
              </div>
              <div class="mobile-record__actions">
                <RowActionButton
                  v-if="canManage"
                  :label="t('mailSender.testConnection')"
                  @click="testConnection(sender)"
                >
                  <PlugZap :size="17" />
                </RowActionButton>
                <RowActionButton
                  v-if="canManage"
                  :label="t(sender.status === 'ACTIVE' ? 'mailSender.disable' : 'mailSender.enable')"
                  @click="changeStatus(sender)"
                >
                  <Power :size="17" />
                </RowActionButton>
                <RowActionButton
                  :label="t(canManage ? 'common.edit' : 'common.details')"
                  @click="openEdit(sender)"
                >
                  <Pencil :size="17" />
                </RowActionButton>
                <RowActionButton
                  v-if="canManage"
                  danger
                  :disabled="sender.status === 'ACTIVE'"
                  :label="t('common.delete')"
                  @click="remove(sender)"
                >
                  <Trash2 :size="17" />
                </RowActionButton>
              </div>
            </article>
          </div>
        </a-spin>
      </div>
    </section>

    <a-drawer
      v-model:open="editorOpen"
      :title="editorTitle"
      :width="680"
      class="responsive-drawer"
      @close="closeEditor"
    >
      <a-form
        ref="formRef"
        :model="form"
        layout="vertical"
        :scroll-to-first-error="true"
      >
        <div class="sender-form-grid">
          <a-form-item
            name="name"
            :label="t('mailSender.name')"
            :rules="[{ required: true, message: t('mailSender.nameRequired') }]"
          >
            <a-input
              v-model:value="form.name"
              :maxlength="128"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
          <a-form-item
            name="security"
            :label="t('mailSender.security')"
            required
          >
            <a-select
              v-model:value="form.security"
              :options="securityOptions"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
          <a-form-item
            name="host"
            :label="t('mailSender.host')"
            :rules="[{ required: true, message: t('mailSender.hostRequired') }]"
          >
            <a-input
              v-model:value="form.host"
              :maxlength="255"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
          <a-form-item
            name="port"
            :label="t('mailSender.port')"
            :rules="[{ required: true, type: 'number', min: 1, max: 65535 }]"
          >
            <a-input-number
              v-model:value="form.port"
              :min="1"
              :max="65535"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
        </div>
        <a-divider>{{ t("mailSender.authentication") }}</a-divider>
        <a-form-item :label="t('mailSender.authentication')">
          <a-switch
            :checked="form.authenticated"
            :disabled="submitting || !canManage"
            @update:checked="changeAuthentication"
          />
          <span class="switch-label">{{ t(form.authenticated ? "mailSender.credentials" : "mailSender.anonymous") }}</span>
        </a-form-item>
        <div
          v-if="form.authenticated"
          class="sender-form-grid"
        >
          <a-form-item
            name="username"
            :label="t('mailSender.username')"
            :rules="[{ required: true, message: t('mailSender.usernameRequired') }]"
          >
            <a-input
              v-model:value="form.username"
              autocomplete="username"
              :maxlength="255"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
          <a-form-item
            name="password"
            :label="t('mailSender.password')"
            :rules="[{ required: !editing?.passwordConfigured, message: t('mailSender.passwordRequired') }]"
          >
            <a-input-password
              v-model:value="form.password"
              autocomplete="new-password"
              :maxlength="1024"
              :placeholder="editing?.passwordConfigured ? t('mailSender.passwordPreserved') : ''"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
        </div>
        <a-divider>{{ t("mailSender.messageDefaults") }}</a-divider>
        <div class="sender-form-grid">
          <a-form-item
            name="fromAddress"
            :label="t('mailSender.fromAddress')"
            :rules="[{ required: true, type: 'email', message: t('mailSender.emailInvalid') }]"
          >
            <a-input
              v-model:value="form.fromAddress"
              :maxlength="255"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
          <a-form-item
            name="fromName"
            :label="t('mailSender.fromName')"
          >
            <a-input
              v-model:value="form.fromName"
              :maxlength="128"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
          <a-form-item
            name="replyTo"
            :label="t('mailSender.replyTo')"
            :rules="[{ type: 'email', message: t('mailSender.emailInvalid') }]"
          >
            <a-input
              v-model:value="form.replyTo"
              :maxlength="255"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
        </div>
      </a-form>
      <template
        v-if="canManage"
        #extra
      >
        <a-space>
          <a-button @click="closeEditor">
            {{ t("common.cancel") }}
          </a-button>
          <a-button
            type="primary"
            :loading="submitting"
            @click="save"
          >
            {{ t(editing ? "common.save" : "common.create") }}
          </a-button>
        </a-space>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.mail-sender-panel { overflow: hidden; }
.sender-name { display: flex; min-width: 0; align-items: center; gap: 10px; }
.sender-name > span { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; color: var(--app-primary); background: var(--app-primary-soft); border-radius: var(--app-radius); }
.sender-name > div { display: grid; min-width: 0; gap: 2px; }
.sender-name strong, .sender-name small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sender-name small { color: var(--app-text-tertiary); }
.sender-form-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 0 14px; }
.sender-form-grid :deep(.ant-input-number) { width: 100%; }
.switch-label { margin-left: 10px; }
@media (max-width: 640px) {
  .sender-form-grid { grid-template-columns: 1fr; gap: 0; }
}
</style>
