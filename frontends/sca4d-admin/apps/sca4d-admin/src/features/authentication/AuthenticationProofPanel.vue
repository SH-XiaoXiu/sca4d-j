<script setup lang="ts">
import type {
  AuthenticationCodeNotice,
  AuthenticationProofSubmission,
} from './proof';

import { computed, onScopeDispose, reactive, ref, watch } from 'vue';

import { VbenPinInput } from '@vben/common-ui';

import { AUTHENTICATION_METHOD_CODES as METHODS } from '@sca4d/admin';
import { message } from 'ant-design-vue';

import { BrowserPasskeyError } from '#/auth/passkey';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';

import {
  authenticationMethodLabel,
  browserPasskeyErrorText,
} from './presentation';

/** 登录和动作认证共用因素输入；秘密只保留在当前面板内。 */
const props = withDefaults(
  defineProps<{
    /** 当前服务端认证操作的标识，变化后清除输入。 */ methods: readonly string[];
    /** 服务端实际允许提交的认证方法。 */ notices?: Readonly<
      Record<string, AuthenticationCodeNotice | null | undefined>
    >;
    /** 浏览器是否支持 Passkey。 */ operationId?: null | string;
    /** 不同验证码方法的公开发送结果。 */ passkeySupported?: boolean;
    /** 请求验证码，不负责执行认证。 */ sendCode?: (
      method: string,
    ) => Promise<unknown>;
    /** 提交一个已经完成格式检查的因素。 */ showSubmit?: boolean;
    /** 对话框使用自己的确认按钮，页面内面板使用内置提交。 */ submitProof: (
      proof: AuthenticationProofSubmission,
    ) => Promise<unknown>;
  }>(),
  {
    operationId: null,
    passkeySupported: true,
    notices: () => ({}),
    sendCode: undefined,
    showSubmit: true,
  },
);
const { t, errorText } = useSjPresentation();
const selected = ref('');
const submitting = ref(false);
const sending = ref(false);
const input = reactive({ password: '', code: '' });
const supported = [
  METHODS.password,
  METHODS.totp,
  METHODS.emailCode,
  METHODS.smsCode,
  METHODS.passkey,
] as readonly string[];
const options = computed(() =>
  props.methods.map((method) => ({
    value: method,
    label: supported.includes(method)
      ? authenticationMethodLabel(method)
      : $t('page.security.verification.unsupportedMethod'),
    disabled:
      !supported.includes(method) ||
      (method === METHODS.passkey && !props.passkeySupported),
  })),
);
const notice = computed(() => props.notices[selected.value]);
const canSubmit = computed(() => {
  if (submitting.value) return false;
  if (selected.value === METHODS.password) return input.password.length > 0;
  if (selected.value === METHODS.passkey) return props.passkeySupported;
  if (selected.value === METHODS.totp) return /^\d{6}$/u.test(input.code);
  return (
    (selected.value === METHODS.emailCode ||
      selected.value === METHODS.smsCode) &&
    Boolean(notice.value) &&
    /^\d{6}$/u.test(input.code)
  );
});
watch(
  () => [props.operationId, props.methods] as const,
  () => {
    clear();
    if (
      !options.value.some(
        (method) => method.value === selected.value && !method.disabled,
      )
    )
      selected.value =
        options.value.find((method) => !method.disabled)?.value ?? '';
  },
  { immediate: true },
);
watch(selected, clear);
onScopeDispose(clear);
/** 切换操作或方法后清除秘密。 */
function clear() {
  input.password = '';
  input.code = '';
}
/** 一个认证操作的因素提交按顺序执行，不影响其他工作区的状态。 */
async function submit() {
  if (!canSubmit.value) return;
  let proof: AuthenticationProofSubmission;
  switch (selected.value) {
    case METHODS.emailCode:
    case METHODS.smsCode:
    case METHODS.totp: {
      proof = { methodCode: selected.value, code: input.code };
      break;
    }
    case METHODS.passkey: {
      proof = { methodCode: METHODS.passkey };
      break;
    }
    case METHODS.password: {
      proof = { methodCode: METHODS.password, password: input.password };
      break;
    }
    default: {
      return;
    }
  }
  submitting.value = true;
  try {
    await props.submitProof(proof);
    clear();
  } catch (error) {
    const text = browserPasskeyErrorText(error) ?? errorText(error);
    if (error instanceof BrowserPasskeyError && error.kind === 'cancelled')
      message.info(text);
    else message.error(text);
  } finally {
    submitting.value = false;
  }
}
/** 验证码发送与因素提交分别管理 loading。 */
async function send() {
  if (!props.sendCode || sending.value) return;
  sending.value = true;
  try {
    await props.sendCode(selected.value);
    input.code = '';
  } finally {
    sending.value = false;
  }
}
/** 后端时间决定重发等待，不硬编码冷却时间。 */
function resendSeconds() {
  const time = Date.parse(notice.value?.resendAt ?? '');
  return Number.isFinite(time)
    ? Math.max(0, Math.ceil((time - Date.now()) / 1000))
    : 0;
}
defineExpose({ submit, canSubmit, submitting });
</script>
<template>
  <div class="space-y-4">
    <a-segmented
      v-if="options.length > 1"
      v-model:value="selected"
      block
      :options="options"
    />
    <a-alert
      v-if="!selected"
      type="warning"
      show-icon
      :message="$t('page.security.verification.unsupportedMethod')"
    />
    <a-form v-else layout="vertical" @finish="submit">
      <a-form-item
        v-if="selected === METHODS.password"
        :label="t('currentPassword')"
        required
      >
        <a-input-password
          v-model:value="input.password"
          autocomplete="current-password"
          :aria-label="t('currentPassword')"
        />
      </a-form-item>
      <a-form-item
        v-else-if="selected === METHODS.totp"
        :label="t('totp')"
        required
      >
        <a-input
          v-model:value="input.code"
          autocomplete="one-time-code"
          inputmode="numeric"
          :maxlength="6"
          :aria-label="t('totp')"
        />
      </a-form-item>
      <a-form-item
        v-else-if="
          selected === METHODS.emailCode || selected === METHODS.smsCode
        "
        :label="authenticationMethodLabel(selected)"
        required
      >
        <a-alert
          v-if="notice"
          class="mb-3"
          type="success"
          show-icon
          :message="notice.maskedRecipient"
        />
        <VbenPinInput
          v-model="input.code"
          :code-length="6"
          :handle-send-code="send"
          :loading="sending"
          :max-time="resendSeconds()"
          :create-text="
            (seconds: number) =>
              seconds > 0
                ? $t('page.security.verification.smsResendIn', {
                    countdown: seconds,
                  })
                : $t('page.business.sendCode')
          "
          @send-error="(error) => message.error(errorText(error))"
        />
      </a-form-item>
      <a-alert
        v-else-if="selected === METHODS.passkey"
        show-icon
        type="info"
        :message="t('usePasskeyToContinue')"
      />
      <a-button
        v-if="showSubmit"
        class="mt-4 w-full"
        html-type="submit"
        type="primary"
        :disabled="!canSubmit"
        :loading="submitting"
      >
        {{ t('verify') }}
      </a-button>
    </a-form>
  </div>
</template>
