<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';

import { VbenPinInput } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import { KeyRound } from 'lucide-vue-next';
import { AUTHENTICATION_METHOD_CODES } from '@sca4d/admin';

import { useI18n } from '#/i18n';
import { $t } from '#/locales';
import { useSecurityStore } from '#/store/security';

import {
  authenticationMethodLabel,
  browserPasskeyErrorText,
  securityActionLabel,
} from './presentation';

interface MethodOption {
  disabled: boolean;
  label: string;
  value: string;
}

const store = useSecurityStore();
const { errorText } = useI18n();
const selectedMethod = ref('');
const proof = reactive({ code: '', password: '' });
const smsResendSeconds = computed(() =>
  secondsUntil(store.smsCodeChallenge?.resendAt),
);
const availableMethods = computed(
  () => store.verification.verification?.availableMethods ?? [],
);
const remainingFactorCount = computed(
  () => store.verification.verification?.remainingFactorCount ?? 0,
);
const methodOptions = computed<MethodOption[]>(() =>
  availableMethods.value.map((method) => ({
    disabled: !supportsMethod(method),
    label: authenticationMethodLabel(method),
    value: method,
  })),
);
const title = computed(() => securityActionLabel(store.pendingAction));
const submitting = computed(() => store.verification.phase === 'loading');
const canSubmit = computed(() => {
  if (selectedMethod.value === AUTHENTICATION_METHOD_CODES.password)
    return proof.password.length > 0;
  if (selectedMethod.value === AUTHENTICATION_METHOD_CODES.totp)
    return /^\d{6}$/u.test(proof.code);
  if (selectedMethod.value === AUTHENTICATION_METHOD_CODES.smsCode) {
    return Boolean(store.smsCodeChallenge) && /^\d{6}$/u.test(proof.code);
  }
  return (
    selectedMethod.value === AUTHENTICATION_METHOD_CODES.passkey &&
    store.passkeySupported
  );
});

watch(
  [() => store.verificationOpen, availableMethods],
  ([open, methods]) => {
    if (!open) {
      selectedMethod.value = '';
      proof.code = '';
      proof.password = '';
      return;
    }
    if (
      methods.includes(selectedMethod.value) &&
      supportsMethod(selectedMethod.value)
    ) {
      return;
    }
    selectedMethod.value =
      methods.find((method) => supportsMethod(method)) ?? methods[0] ?? '';
  },
  { immediate: true },
);

/** 判断页面能否提交指定认证方法；未知方法保持可见但不可选择。 */
function supportsMethod(method: string): boolean {
  if (
    method === AUTHENTICATION_METHOD_CODES.password ||
    method === AUTHENTICATION_METHOD_CODES.totp ||
    method === AUTHENTICATION_METHOD_CODES.smsCode
  )
    return true;
  return (
    method === AUTHENTICATION_METHOD_CODES.passkey && store.passkeySupported
  );
}

/** 提交调用方当前选择的认证因素，并保留后端返回的后续方法。 */
async function submit(): Promise<void> {
  if (submitting.value || !canSubmit.value) return;
  try {
    if (selectedMethod.value === AUTHENTICATION_METHOD_CODES.password) {
      await store.submitPassword(proof.password);
      proof.password = '';
      return;
    }
    if (selectedMethod.value === AUTHENTICATION_METHOD_CODES.totp) {
      await store.submitTotp(proof.code);
      proof.code = '';
      return;
    }
    if (selectedMethod.value === AUTHENTICATION_METHOD_CODES.smsCode) {
      await store.submitSmsCode(proof.code);
      proof.code = '';
      return;
    }
    await store.submitPasskey();
  } catch (error) {
    message.error(
      browserPasskeyErrorText(error) ?? errorText(store.verification.error),
    );
  }
}

/** 签发当前动作认证使用的 SMS Code Challenge。 */
async function requestSmsCode(): Promise<void> {
  try {
    const challenge = await store.requestSmsCode();
    proof.code = '';
    message.success(
      $t('page.security.verification.smsSent', {
        recipient: challenge.maskedRecipient,
      }),
    );
  } catch {
    message.error(errorText(store.verification.error));
  }
}

function secondsUntil(value: string | undefined): number {
  if (!value) return 0;
  return Math.max(1, Math.ceil((Date.parse(value) - Date.now()) / 1000));
}
</script>

<template>
  <a-modal
    :cancel-text="$t('page.security.common.cancel')"
    :closable="false"
    :confirm-loading="submitting"
    :mask-closable="false"
    :ok-button-props="{ disabled: !canSubmit }"
    :ok-text="$t('page.security.verification.submit')"
    :open="store.verificationOpen"
    :title="title"
    @cancel="store.cancelVerification"
    @ok="submit"
  >
    <div class="space-y-5">
      <a-alert
        :message="
          $t('page.security.verification.remainingFactors', {
            count: remainingFactorCount,
          })
        "
        show-icon
        type="info"
      />

      <a-segmented
        v-model:value="selectedMethod"
        block
        :options="methodOptions"
      />

      <a-form layout="vertical" @finish="submit">
        <a-form-item
          v-if="selectedMethod === AUTHENTICATION_METHOD_CODES.password"
          :label="$t('page.security.verification.password')"
          required
        >
          <a-input-password
            v-model:value="proof.password"
            :aria-label="$t('page.security.verification.password')"
            autocomplete="current-password"
            autofocus
          />
        </a-form-item>

        <a-form-item
          v-else-if="selectedMethod === AUTHENTICATION_METHOD_CODES.totp"
          :label="$t('page.security.verification.totp')"
          required
        >
          <a-input
            v-model:value="proof.code"
            :aria-label="$t('page.security.verification.totp')"
            autocomplete="one-time-code"
            autofocus
            inputmode="numeric"
            :maxlength="6"
          >
            <template #prefix>
              <KeyRound :size="17" aria-hidden="true" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item
          v-else-if="selectedMethod === AUTHENTICATION_METHOD_CODES.smsCode"
          :label="$t('page.security.verification.smsCode')"
          required
        >
          <a-alert
            v-if="store.smsCodeChallenge"
            class="mb-3"
            :message="
              $t('page.security.verification.smsSent', {
                recipient: store.smsCodeChallenge.maskedRecipient,
              })
            "
            show-icon
            type="success"
          />
          <VbenPinInput
            v-model="proof.code"
            :code-length="6"
            :create-text="
              (countdown: number) =>
                countdown > 0
                  ? $t('page.security.verification.smsResendIn', { countdown })
                  : $t('page.security.verification.smsSend')
            "
            :handle-send-code="requestSmsCode"
            :loading="submitting"
            :max-time="smsResendSeconds"
          />
        </a-form-item>

        <a-alert
          v-else-if="selectedMethod === AUTHENTICATION_METHOD_CODES.passkey"
          :message="$t('page.security.verification.passkey')"
          show-icon
          type="info"
        />

        <a-alert
          v-else
          :message="$t('page.security.verification.unsupportedMethod')"
          show-icon
          type="warning"
        />
      </a-form>
    </div>
  </a-modal>
</template>
