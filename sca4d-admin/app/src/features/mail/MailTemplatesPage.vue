<script setup lang="ts">
defineOptions({ name: "MailTemplatesPage" });

import {
  createIdempotencyKey,
  isApiError,
  type Id,
  type MailSenderView,
  type MailTemplateStatus,
  type MailTemplateVariables,
  type MailTemplateView,
  type RenderedMailView
} from "@sca4d/admin-common";
import { Eye, FileText, Pencil, Plus, Power, RotateCcw, Send, Trash2 } from "lucide-vue-next";
import { message, Modal, type FormInstance } from "ant-design-vue";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute, useRouter } from "vue-router";
import { mailSenderApi, mailTemplateApi } from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const auth = useSessionStore();
const { t } = useI18n();
const loading = ref(false);
const submitting = ref(false);
const previewing = ref(false);
const sending = ref(false);
const editorOpen = ref(false);
const previewOpen = ref(false);
const testOpen = ref(false);
const formRef = ref<FormInstance>();
const testFormRef = ref<FormInstance>();
const editing = ref<MailTemplateView | null>(null);
const testing = ref<MailTemplateView | null>(null);
const templates = ref<MailTemplateView[]>([]);
const senders = ref<MailSenderView[]>([]);
const preview = ref<RenderedMailView | null>(null);
const previewMode = ref<"html" | "text">("html");
const createKey = ref<string | null>(null);
const variableValues = reactive<MailTemplateVariables>({});
const form = reactive({
  family: "",
  locale: "zh-CN",
  subjectTemplate: "",
  textTemplate: "",
  htmlTemplate: ""
});
const testForm = reactive({
  senderId: "" as Id,
  recipient: ""
});

const canManage = computed(() => auth.hasPermission("system:mail-templates:manage"));
const editorTitle = computed(() => editing.value
  ? t(canManage.value ? "mailTemplate.edit" : "mailTemplate.details")
  : t("mailTemplate.create"));
const columns = computed(() => [
  { title: t("mailTemplate.template"), key: "template", width: 300 },
  { title: t("mailTemplate.locale"), dataIndex: "locale", key: "locale", width: 140 },
  { title: t("mailTemplate.subject"), dataIndex: "subjectTemplate", key: "subject", width: 340 },
  { title: t("mailTemplate.variables"), key: "variables", width: 210 },
  { title: t("common.status"), key: "status", width: 110 },
  { title: t("common.actions"), key: "actions", width: 210, fixed: "right" as const }
]);
const draftVariableNames = computed(() => {
  const names: string[] = [];
  const seen = new Set<string>();
  const expression = /{{\s*([a-z][a-zA-Z0-9_]{0,63})\s*}}/g;
  for (const content of [form.subjectTemplate, form.textTemplate, form.htmlTemplate]) {
    for (const match of content.matchAll(expression)) {
      const name = match[1];
      if (name && !seen.has(name)) {
        seen.add(name);
        names.push(name);
      }
    }
  }
  return names;
});
const activeSenderOptions = computed(() => senders.value
  .filter((sender) => sender.status === "ACTIVE")
  .map((sender) => ({ value: sender.senderId, label: `${sender.name} · ${sender.fromAddress}` })));

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function statusColor(status: MailTemplateStatus): string {
  return status === "ACTIVE" ? "green" : "default";
}

function findTemplate(templateId: Id): MailTemplateView | null {
  return templates.value.find((template) => template.templateId === templateId) ?? null;
}

function resetVariables(names: string[]): void {
  const existing = { ...variableValues };
  for (const key of Object.keys(variableValues)) delete variableValues[key];
  for (const name of names) variableValues[name] = existing[name] ?? "";
}

function writeInput() {
  return {
    family: form.family.trim(),
    locale: form.locale.trim(),
    subjectTemplate: form.subjectTemplate,
    textTemplate: form.textTemplate,
    ...(form.htmlTemplate.trim() ? { htmlTemplate: form.htmlTemplate } : {})
  };
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    templates.value = await mailTemplateApi.templates();
  } catch (error) {
    message.error(errorMessage(error, "mailTemplate.loadFailed"));
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editing.value = null;
  createKey.value = createIdempotencyKey();
  Object.assign(form, {
    family: "",
    locale: "zh-CN",
    subjectTemplate: "",
    textTemplate: "",
    htmlTemplate: ""
  });
  resetVariables([]);
  editorOpen.value = true;
}

