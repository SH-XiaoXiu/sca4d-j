<script setup lang="ts">
import type { Component } from 'vue';

import type { VbenFormSchema } from '@vben/common-ui';

import type { AuthenticationProofSubmission } from '#/features/authentication/proof';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { AuthenticationLogin, z } from '@vben/common-ui';
import {
  SvgGithubIcon,
  SvgGoogleIcon,
  SvgQQChatIcon,
  SvgWeChatIcon,
} from '@vben/icons';

import { AUTHENTICATION_METHOD_CODES as METHODS } from '@sca4d/admin';
import { Button, Divider, message } from 'ant-design-vue';
import { Globe2, KeyRound, Mail } from 'lucide-vue-next';

import { BrowserPasskeyError } from '#/auth/passkey';
import AuthenticationProofPanel from '#/features/authentication/AuthenticationProofPanel.vue';
import { browserPasskeyErrorText } from '#/features/authentication/presentation';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';
const authStore = useAuthStore();
const route = useRoute();
const router = useRouter();
const passkeySubmitting = ref(false);
const passwordSubmitting = ref(false);
const federatedSubmitting = ref(new Set<string>());
const notices = computed(() => ({
  [METHODS.emailCode]: authStore.pendingEmailCodeChallenge,
  [METHODS.smsCode]: authStore.pendingSmsCodeChallenge,
}));
const codeLoginPath = computed(() =>
  typeof route.query.redirect === 'string'
    ? `/auth/code-login?redirect=${encodeURIComponent(route.query.redirect)}`
    : '/auth/code-login',
);
const formSchema = computed((): VbenFormSchema[] => [
  {
    component: 'VbenInput',
    fieldName: 'username',
    label: $t('page.business.loginIdentifier'),
    componentProps: {
      autocomplete: 'username',
      placeholder: $t('page.business.loginIdentifierPlaceholder'),
    },
    rules: z
      .string()
      .min(1, { message: $t('page.business.loginIdentifierPlaceholder') }),
  },
  {
    component: 'VbenInputPassword',
    fieldName: 'password',
    label: $t('page.business.password'),
    componentProps: { autocomplete: 'current-password' },
    rules: z
      .string()
      .min(1, { message: $t('page.business.passwordPlaceholder') }),
  },
]);
onMounted(async () => {
  const completion = route.query.federatedCompletion;
  if (typeof completion === 'string' && completion) {
    try {
      await authStore.completeFederatedLogin(completion);
      await router.replace({
        query: { ...route.query, federatedCompletion: undefined },
      });
      return;
    } catch {
      message.error($t('page.business.authenticationFailed'));
    }
  }
  await authStore.loadFederatedProviders();
});
/** 密码起点只管理自身的提交状态。 */
async function passwordLogin(
  values: Parameters<typeof authStore.authLogin>[0],
) {
  if (passwordSubmitting.value) return;
  passwordSubmitting.value = true;
  try {
    await authStore.authLogin(values);
  } finally {
    passwordSubmitting.value = false;
  }
}
/** 浏览器 Passkey 请求与其他登录入口分别显示等待状态。 */
async function passkeyLogin() {
  if (passkeySubmitting.value) return;
  passkeySubmitting.value = true;
  try {
    await authStore.loginWithPasskey();
  } catch (error) {
    const text =
      browserPasskeyErrorText(error) ??
      $t('page.business.authenticationFailed');
    if (error instanceof BrowserPasskeyError && error.kind === 'cancelled')
      message.info(text);
    else message.error(text);
  } finally {
    passkeySubmitting.value = false;
  }
}
/** 一个 Provider 的跳转请求不会禁用其他入口。 */
async function federatedLogin(code: string) {
  if (federatedSubmitting.value.has(code)) return;
  federatedSubmitting.value.add(code);
  try {
    await authStore.loginWithFederated(code);
  } catch {
    message.error($t('page.business.authenticationFailed'));
  } finally {
    federatedSubmitting.value.delete(code);
  }
}
/** 按服务端返回的同一 attempt 提交后续因素。 */
async function submitProof(proof: AuthenticationProofSubmission) {
  switch (proof.methodCode) {
    case METHODS.emailCode: {
      await authStore.submitEmailCode(proof.code);
      break;
    }
    case METHODS.password: {
      await authStore.submitPassword(proof.password);
      break;
    }
    case METHODS.smsCode: {
      await authStore.submitSmsCode(proof.code);
      break;
    }
    case METHODS.totp: {
      await authStore.submitTotp(proof.code);
      break;
    }
    default: {
      await authStore.submitPasskey();
    }
  }
}
/** 验证码发送不执行因素提交。 */
async function sendCode(method: string) {
  await (method === METHODS.emailCode
    ? authStore.requestPendingEmailCode()
    : authStore.requestPendingSmsCode());
}
/** 使用已提供的品牌图标，未知品牌显示通用网络图标。 */
function providerIcon(reference: null | string): Component {
  const icons: Readonly<Record<string, Component>> = {
    github: SvgGithubIcon,
    google: SvgGoogleIcon,
    qq: SvgQQChatIcon,
    wechat: SvgWeChatIcon,
  };
  return reference ? (icons[reference] ?? Globe2) : Globe2;
}
</script>
<template>
  <section v-if="authStore.requiresAdditionalAuthentication" class="w-full">
    <h2 class="mb-2 text-xl font-semibold">
      {{ $t('page.business.additionalAuthenticationTitle') }}
    </h2>
    <p class="mb-6 text-sm text-muted-foreground">
      {{ $t('page.business.additionalAuthenticationDescription') }}
    </p>
    <AuthenticationProofPanel
      :methods="authStore.availablePendingLoginMethods"
      :operation-id="authStore.pendingLoginAttemptId"
      :passkey-supported="authStore.passkeySupported"
      :notices="notices"
      :submit-proof="submitProof"
      :send-code="sendCode"
    />
  </section>
  <AuthenticationLogin
    v-else
    :form-schema="formSchema"
    :loading="passwordSubmitting"
    :code-login-path="codeLoginPath"
    :show-code-login="false"
    :show-forget-password="false"
    :show-qrcode-login="false"
    :show-register="false"
    :show-third-party-login="false"
    :title="$t('page.business.loginTitle')"
    :sub-title="$t('page.business.authDescription')"
    @submit="passwordLogin"
  >
    <template #third-party-login>
      <Divider>{{ $t('page.business.or') }}</Divider>
      <div class="grid gap-3">
        <Button
          v-for="provider in authStore.federatedProviders"
          :key="provider.code"
          class="w-full"
          size="large"
          :loading="federatedSubmitting.has(provider.code)"
          @click="federatedLogin(provider.code)"
        >
          <component
            :is="providerIcon(provider.iconReference)"
            :size="17"
            aria-hidden="true"
          />
          {{ provider.buttonText }}
        </Button>
        <Button class="w-full" size="large" @click="router.push(codeLoginPath)">
          <Mail :size="17" aria-hidden="true" />
          {{ $t('page.business.emailCodeLogin') }}
        </Button>
        <Button
          v-if="authStore.passkeySupported"
          class="w-full"
          size="large"
          :loading="passkeySubmitting"
          @click="passkeyLogin"
        >
          <KeyRound :size="17" aria-hidden="true" />
          {{ $t('page.business.passkeyLogin') }}
        </Button>
      </div>
    </template>
  </AuthenticationLogin>
</template>
