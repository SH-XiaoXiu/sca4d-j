<script lang="ts" setup>
import type { Component } from 'vue';

import type { VbenFormSchema } from '@vben/common-ui';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { AuthenticationLogin, VbenPinInput, z } from '@vben/common-ui';
import {
  SvgGithubIcon,
  SvgGoogleIcon,
  SvgQQChatIcon,
  SvgWeChatIcon,
} from '@vben/icons';

import { Alert, Button, Divider, Input, message } from 'ant-design-vue';
import { Globe2, KeyRound, Mail } from 'lucide-vue-next';

import { AUTHENTICATION_METHOD_CODES } from '@sca4d/admin';

import { BrowserPasskeyError } from '#/auth/passkey';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();
const route = useRoute();
const router = useRouter();
const totpCode = ref('');
const pendingPassword = ref('');
const pendingEmailCode = ref('');
const pendingSmsCode = ref('');
const activePendingMethod = ref('');
const federatedLoadingCode = ref<null | string>(null);

const codeLoginPath = computed(() => {
  const redirect = route.query.redirect;
  return typeof redirect === 'string' && redirect
    ? `/auth/code-login?redirect=${encodeURIComponent(redirect)}`
    : '/auth/code-login';
});

const pendingEmailResendSeconds = computed(() =>
  secondsUntil(authStore.pendingEmailCodeChallenge?.resendAt),
);
const pendingSmsResendSeconds = computed(() =>
  secondsUntil(authStore.pendingSmsCodeChallenge?.resendAt),
);

const formSchema = computed((): VbenFormSchema[] => [
  {
    component: 'VbenInput',
    componentProps: {
      autocomplete: 'username',
      placeholder: $t('page.business.loginIdentifierPlaceholder'),
    },
    fieldName: 'username',
    label: $t('page.business.loginIdentifier'),
    rules: z
      .string()
      .min(1, { message: $t('page.business.loginIdentifierPlaceholder') }),
  },
  {
    component: 'VbenInputPassword',
    componentProps: {
      autocomplete: 'current-password',
      placeholder: $t('page.business.passwordPlaceholder'),
    },
    fieldName: 'password',
    label: $t('page.business.password'),
    rules: z
      .string()
      .min(1, { message: $t('page.business.passwordPlaceholder') }),
  },
]);

/** 校验并提交当前登录 attempt 的六位 TOTP 验证码。 */
async function handleTotpSubmit(): Promise<void> {
  if (!/^\d{6}$/u.test(totpCode.value)) return;
  try {
    await authStore.submitTotp(totpCode.value);
    totpCode.value = '';
  } catch {
    message.error($t('page.business.authenticationFailed'));
  }
}

/** 校验并提交当前登录 attempt 的密码。 */
async function handlePasswordSubmit(): Promise<void> {
  if (!pendingPassword.value) return;
  try {
    await authStore.submitPassword(pendingPassword.value);
    pendingPassword.value = '';
  } catch {
    message.error($t('page.business.authenticationFailed'));
  }
}

/** 请求当前登录 attempt 所属账号的 Email 验证码。 */
async function handlePendingEmailSend(): Promise<void> {
  const challenge = await authStore.requestPendingEmailCode();
  pendingEmailCode.value = '';
  message.success(
    $t('page.business.emailCodeSent', {
      recipient: challenge.maskedRecipient,
    }),
  );
  await nextTick();
}

/** 校验并提交当前登录 attempt 的 Email 验证码。 */
async function handlePendingEmailSubmit(): Promise<void> {
  if (!/^\d{6}$/u.test(pendingEmailCode.value)) return;
  try {
    await authStore.submitEmailCode(pendingEmailCode.value);
    pendingEmailCode.value = '';
  } catch {
    message.error($t('page.business.authenticationFailed'));
  }
}

/** 请求当前登录 attempt 所属账号的 SMS 验证码。 */
async function handlePendingSmsSend(): Promise<void> {
  const challenge = await authStore.requestPendingSmsCode();
  pendingSmsCode.value = '';
  message.success(
    $t('page.business.smsCodeSent', {
      recipient: challenge.maskedRecipient,
    }),
  );
  await nextTick();
}

/** 校验并提交当前登录 attempt 的 SMS 验证码。 */
async function handlePendingSmsSubmit(): Promise<void> {
  if (!/^\d{6}$/u.test(pendingSmsCode.value)) return;
  try {
    await authStore.submitSmsCode(pendingSmsCode.value);
    pendingSmsCode.value = '';
  } catch {
    message.error($t('page.business.authenticationFailed'));
  }
}

/** 根据当前选择的后续认证方法提交输入。 */
async function handlePendingSubmit(): Promise<void> {
  switch (activePendingMethod.value) {
    case AUTHENTICATION_METHOD_CODES.emailCode: {
      await handlePendingEmailSubmit();
      break;
    }
    case AUTHENTICATION_METHOD_CODES.passkey: {
      await handlePendingPasskey();
      break;
    }
    case AUTHENTICATION_METHOD_CODES.password: {
      await handlePasswordSubmit();
      break;
    }
    case AUTHENTICATION_METHOD_CODES.smsCode: {
      await handlePendingSmsSubmit();
      break;
    }
    case AUTHENTICATION_METHOD_CODES.totp: {
      await handleTotpSubmit();
      break;
    }
  }
}