function openEdit(template: MailTemplateView): void {
  editing.value = template;
  createKey.value = null;
  Object.assign(form, {
    family: template.family,
    locale: template.locale,
    subjectTemplate: template.subjectTemplate,
    textTemplate: template.textTemplate,
    htmlTemplate: template.htmlTemplate ?? ""
  });
  resetVariables(template.variableNames);
  editorOpen.value = true;
  if (String(route.params.templateId ?? "") !== template.templateId) {
    void router.push(`/system/mail-templates/${template.templateId}`);
  }
}

function closeEditor(): void {
  editorOpen.value = false;
  editing.value = null;
  createKey.value = null;
  if (route.params.templateId) void router.push("/system/mail-templates");
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
    if (editing.value) {
      await mailTemplateApi.update(editing.value.templateId, writeInput(), editing.value.version);
      message.success(t("mailTemplate.updateSuccess"));
    } else {
      createKey.value ??= createIdempotencyKey();
      await mailTemplateApi.create(writeInput(), createKey.value);
      message.success(t("mailTemplate.createSuccess"));
    }
    closeEditor();
    await load();
  } catch (error) {
    if (isApiError(error) && (error.httpStatus === 409 || error.httpStatus === 412)) {
      const draft = { ...form };
      const values = { ...variableValues };
      const templateId = editing.value?.templateId;
      await load();
      if (templateId) {
        const latest = findTemplate(templateId);
        if (!latest) {
          closeEditor();
          message.error(t("mailTemplate.notFound"));
          return;
        }
        editing.value = latest;
      }
      Object.assign(form, draft);
      resetVariables(Object.keys(values));
      Object.assign(variableValues, values);
      createKey.value = editing.value ? null : createIdempotencyKey();
      message.warning(t("mailTemplate.conflict"));
      return;
    }
    message.error(errorMessage(error, "mailTemplate.saveFailed"));
  } finally {
    submitting.value = false;
  }
}

async function showPreview(): Promise<void> {
  if (!canManage.value) return;
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  resetVariables(draftVariableNames.value);
  previewing.value = true;
  try {
    preview.value = await mailTemplateApi.preview(writeInput(), { ...variableValues });
    previewMode.value = preview.value.html ? "html" : "text";
    previewOpen.value = true;
  } catch (error) {
    message.error(errorMessage(error, "mailTemplate.previewFailed"));
  } finally {
    previewing.value = false;
  }
}

async function openTest(template: MailTemplateView): Promise<void> {
  testing.value = template;
  testForm.senderId = "";
  testForm.recipient = "";
  resetVariables(template.variableNames);
  try {
    senders.value = await mailSenderApi.senders();
    testOpen.value = true;
  } catch (error) {
    message.error(errorMessage(error, "mailTemplate.sendersFailed"));
  }
}

async function sendTest(): Promise<void> {
  if (!testing.value) return;
  try {
    await testFormRef.value?.validate();
  } catch {
    return;
  }
  if (testing.value.variableNames.some((name) => !variableValues[name])) {
    message.error(t("mailTemplate.variableRequired"));
    return;
  }
  sending.value = true;
  try {
    await mailTemplateApi.sendTest(
      testing.value.templateId,
      testForm.senderId,
      testForm.recipient.trim(),
      { ...variableValues }
    );
    message.success(t("mailTemplate.testSuccess"));
    testOpen.value = false;
  } catch (error) {
    message.error(errorMessage(error, "mailTemplate.testFailed"));
  } finally {
    sending.value = false;
  }
}

function changeStatus(template: MailTemplateView): void {
  const next: MailTemplateStatus = template.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  Modal.confirm({
    title: t(next === "ACTIVE" ? "mailTemplate.enableTitle" : "mailTemplate.disableTitle", { family: template.family, locale: template.locale }),
    content: t(next === "ACTIVE" ? "mailTemplate.enableWarning" : "mailTemplate.disableWarning"),
    okText: t(next === "ACTIVE" ? "common.active" : "common.disabled"),
    cancelText: t("common.cancel"),
    onOk: async () => {
      try {
        await mailTemplateApi.changeStatus(template.templateId, next, template.version);
        message.success(t("mailTemplate.statusSuccess"));
        await load();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 412) await load();
        message.error(errorMessage(error, "mailTemplate.statusFailed"));
      }
    }
  });
}

function remove(template: MailTemplateView): void {
  Modal.confirm({
    title: t("mailTemplate.deleteTitle", { family: template.family, locale: template.locale }),
    content: t("mailTemplate.deleteWarning"),
    okText: t("common.delete"),
    cancelText: t("common.cancel"),
    okType: "danger",
    onOk: async () => {
      try {
        await mailTemplateApi.delete(template.templateId, template.version);
        message.success(t("mailTemplate.deleteSuccess"));
        if (String(route.params.templateId ?? "") === template.templateId) closeEditor();
        await load();
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 412) await load();
        message.error(errorMessage(error, "mailTemplate.deleteFailed"));
      }
    }
  });
}

