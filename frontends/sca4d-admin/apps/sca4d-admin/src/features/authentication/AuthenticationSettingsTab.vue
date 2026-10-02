<script setup lang="ts">
import type {
  AuthenticationMethodSettings,
  AuthenticationSettings,
  Int64String,
} from '@sca4d/admin';

import type { VbenFormSchema } from '#/adapter/form';

import { computed, reactive, watch } from 'vue';

import { z } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import { Save } from 'lucide-vue-next';

import { SjResourceState, useSjForm } from '#/components/sj';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { useSecurityStore } from '#/store/security';

import { authenticationMethodLabel } from './presentation';

const { errorText } = useSjPresentation();

const store = useSecurityStore();
interface AuthenticationSettingsFormValues {
  loginFactorCount: number;
  reusableAuthenticationMaximumAgeSeconds: Int64String;
  sensitiveActionFactorCount: number;
}

const form = reactive<{
  methods: AuthenticationMethodSettings[];
}>({
  methods: [],
});
const loading = computed(
  () =>
    store.authenticationSettings.phase === 'loading' ||
    store.verification.phase === 'loading',
);

const formSchema = computed((): VbenFormSchema[] => [
  {
    component: 'InputNumber',
    componentProps: {
      min: 1,
    },
    fieldName: 'loginFactorCount',
    label: $t('page.security.settings.loginFactorCount'),
    rules: z.number().min(1),
  },
  {
    component: 'InputNumber',
    componentProps: {
      min: 0,
    },
    fieldName: 'sensitiveActionFactorCount',
    label: $t('page.security.settings.sensitiveActionFactorCount'),
    rules: z.number().min(0),
  },
  {
    component: 'InputNumber',
    componentProps: {
      min: 1,
      stringMode: true,
    },
    fieldName: 'reusableAuthenticationMaximumAgeSeconds',
    label: $t('page.security.settings.reusableAge'),
    rules: z.union([z.number().min(1), z.string().regex(/^[1-9]\d*$/)]),
  },
]);

const [SettingsForm, settingsFormApi] = useSjForm(
  reactive({
    commonConfig: {
      componentProps: {
        class: 'w-full',
      },
    },
    layout: 'vertical',
    schema: formSchema,
    showDefaultActions: false,
    wrapperClass: 'grid grid-cols-1 gap-x-5 gap-y-1 md:grid-cols-3',
  }),
);

watch(
  () => store.authenticationSettings.settings,
  (settings) => {
    if (!settings) return;
    form.methods = settings.methods.map((method) => ({ ...method }));
    void settingsFormApi.setValues(
      {
        loginFactorCount: settings.loginFactorCount,
        reusableAuthenticationMaximumAgeSeconds:
          settings.reusableAuthenticationMaximumAgeSeconds,
        sensitiveActionFactorCount: settings.sensitiveActionFactorCount,
      },
      false,
    );
  },
  { immediate: true },
);

/** 使用表单中的完整值替换账号认证要求。 */
async function save(): Promise<void> {
  const { valid } = await settingsFormApi.validate();
  if (!valid) return;
  const values =
    await settingsFormApi.getValues<AuthenticationSettingsFormValues>();
  const replacement: AuthenticationSettings = {
    loginFactorCount: values.loginFactorCount,
    methods: form.methods.map((method) => ({ ...method })),
    reusableAuthenticationMaximumAgeSeconds:
      values.reusableAuthenticationMaximumAgeSeconds,
    sensitiveActionFactorCount: values.sensitiveActionFactorCount,
  };
  try {
    await store.replaceAuthenticationSettings(replacement);
  } catch {
    message.error(
      errorText(store.authenticationSettings.error ?? store.verification.error),
    );
  }
}
</script>

<template>
  <SjResourceState
    :error="store.authenticationSettings.error"
    :phase="store.authenticationSettings.phase"
    @retry="store.loadAuthenticationSettings"
  >
    <section class="w-full">
      <header
        class="mb-5 flex min-h-10 items-center justify-between gap-4 max-sm:flex-col max-sm:items-stretch"
      >
        <h2 class="text-base font-semibold">
          {{ $t('page.security.settings.title') }}
        </h2>
        <a-button
          class="max-sm:w-full"
          :loading="loading"
          type="primary"
          @click="save"
        >
          <Save :size="17" aria-hidden="true" />
          {{ $t('page.security.common.save') }}
        </a-button>
      </header>

      <div class="rounded-md border bg-background p-4">
        <SettingsForm />
      </div>

      <div class="mt-5 overflow-x-auto rounded-md border bg-background">
        <div
          class="grid min-w-[720px] grid-cols-[minmax(180px,1fr)_repeat(3,minmax(150px,180px))] gap-3 border-b bg-muted/40 px-4 py-3 text-sm font-medium"
        >
          <span>{{ $t('page.security.settings.method') }}</span>
          <span class="text-center">
            {{ $t('page.security.settings.loginStart') }}
          </span>
          <span class="text-center">
            {{ $t('page.security.settings.loginAdditional') }}
          </span>
          <span class="text-center">
            {{ $t('page.security.settings.sensitiveAction') }}
          </span>
        </div>
        <div
          v-for="method in form.methods"
          :key="method.methodCode"
          class="grid min-w-[720px] grid-cols-[minmax(180px,1fr)_repeat(3,minmax(150px,180px))] items-center gap-3 border-b px-4 py-3 last:border-b-0"
        >
          <span class="font-medium">
            {{ authenticationMethodLabel(method.methodCode) }}
          </span>
          <div class="flex justify-center">
            <a-switch v-model:checked="method.loginStartEnabled" />
          </div>
          <div class="flex justify-center">
            <a-switch v-model:checked="method.loginAdditionalEnabled" />
          </div>
          <div class="flex justify-center">
            <a-switch v-model:checked="method.sensitiveActionEnabled" />
          </div>
        </div>
      </div>
    </section>
  </SjResourceState>
</template>
