<script setup lang="ts">
import type { EmailTemplateSource, RenderedEmail } from '@sca4d/admin';

import { computed, onScopeDispose, ref } from 'vue';

import { message } from 'ant-design-vue';
import { Plus, Trash2 } from 'lucide-vue-next';

import { SjResourceEditor, SjTabs } from '#/components/sj';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { businessApplication } from '#/platform/business-application';
/** 使用未保存源码和变量请求服务端渲染，预览不会写入模板。 */
const language = ref('');
const loading = ref(false);
const visible = ref(false);
const locales = ref<Readonly<Record<string, EmailTemplateSource>>>({});
const variables = ref<{ key: string; value: string }[]>([]);
const result = ref<RenderedEmail>();
const options = computed(() =>
  Object.keys(locales.value).map((value) => ({ label: value, value })),
);
const { errorText } = useSjPresentation();
let generation = 0;
onScopeDispose(() => {
  generation++;
});
/** 接收完整源码快照，不持有编辑器的可变草稿。 */
function open(input: {
  locales: Readonly<Record<string, EmailTemplateSource>>;
  variables: readonly string[];
}) {
  generation++;
  locales.value = structuredClone(input.locales);
  language.value = Object.keys(input.locales)[0] ?? '';
  variables.value = input.variables.map((key) => ({ key, value: '' }));
  result.value = undefined;
  visible.value = true;
}
/** 只接收当前草稿的服务端安全渲染结果。 */
async function preview() {
  if (loading.value) return;
  const source = locales.value[language.value];
  if (!source) return;
  const values: Record<string, string> = {};
  for (const row of variables.value) {
    const key = row.key.trim();
    if (!key || key in values || row.value.length > 4096) {
      message.error(t('invalidVariable'));
      return;
    }
    values[key] = row.value;
  }
  const read = ++generation;
  loading.value = true;
  try {
    const rendered = await businessApplication.emailTemplates.preview({
      source,
      languageTag: language.value,
      variables: values,
    });
    if (read === generation) result.value = rendered;
  } catch (error) {
    if (read === generation) message.error(errorText(error));
  } finally {
    if (read === generation) loading.value = false;
  }
}
/** 使用现有邮件通知文案。 */
function t(key: string) {
  return $t(`page.emailNotifications.${key}`);
}
defineExpose({ open });
</script>
<template>
  <SjResourceEditor
    v-model:open="visible"
    read-only
    size="xl"
    :title="t('preview')"
    @closed="
      generation++;
      loading = false;
    "
  >
    <a-form layout="vertical">
      <a-form-item :label="t('locale')">
        <a-select v-model:value="language" :options="options" />
      </a-form-item>
      <div
        v-for="(row, index) in variables"
        :key="index"
        class="grid grid-cols-[1fr_1fr_40px] gap-2"
      >
        <a-form-item :label="t('variable')">
          <a-input v-model:value="row.key" />
        </a-form-item>
        <a-form-item :label="t('value')">
          <a-input v-model:value="row.value" :maxlength="4096" />
        </a-form-item>
        <a-button
          class="mt-7"
          danger
          type="text"
          :aria-label="t('deleteVariable')"
          @click="variables.splice(index, 1)"
        >
          <Trash2 :size="17" aria-hidden="true" />
        </a-button>
      </div>
      <div class="flex gap-2">
        <a-button @click="variables.push({ key: '', value: '' })">
          <Plus :size="17" aria-hidden="true" />
          {{ t('addVariable') }}
        </a-button>
        <a-button type="primary" :loading="loading" @click="preview">
          {{ t('generatePreview') }}
        </a-button>
      </div>
    </a-form>
    <SjTabs v-if="result" variant="section">
      <a-tab-pane key="text" :tab="t('textBody')">
        <h3 class="my-4 text-sm font-semibold">{{ result.subject }}</h3>
        <pre class="whitespace-pre-wrap break-words text-sm">{{
          result.text
        }}</pre>
      </a-tab-pane>
      <a-tab-pane v-if="result.html" key="html" :tab="t('htmlBody')">
        <iframe
          class="h-96 w-full border border-border"
          sandbox=""
          :srcdoc="result.html"
          :title="t('preview')"
        ></iframe>
      </a-tab-pane>
    </SjTabs>
  </SjResourceEditor>
</template>
