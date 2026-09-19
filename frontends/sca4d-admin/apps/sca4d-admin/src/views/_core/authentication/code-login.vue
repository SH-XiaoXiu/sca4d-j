<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';
import type { Recordable } from '@vben/types';

import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { AuthenticationCodeLogin, z } from '@vben/common-ui';
import { LOGIN_PATH } from '@vben/constants';

import { message } from 'ant-design-vue';

import { $t } from '#/locales';
import { useAuthStore } from '#/store';

defineOptions({ name: 'CodeLogin' });

const authStore = useAuthStore();
const route = useRoute();
const router = useRouter();
const loginMode = ref<'email' | 'sms'>('email');

const loginPath = computed(() => {
  const redirect = route.query.redirect;
  return typeof redirect === 'string' && redirect
    ? `${LOGIN_PATH}?redirect=${encodeURIComponent(redirect)}`
    : LOGIN_PATH;
});

const subTitle = computed(() =>
  loginMode.value === 'email'
    ? authStore.initialEmailCodeChallenge
      ? $t('page.business.emailCodeSent', {
          recipient: authStore.initialEmailCodeChallenge.maskedRecipient,
        })
      : $t('page.business.emailCodeLoginDescription')
    : authStore.initialSmsCodeChallenge
      ? $t('page.business.smsCodeSent', {
          recipient: authStore.initialSmsCodeChallenge.maskedRecipient,
        })
      : $t('page.business.smsCodeLoginDescription'),
);

const resendSeconds = computed(() =>
  secondsUntil(
    loginMode.value === 'email'
      ? authStore.initialEmailCodeChallenge?.resendAt
      : authStore.initialSmsCodeChallenge?.resendAt,
  ),
);

const formSchema = computed((): VbenFormSchema[] => [
  {
    component: 'VbenInput',
    componentProps:
      loginMode.value === 'email'
        ? {
            autocomplete: 'email',
            placeholder: $t('page.business.emailPlaceholder'),
            type: 'email',
          }
        : {
            autocomplete: 'tel',
            placeholder: $t('page.business.phonePlaceholder'),
            type: 'tel',
          },
    fieldName: loginMode.value === 'email' ? 'email' : 'phoneNumber',
    label:
      loginMode.value === 'email'
        ? $t('page.business.email')
        : $t('page.business.phoneNumber'),
    rules:
      loginMode.value === 'email'
        ? z
            .string()
            .trim()
            .min(1, { message: $t('page.business.emailPlaceholder') })
            .email({ message: $t('page.business.invalidEmail') })
            .max(320, { message: $t('page.business.invalidEmail') })
        : z
            .string()
            .trim()
            .regex(/^\+[1-9]\d{1,14}$/u, {
              message: $t('page.business.invalidPhone'),
            }),
  },
  {
    component: 'VbenPinInput',
    componentProps: (values: Recordable<unknown>) => {
      const target =
        loginMode.value === 'email'
          ? typeof values.email === 'string'
            ? values.email.trim()
            : ''
          : typeof values.phoneNumber === 'string'
            ? values.phoneNumber.trim()
            : '';
      return {
        codeLength: 6,
        createText: (countdown: number) =>
          countdown > 0
            ? $t(
                loginMode.value === 'email'
                  ? 'page.business.resendEmailCodeIn'
                  : 'page.business.resendSmsCodeIn',
                { countdown },
              )
            : $t(
                loginMode.value === 'email'
                  ? 'page.business.sendEmailCode'
                  : 'page.business.sendSmsCode',
              ),
        disabled:
          loginMode.value === 'email' ? !isEmail(target) : !isPhone(target),
        handleSendCode: async () => {
          const challenge =
            loginMode.value === 'email'
              ? await authStore.requestEmailLoginCode(target)
              : await authStore.requestSmsLoginCode(target);
          message.success(
            $t(
              loginMode.value === 'email'
                ? 'page.business.emailCodeSent'
                : 'page.business.smsCodeSent',
              {
                recipient: challenge.maskedRecipient,
              },
            ),
          );
          await nextTick();
        },
        loading:
          loginMode.value === 'email'
            ? authStore.emailCodeSending
            : authStore.smsCodeSending,
        maxTime: resendSeconds.value,
        onSendError: handleSendError,
      };
    },
    fieldName: 'code',
    label:
      loginMode.value === 'email'
        ? $t('page.business.emailCode')
        : $t('page.business.smsCode'),
    rules: z.string().regex(/^\d{6}$/u, {
      message: $t('page.business.emailCodeLength'),
    }),
  },
]);

/** 使用当前 Challenge 提交 Email Code，并在需要后续因素时返回登录页。 */
async function handleLogin(values: Recordable<unknown>): Promise<void> {
  const target =
    loginMode.value === 'email'
      ? typeof values.email === 'string'
        ? values.email
        : ''
      : typeof values.phoneNumber === 'string'
        ? values.phoneNumber
        : '';
  const code = typeof values.code === 'string' ? values.code : '';
  try {
    const result =
      loginMode.value === 'email'
        ? await authStore.loginWithEmailCode(target, code)
        : await authStore.loginWithSmsCode(target, code);
    if (!result.userInfo) {
      await router.replace({
        path: LOGIN_PATH,
        query:
          typeof route.query.redirect === 'string'
            ? { redirect: route.query.redirect }
            : {},
      });
    }
  } catch {
    message.error($t('page.business.authenticationFailed'));
  }
}

/** 显示 VbenPinInput 捕获的发送失败。 */
function handleSendError(): void {
  message.error(
    $t(
      loginMode.value === 'email'
        ? 'page.business.emailCodeSendFailed'
        : 'page.business.smsCodeSendFailed',
    ),
  );
}

/** 使用基本邮箱形状控制发送按钮，最终格式仍由表单和后端校验。 */
function isEmail(value: string): boolean {
  return value.length <= 320 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/u.test(value);
}

function isPhone(value: string): boolean {
  return /^\+[1-9]\d{1,14}$/u.test(value);
}

/** 使用后端签发时间计算 VbenPinInput 的整秒倒计时。 */
function secondsUntil(value: string | undefined): number {
  if (!value) return 0;
  return Math.max(1, Math.ceil((Date.parse(value) - Date.now()) / 1000));
}

onBeforeUnmount(() => {
  authStore.clearInitialEmailCodeChallenge();
  authStore.clearInitialSmsCodeChallenge();
});
</script>

<template>
  <div>
    <a-segmented
      v-model:value="loginMode"
      block
      class="mb-5"
      :options="[
        { label: $t('page.business.emailCode'), value: 'email' },
        { label: $t('page.business.smsCode'), value: 'sms' },
      ]"
    />
    <AuthenticationCodeLogin
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :login-path="loginPath"
      :sub-title="subTitle"
      :title="
        $t(
          loginMode === 'email'
            ? 'page.business.emailCodeLoginTitle'
            : 'page.business.smsCodeLoginTitle',
        )
      "
      @submit="handleLogin"
    />
  </div>
</template>