/** 使用 Passkey 开始 discoverable login，并显示浏览器 ceremony 失败原因。 */
async function handlePasskeyLogin(): Promise<void> {
  try {
    await authStore.loginWithPasskey();
  } catch (error) {
    handlePasskeyError(error);
  }
}

/** 使用 Passkey 提交当前登录 attempt 的后续认证因素。 */
async function handlePendingPasskey(): Promise<void> {
  try {
    await authStore.submitPasskey();
  } catch (error) {
    handlePasskeyError(error);
  }
}

/** 把浏览器 Passkey 错误转换为登录页文案；其他请求错误继续交给调用链。 */
function handlePasskeyError(error: unknown): void {
  if (!(error instanceof BrowserPasskeyError)) throw error;
  if (error.kind === 'unsupported') {
    message.error($t('page.business.passkeyUnsupported'));
    return;
  }
  if (error.kind === 'cancelled') {
    message.info($t('page.business.passkeyCancelled'));
    return;
  }
  message.error($t('page.business.passkeyFailed'));
}

/** 打开现有 Email 验证码登录页，并保留登录后的目标地址。 */
async function handleEmailCodeLogin(): Promise<void> {
  await router.push(codeLoginPath.value);
}

/** 使用后端当前允许展示的 Provider 开始联邦登录。 */
async function handleFederatedLogin(providerCode: string): Promise<void> {
  federatedLoadingCode.value = providerCode;
  try {
    await authStore.loginWithFederated(providerCode);
  } catch {
    message.error($t('page.business.authenticationFailed'));
  } finally {
    federatedLoadingCode.value = null;
  }
}

/** 返回 Vben 已提供的品牌图标，未知 Provider 使用通用网络图标。 */
function federatedProviderIcon(iconReference: null | string): Component {
  const icons: Readonly<Record<string, Component>> = {
    github: SvgGithubIcon,
    google: SvgGoogleIcon,
    qq: SvgQQChatIcon,
    wechat: SvgWeChatIcon,
  };
  return (iconReference && icons[iconReference]) || Globe2;
}

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

/** 返回登录方法在当前语言下的名称。 */
function pendingMethodLabel(method: string): string {
  const labels: Record<string, string> = {
    [AUTHENTICATION_METHOD_CODES.emailCode]: $t('page.business.emailCode'),
    [AUTHENTICATION_METHOD_CODES.smsCode]: $t('page.business.smsCode'),
    [AUTHENTICATION_METHOD_CODES.federated]: $t(
      'page.security.methods.federated',
    ),
    [AUTHENTICATION_METHOD_CODES.passkey]: 'Passkey',
    [AUTHENTICATION_METHOD_CODES.password]: $t('page.business.password'),
    [AUTHENTICATION_METHOD_CODES.totp]: $t('page.business.totpLabel'),
  };
  return labels[method] ?? method;
}

/** 显示 VbenPinInput 捕获的验证码发送失败。 */
function handleEmailSendError(): void {
  message.error($t('page.business.emailCodeSendFailed'));
}

/** 使用后端签发时间计算 VbenPinInput 的整秒倒计时。 */
function secondsUntil(value: string | undefined): number {
  if (!value) return 0;
  return Math.max(1, Math.ceil((Date.parse(value) - Date.now()) / 1000));
}

watch(
  () => authStore.availablePendingLoginMethods,
  (methods) => {
    if (!methods.includes(activePendingMethod.value as never)) {
      activePendingMethod.value = methods[0] ?? '';
    }
  },
  { immediate: true },
);
</script>

