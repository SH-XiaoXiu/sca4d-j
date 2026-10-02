<script setup lang="ts">
import type {
  EmailTemplate,
  EmailTemplateSource,
  Int64String,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { computed, onScopeDispose, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { message } from 'ant-design-vue';
import { FileText, Plus, Trash2 } from 'lucide-vue-next';

import { SjResourceEditor, SjTabs } from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { businessApplication } from '#/platform/business-application';
import {
  resourceQueryId,
  withoutResourceQuery,
} from '#/platform/resource-reference-routes';

/** 直接编辑后端保存的 Email 源码，完整替换语言集合，不经富文本转换。 */
const emit = defineEmits<{
  /** 请求服务端预览当前未保存草稿。 */ preview: [
    input: {
      locales: Readonly<Record<string, EmailTemplateSource>>;
      variables: readonly string[];
    },
  ];
}>();
const templates = useSjControllerState(businessApplication.emailTemplates);
const session = useSjAccess();
const { errorText } = useSjPresentation();
const route = useRoute();
const router = useRouter();
const create = ref(true);
const loading = ref(false);
const saving = ref(false);
const visible = ref(false);
const detail = ref<EmailTemplate | null>(null);
const activeLocale = ref('');
const addedLocale = ref<string>();
const formRef = ref<FormInstance>();
const form = reactive({
  notificationCode: '',
  locales: {} as Record<
    string,
    { html: string; subject: string; text: string }
  >,
});
const baseline = ref('');
const dirty = computed(
  () => Boolean(baseline.value) && JSON.stringify(form) !== baseline.value,
);
const canManage = computed(() => session.value.capabilities.emailManage);
const defaultLocale = computed(
  () => templates.value.localeCatalog?.defaultLocale ?? '',
);
const suggestions = computed(() =>
  (templates.value.localeCatalog?.supportedLocales ?? [])
    .filter((locale) => !(locale in form.locales))
    .map((value) => ({ label: value, value })),
);
let generation = 0;
onScopeDispose(() => {
  generation++;
});
/** 按已有资源或深链接读取源码与强 ETag，创建只使用服务端语言目录。 */
async function open(template: EmailTemplate | Int64String | null = null) {
  const read = ++generation;
  const id = typeof template === 'string' ? template : template?.templateId;
  create.value = !id;
  detail.value = null;
  visible.value = true;
  loading.value = true;
  baseline.value = '';
  try {
    if (!templates.value.localeCatalog)
      await businessApplication.emailTemplates.load();
    const current = id
      ? await businessApplication.emailTemplates.require(id)
      : null;
    if (read !== generation) return;
    detail.value = current;
    form.notificationCode = current?.notificationCode ?? '';
    form.locales = current
      ? structuredClone(current.locales)
      : { [defaultLocale.value]: { subject: '', text: '', html: '' } };
    activeLocale.value =
      defaultLocale.value in form.locales
        ? defaultLocale.value
        : (Object.keys(form.locales)[0] ?? '');
    baseline.value = JSON.stringify(form);
    formRef.value?.clearValidate();
  } catch (error) {
    if (read === generation) message.error(errorText(error));
  } finally {
    if (read === generation) loading.value = false;
  }
}
/** 添加目录允许且尚未提供的语言，不预填其他语言正文。 */
function addLocale() {
  const locale = addedLocale.value;
  if (!locale || locale in form.locales) return;
  form.locales[locale] = { subject: '', text: '', html: '' };
  activeLocale.value = locale;
  addedLocale.value = undefined;
}
/** 默认语言始终保留，其他语言明确移除整个源码项。 */
function removeLocale(locale: string) {
  if (locale === defaultLocale.value) return;
  delete form.locales[locale];
  activeLocale.value = Object.keys(form.locales)[0] ?? '';
}
/** 提交三个原始源码字段，保存失败保留完整输入。 */
async function save() {
  if (saving.value || loading.value || !canManage.value) return;
  await formRef.value?.validate();
  saving.value = true;
  try {
    const input = {
      notificationCode: form.notificationCode.trim(),
      locales: structuredClone(form.locales),
    };
    await (create.value
      ? businessApplication.emailTemplates.create(input)
      : businessApplication.emailTemplates.replace(input));
    baseline.value = JSON.stringify(form);
    visible.value = false;
    message.success(t('saved'));
  } catch (error) {
    message.error(errorText(error));
  } finally {
    saving.value = false;
  }
}
/** 关闭后取消该详情的旧读取，并清除已经消费的链接参数。 */
async function closed() {
  generation++;
  businessApplication.emailTemplates.clearDetail();
  if (resourceQueryId(route.query, 'templateId'))
    await router.replace({
      query: withoutResourceQuery(route.query, 'templateId'),
    });
}
/** 与其余通知配置使用同一 Locale namespace。 */
function t(key: string) {
  return $t(`page.emailNotifications.${key}`);
}
/** 提供完整源码副本，预览不会修改当前编辑草稿。 */
function previewDraft() {
  emit('preview', {
    locales: structuredClone(form.locales),
    variables: detail.value?.variables ?? [],
  });
}
defineExpose({ open });

import { SjSelect } from '#/components/sj';
const sjEmptyText = computed(() => $t('sj.noData'));
</script>
<template>
  <SjResourceEditor
    :read-only="!canManage"
    v-model:open="visible"
    :create="create"
    size="xl"
    :title="create ? t('createTemplate') : t('templateDetails')"
    :loading="loading"
    :saving="saving"
    :dirty="dirty"
    :submit-disabled="!canManage"
    @submit="save"
    @closed="closed"
  >
    <a-form
      ref="formRef"
      layout="vertical"
      :model="form"
      :disabled="!canManage || loading"
    >
      <a-form-item
        name="notificationCode"
        :label="t('notificationCode')"
        :rules="[
          { required: true, whitespace: true, message: t('required') },
          { max: 128 },
        ]"
      >
        <a-input v-model:value="form.notificationCode" :maxlength="128" />
      </a-form-item>
      <div class="mb-4 flex flex-wrap items-center gap-2">
        <SjSelect
          :empty-text="sjEmptyText"
          v-model:value="addedLocale"
          class="min-w-48"
          :options="suggestions"
          :aria-label="t('locale')"
        />
        <a-button :disabled="!addedLocale" @click="addLocale">
          <Plus :size="17" aria-hidden="true" />
          {{ t('addLocale') }}
        </a-button>
        <a-button v-if="canManage" @click="previewDraft">
          <FileText :size="17" aria-hidden="true" />
          {{ t('preview') }}
        </a-button>
      </div>
      <SjTabs v-model:active-key="activeLocale" variant="section">
        <a-tab-pane
          v-for="(source, locale) in form.locales"
          :key="locale"
          :tab="
            locale === defaultLocale
              ? `${locale} · ${$t('sj.defaultLocale')}`
              : locale
          "
        >
          <a-button
            v-if="locale !== defaultLocale"
            class="mb-4"
            danger
            :aria-label="`${t('removeLocale')}: ${locale}`"
            @click="removeLocale(locale)"
          >
            <Trash2 :size="17" aria-hidden="true" />
            {{ t('removeLocale') }}
          </a-button>
          <a-form-item
            :name="['locales', locale, 'subject']"
            :label="t('subjectSource')"
            :rules="[{ max: 998 }]"
          >
            <a-input v-model:value="source.subject" :maxlength="998" />
          </a-form-item>
          <a-form-item
            :name="['locales', locale, 'text']"
            :label="t('textBody')"
          >
            <a-textarea
              v-model:value="source.text"
              :auto-size="{ minRows: 4, maxRows: 12 }"
              :maxlength="1000000"
            />
          </a-form-item>
          <a-form-item
            :name="['locales', locale, 'html']"
            :label="t('htmlBody')"
          >
            <a-textarea
              v-model:value="source.html"
              class="font-mono"
              :auto-size="{ minRows: 6, maxRows: 16 }"
              :maxlength="1000000"
            />
          </a-form-item>
        </a-tab-pane>
      </SjTabs>
    </a-form>
  </SjResourceEditor>
</template>
