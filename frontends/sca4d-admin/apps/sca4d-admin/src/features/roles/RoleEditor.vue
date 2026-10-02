<script setup lang="ts">
import type { LocalizedFields, PlatformRole } from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { computed, onScopeDispose, reactive, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import { SjLocaleTabs, SjResourceEditor } from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

/** 创建角色或完整替换名称翻译，草稿与读取的版本只属于这次编辑。 */
const props = defineProps<{
  /** 编辑对象，创建时为空。 */ role: null | PlatformRole;
}>();

const emit = defineEmits<{ /** 工作区已关闭。 */ closed: [] }>();

const { t, errorText } = useSjPresentation();

const open = defineModel<boolean>('open', { required: true });
const roles = useSjControllerState(businessApplication.roles);
const session = useSjAccess();
const formRef = ref<FormInstance>();
const form = reactive({ code: '', names: {} as Record<string, string> });
const baseline = ref('');
const loading = ref(false);
const saving = ref(false);
const canManage = computed(() => session.value.capabilities.rolesManage);
const locales = computed(() => [
  ...new Set([
    ...(roles.value.localeCatalog?.supportedLocales ?? []),
    ...Object.keys(form.names),
  ]),
]);
const defaultLocale = computed(
  () => roles.value.localeCatalog?.defaultLocale ?? '',
);
const dirty = computed(
  () => Boolean(baseline.value) && JSON.stringify(form) !== baseline.value,
);
let generation = 0;
onScopeDispose(() => {
  generation++;
});
watch(
  () => [open.value, props.role?.id] as const,
  async ([visible]) => {
    const read = ++generation;
    if (!visible) return;
    baseline.value = '';
    form.code = props.role?.code ?? '';
    form.names = {};
    loading.value = true;
    try {
      await (props.role
        ? businessApplication.roles.loadI18n(props.role.id)
        : businessApplication.roles.loadLocales());
      if (read !== generation) return;
      const stored = props.role ? (roles.value.i18n?.i18n ?? {}) : {};
      form.names = Object.fromEntries(
        [
          ...new Set([
            ...(roles.value.localeCatalog?.supportedLocales ?? []),
            ...Object.keys(stored),
          ]),
        ].map((locale) => [locale, stored[locale]?.name ?? '']),
      );
      baseline.value = JSON.stringify(form);
      formRef.value?.clearValidate();
    } catch (error) {
      if (read === generation) message.error(errorText(error));
    } finally {
      if (read === generation) loading.value = false;
    }
  },
);
/** 默认语言必填，其余已保存语言原样进入完整替换请求。 */
async function save() {
  if (saving.value || loading.value || !canManage.value) return;
  await formRef.value?.validate();
  saving.value = true;
  const i18n: LocalizedFields = Object.fromEntries(
    Object.entries(form.names).flatMap(([locale, value]) =>
      value.trim() ? [[locale, { name: value.trim() }]] : [],
    ),
  );
  try {
    await (props.role
      ? businessApplication.roles.replaceI18n(i18n)
      : businessApplication.roles.create(form.code.trim(), i18n));
    baseline.value = JSON.stringify(form);
    open.value = false;
    message.success(t(props.role ? 'updateSuccess' : 'createSuccess'));
  } catch (error) {
    message.error(errorText(error));
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <SjResourceEditor
    v-model:open="open"
    :create="!role"
    :title="role ? t('editRoleNames') : t('createRole')"
    size="xl"
    :loading="loading"
    :saving="saving"
    :dirty="dirty"
    :submit-disabled="!canManage"
    @submit="save"
    @closed="emit('closed')"
  >
    <a-form
      ref="formRef"
      layout="vertical"
      :model="form"
      :disabled="!canManage || loading"
    >
      <a-form-item
        name="code"
        :label="t('roleCode')"
        :rules="[{ required: true, whitespace: true, message: t('required') }]"
      >
        <a-input
          v-model:value="form.code"
          :disabled="Boolean(role)"
          :maxlength="128"
        />
      </a-form-item>
      <SjLocaleTabs :locales="locales" :default-locale="defaultLocale">
        <template #default="{ locale }">
          <a-form-item
            :name="['names', locale]"
            :label="t('roleNameForLocale', { locale })"
            :rules="[
              {
                required: locale === defaultLocale,
                whitespace: locale === defaultLocale,
                message: t('required'),
              },
              { max: 128, message: t('roleNameLength') },
            ]"
          >
            <a-input v-model:value="form.names[locale]" :maxlength="128" />
          </a-form-item>
        </template>
      </SjLocaleTabs>
    </a-form>
  </SjResourceEditor>
</template>
