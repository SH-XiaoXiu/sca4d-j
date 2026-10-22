<script setup lang="ts">
import type { FormInstance } from 'ant-design-vue';

import { computed, reactive, ref, watch } from 'vue';

import { message } from 'ant-design-vue';
import { Save } from 'lucide-vue-next';

import { SjResourceState } from '#/components/sj';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { useSecurityStore } from '#/store/security';

const { dateTime, errorText } = useSjPresentation();

const store = useSecurityStore();
const formRef = ref<FormInstance>();
const form = reactive({ confirmPassword: '', newPassword: '' });
const loading = computed(
  () =>
    store.password.phase === 'loading' ||
    store.verification.phase === 'loading',
);

watch(
  () => store.completedAction,
  (action) => {
    if (action !== 'password.replace') return;
    form.newPassword = '';
    form.confirmPassword = '';
    formRef.value?.clearValidate();
  },
);

/** 校验新密码并开始替换密码所需的动作认证。 */
async function save(): Promise<void> {
  await formRef.value?.validate();
  try {
    await store.replacePassword(form.newPassword);
  } catch {
    message.error(errorText(store.password.error ?? store.verification.error));
  }
}

/** 校验两次输入的新密码一致。 */
function validateConfirmation(): Promise<void> {
  return form.confirmPassword === form.newPassword
    ? Promise.resolve()
    : Promise.reject(new Error($t('page.security.password.mismatch')));
}
</script>

<template>
  <SjResourceState
    :error="store.password.error"
    :phase="store.password.phase"
    @retry="store.loadPassword"
  >
    <section class="w-full">
      <header class="mb-6 flex min-h-10 items-center gap-3">
        <h2 class="text-base font-semibold">
          {{ $t('page.security.password.title') }}
        </h2>
        <span
          v-if="store.password.password"
          class="text-muted-foreground text-xs"
        >
          {{ dateTime(store.password.password.updatedAt) }}
        </span>
      </header>

      <a-form
        ref="formRef"
        class="max-w-xl"
        layout="vertical"
        :model="form"
        @finish="save"
      >
        <a-form-item
          :label="$t('page.security.password.newPassword')"
          name="newPassword"
          :rules="[
            {
              required: true,
              message: $t('page.security.common.required'),
            },
            {
              min: 8,
              max: 128,
              message: $t('page.security.password.length'),
            },
          ]"
        >
          <a-input-password
            v-model:value="form.newPassword"
            autocomplete="new-password"
          />
        </a-form-item>
        <a-form-item
          :label="$t('page.security.password.confirmPassword')"
          name="confirmPassword"
          :rules="[
            {
              required: true,
              message: $t('page.security.common.required'),
            },
            { trigger: 'change', validator: validateConfirmation },
          ]"
        >
          <a-input-password
            v-model:value="form.confirmPassword"
            autocomplete="new-password"
          />
        </a-form-item>
        <a-button html-type="submit" :loading="loading" type="primary">
          <Save :size="17" aria-hidden="true" />
          {{ $t('page.security.common.save') }}
        </a-button>
      </a-form>
    </section>
  </SjResourceState>
</template>