<template>
  <div
    v-if="authStore.requiresAdditionalAuthentication"
    class="w-full"
    @keydown.enter.prevent="handlePendingSubmit"
  >
    <h2 class="mb-2 text-2xl font-semibold">
      {{ $t('page.business.additionalAuthenticationTitle') }}
    </h2>
    <p class="text-muted-foreground mb-6 text-sm">
      {{ $t('page.business.additionalAuthenticationDescription') }}
    </p>
    <Alert
      class="mb-4"
      :message="$t('page.business.authenticationContinues')"
      show-icon
      type="info"
    />
    <a-tabs
      v-model:active-key="activePendingMethod"
      :animated="false"
      :tab-bar-style="
        authStore.availablePendingLoginMethods.length > 1
          ? undefined
          : { display: 'none' }
      "
    >
      <a-tab-pane
        v-for="method in authStore.availablePendingLoginMethods"
        :key="method"
        :tab="pendingMethodLabel(method)"
      >
        <template v-if="method === AUTHENTICATION_METHOD_CODES.password">
          <Input.Password
            v-model:value="pendingPassword"
            :aria-label="$t('page.business.password')"
            autocomplete="current-password"
            size="large"
          />
          <Button
            class="mt-5 w-full"
            :disabled="!pendingPassword"
            :loading="authStore.loginLoading"
            size="large"
            type="primary"
            @click="handlePasswordSubmit"
          >
            {{ $t('page.business.continueLogin') }}
          </Button>
        </template>
        <template v-else-if="method === AUTHENTICATION_METHOD_CODES.totp">
          <Input
            v-model:value="totpCode"
            :aria-label="$t('page.business.totpLabel')"
            autocomplete="one-time-code"
            inputmode="numeric"
            :maxlength="6"
            placeholder="000000"
            size="large"
          />
          <Button
            class="mt-5 w-full"
            :disabled="!/^\d{6}$/u.test(totpCode)"
            :loading="authStore.loginLoading"
            size="large"
            type="primary"
            @click="handleTotpSubmit"
          >
            {{ $t('page.business.continueLogin') }}
          </Button>
        </template>
        <template v-else-if="method === AUTHENTICATION_METHOD_CODES.emailCode">
          <Alert
            v-if="authStore.pendingEmailCodeChallenge"
            class="mb-4"
            :message="
              $t('page.business.emailCodeSent', {
                recipient: authStore.pendingEmailCodeChallenge.maskedRecipient,
              })
            "
            show-icon
            type="success"
          />
          <VbenPinInput
            v-model="pendingEmailCode"
            :code-length="6"
            :create-text="
              (countdown: number) =>
                countdown > 0
                  ? $t('page.business.resendEmailCodeIn', { countdown })
                  : $t('page.business.sendEmailCode')
            "
            :handle-send-code="handlePendingEmailSend"
            :loading="authStore.emailCodeSending"
            :max-time="pendingEmailResendSeconds"
            @send-error="handleEmailSendError"
          />
          <Button
            class="mt-5 w-full"
            :disabled="!/^\d{6}$/u.test(pendingEmailCode)"
            :loading="authStore.loginLoading"
            size="large"
            type="primary"
            @click="handlePendingEmailSubmit"
          >
            {{ $t('page.business.continueLogin') }}
          </Button>
        </template>
        <template v-else-if="method === AUTHENTICATION_METHOD_CODES.smsCode">
          <Alert
            v-if="authStore.pendingSmsCodeChallenge"
            class="mb-4"
            :message="
              $t('page.business.smsCodeSent', {
                recipient: authStore.pendingSmsCodeChallenge.maskedRecipient,
              })
            "
            show-icon
            type="success"
          />
          <VbenPinInput
            v-model="pendingSmsCode"
            :code-length="6"
            :create-text="
              (countdown: number) =>
                countdown > 0
                  ? $t('page.business.resendSmsCodeIn', { countdown })
                  : $t('page.business.sendSmsCode')
            "
            :handle-send-code="handlePendingSmsSend"
            :loading="authStore.smsCodeSending"
            :max-time="pendingSmsResendSeconds"
          />
          <Button
            class="mt-5 w-full"
            :disabled="!/^\d{6}$/u.test(pendingSmsCode)"
            :loading="authStore.loginLoading"
            size="large"
            type="primary"
            @click="handlePendingSmsSubmit"
          >
            {{ $t('page.business.continueLogin') }}
          </Button>
        </template>
        <Button
          v-else-if="method === AUTHENTICATION_METHOD_CODES.passkey"
          class="w-full"
          :loading="authStore.loginLoading"
          size="large"
          type="primary"
          @click="handlePendingPasskey"
        >
          <KeyRound :size="17" aria-hidden="true" />
          {{ $t('page.business.passkeyLogin') }}
        </Button>
      </a-tab-pane>
    </a-tabs>
  </div>
  <AuthenticationLogin
    v-else
    :code-login-path="codeLoginPath"
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    :show-code-login="false"
    :show-forget-password="false"
    :show-qrcode-login="false"
    :show-register="false"
    :show-third-party-login="false"
    :sub-title="$t('page.business.authDescription')"
    :title="$t('page.business.loginTitle')"
    @submit="authStore.authLogin"
  >
    <template #third-party-login>
      <Divider>{{ $t('page.business.or') }}</Divider>
      <div class="grid gap-3">
        <Button
          v-for="provider in authStore.federatedProviders"
          :key="provider.code"
          class="w-full"
          :disabled="federatedLoadingCode !== null"
          :loading="federatedLoadingCode === provider.code"
          size="large"
          @click="handleFederatedLogin(provider.code)"
        >
          <component
            :is="federatedProviderIcon(provider.iconReference)"
            aria-hidden="true"
            class="size-[17px]"
          />
          {{ provider.buttonText }}
        </Button>
        <Button class="w-full" size="large" @click="handleEmailCodeLogin">
          <Mail :size="17" aria-hidden="true" />
          {{ $t('page.business.emailCodeLogin') }}
        </Button>
        <Button
          v-if="authStore.passkeySupported"
          class="w-full"
          :disabled="federatedLoadingCode !== null"
          :loading="authStore.loginLoading"
          size="large"
          @click="handlePasskeyLogin"
        >
          <KeyRound :size="17" aria-hidden="true" />
          {{ $t('page.business.passkeyLogin') }}
        </Button>
      </div>
    </template>
  </AuthenticationLogin>
</template>