async function openRouteTemplate(templateId: string): Promise<void> {
  if (!templateId) return;
  const template = findTemplate(templateId);
  if (!template) {
    message.error(t("mailTemplate.notFound"));
    await router.replace("/system/mail-templates");
    return;
  }
  openEdit(template);
}

watch(draftVariableNames, (names) => resetVariables(names));
onMounted(async () => {
  await load();
  await openRouteTemplate(String(route.params.templateId ?? ""));
});
watch(
  () => route.params.templateId,
  (templateId) => {
    if (templateId) void openRouteTemplate(String(templateId));
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
      :title="t('route.mailTemplates')"
      :description="t('mailTemplate.description')"
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
          <Plus :size="16" />{{ t("mailTemplate.create") }}
        </a-button>
      </template>
    </PageHeader>

    <section class="page-panel management-panel mail-template-panel">
      <a-table
        class="desktop-only"
        row-key="templateId"
        :columns="columns"
        :data-source="templates"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1190 }"
      >
        <template #emptyText>
          {{ t("mailTemplate.empty") }}
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'template'">
            <div class="template-name">
              <span><FileText :size="18" /></span><strong>{{ record.family }}</strong>
            </div>
          </template>
          <template v-else-if="column.key === 'variables'">
            <a-space
              v-if="record.variableNames.length"
              :size="[4, 4]"
              wrap
            >
              <a-tag
                v-for="name in record.variableNames"
                :key="name"
              >
                {{ name }}
              </a-tag>
            </a-space>
            <span v-else>—</span>
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
                :label="t('mailTemplate.testSend')"
                @click="openTest(record)"
              >
                <Send :size="17" />
              </RowActionButton>
              <RowActionButton
                v-if="canManage"
                :label="t(record.status === 'ACTIVE' ? 'mailTemplate.disable' : 'mailTemplate.enable')"
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
            v-if="!loading && templates.length === 0"
            :description="t('mailTemplate.empty')"
          />
          <div
            v-else
            class="mobile-card-list"
          >
            <article
              v-for="template in templates"
              :key="template.templateId"
              class="mobile-record"
            >
              <div class="mobile-record__header">
                <strong class="mobile-record__title">{{ template.family }}</strong><a-tag :color="statusColor(template.status)">
                  {{ t(template.status === "ACTIVE" ? "common.active" : "common.disabled") }}
                </a-tag>
              </div>
              <div class="mobile-record__meta">
                <span>{{ t("mailTemplate.locale") }}</span><strong>{{ template.locale }}</strong>
                <span>{{ t("mailTemplate.subject") }}</span><strong>{{ template.subjectTemplate }}</strong>
              </div>
              <div class="mobile-record__actions">
                <RowActionButton
                  v-if="canManage"
                  :label="t('mailTemplate.testSend')"
                  @click="openTest(template)"
                >
                  <Send :size="17" />
                </RowActionButton>
                <RowActionButton
                  v-if="canManage"
                  :label="t(template.status === 'ACTIVE' ? 'mailTemplate.disable' : 'mailTemplate.enable')"
                  @click="changeStatus(template)"
                >
                  <Power :size="17" />
                </RowActionButton>
                <RowActionButton
                  :label="t(canManage ? 'common.edit' : 'common.details')"
                  @click="openEdit(template)"
                >
                  <Pencil :size="17" />
                </RowActionButton>
                <RowActionButton
                  v-if="canManage"
                  danger
                  :disabled="template.status === 'ACTIVE'"
                  :label="t('common.delete')"
                  @click="remove(template)"
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
      :width="760"
      class="responsive-drawer"
      @close="closeEditor"
    >
      <a-form
        ref="formRef"
        :model="form"
        layout="vertical"
        :scroll-to-first-error="true"
      >
        <div class="template-form-grid">
          <a-form-item
            name="family"
            :label="t('mailTemplate.family')"
            :rules="[{ required: true, message: t('mailTemplate.familyRequired') }]"
          >
            <a-input
              v-model:value="form.family"
              :maxlength="64"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
          <a-form-item
            name="locale"
            :label="t('mailTemplate.locale')"
            :rules="[{ required: true, message: t('mailTemplate.localeRequired') }]"
          >
            <a-input
              v-model:value="form.locale"
              :maxlength="35"
              :disabled="submitting || !canManage"
            />
          </a-form-item>
        </div>
        <a-form-item
          name="subjectTemplate"
          :label="t('mailTemplate.subject')"
          :rules="[{ required: true, message: t('mailTemplate.subjectRequired') }]"
        >
          <a-input
            v-model:value="form.subjectTemplate"
            :maxlength="512"
            :disabled="submitting || !canManage"
          />
        </a-form-item>
        <a-form-item
          name="textTemplate"
          :label="t('mailTemplate.text')"
          :rules="[{ required: true, message: t('mailTemplate.textRequired') }]"
        >
          <a-textarea
            v-model:value="form.textTemplate"
            :rows="8"
            :maxlength="100000"
            :disabled="submitting || !canManage"
          />
        </a-form-item>
        <a-form-item
          name="htmlTemplate"
          :label="t('mailTemplate.html')"
        >
          <a-textarea
            v-model:value="form.htmlTemplate"
            :rows="10"
            :maxlength="100000"
            :disabled="submitting || !canManage"
            class="code-input"
          />
        </a-form-item>
        <a-divider v-if="draftVariableNames.length">
          {{ t("mailTemplate.variables") }}
        </a-divider>
        <div class="template-form-grid">
          <a-form-item
            v-for="name in draftVariableNames"
            :key="name"
            :label="name"
          >
            <a-input
              v-model:value="variableValues[name]"
              :maxlength="10000"
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
          <a-button
            :loading="previewing"
            @click="showPreview"
          >
            <Eye :size="16" />{{ t("mailTemplate.preview") }}
          </a-button>
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

    <a-modal
      v-model:open="previewOpen"
      :title="preview?.subject ?? t('mailTemplate.preview')"
      :footer="null"
      width="760px"
    >
      <a-segmented
        v-model:value="previewMode"
        :options="[{ label: t('mailTemplate.html'), value: 'html', disabled: !preview?.html }, { label: t('mailTemplate.text'), value: 'text' }]"
      />
      <iframe
        v-if="previewMode === 'html' && preview?.html"
        class="mail-preview"
        sandbox=""
        :title="t('mailTemplate.preview')"
        :srcdoc="preview.html"
      />
      <pre
        v-else
        class="text-preview"
      >{{ preview?.text }}</pre>
    </a-modal>

    <a-modal
      v-model:open="testOpen"
      :title="t('mailTemplate.testSend')"
      :confirm-loading="sending"
      :ok-text="t('mailTemplate.send')"
      :cancel-text="t('common.cancel')"
      @ok="sendTest"
    >
      <a-form
        ref="testFormRef"
        :model="testForm"
        layout="vertical"
      >
        <a-form-item
          name="senderId"
          :label="t('mailTemplate.sender')"
          :rules="[{ required: true, message: t('mailTemplate.senderRequired') }]"
        >
          <a-select
            v-model:value="testForm.senderId"
            :options="activeSenderOptions"
          />
        </a-form-item>
        <a-form-item
          name="recipient"
          :label="t('mailTemplate.recipient')"
          :rules="[{ required: true, type: 'email', message: t('mailTemplate.recipientRequired') }]"
        >
          <a-input
            v-model:value="testForm.recipient"
            :maxlength="255"
          />
        </a-form-item>
        <div class="template-form-grid">
          <a-form-item
            v-for="name in testing?.variableNames ?? []"
            :key="name"
            :label="name"
            :rules="[{ required: true, message: t('mailTemplate.variableRequired') }]"
          >
            <a-input
              v-model:value="variableValues[name]"
              :maxlength="10000"
            />
          </a-form-item>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.mail-template-panel { overflow: hidden; }
.template-name { display: flex; min-width: 0; align-items: center; gap: 10px; }
.template-name > span { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; color: var(--app-primary); background: var(--app-primary-soft); border-radius: var(--app-radius); }
.template-name strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.template-form-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 0 14px; }
.code-input :deep(textarea) { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
.mail-preview { width: 100%; height: 420px; margin-top: 16px; border: 1px solid var(--app-border); border-radius: var(--app-radius); background: #fff; }
.text-preview { min-height: 220px; max-height: 420px; margin: 16px 0 0; padding: 16px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--app-text); background: var(--app-surface-subtle); border: 1px solid var(--app-border); border-radius: var(--app-radius); }
@media (max-width: 640px) {
  .template-form-grid { grid-template-columns: 1fr; gap: 0; }
  .mail-preview { height: 340px; }
}
</style>
