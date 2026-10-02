<script setup lang="ts">
import type { AuthenticationProofSubmission } from './proof';

import { computed, ref } from 'vue';

import { AUTHENTICATION_METHOD_CODES as METHODS } from '@sca4d/admin';

import { SjDialog } from '#/components/sj';
import { $t } from '#/locales';
import { useSecurityStore } from '#/store/security';

import AuthenticationProofPanel from './AuthenticationProofPanel.vue';
import { securityActionLabel } from './presentation';

/** 当前受保护动作的因素输入与取消入口；具体写入继续由安全 Store 协调。 */
const store = useSecurityStore();
const proofPanel = ref<InstanceType<typeof AuthenticationProofPanel>>();
const title = computed(() => securityActionLabel(store.pendingAction));
const methods = computed(
  () => store.verification.verification?.availableMethods ?? [],
);
const notices = computed(() => ({ [METHODS.smsCode]: store.smsCodeChallenge }));
/** 提交一个因素，满足策略后安全 Store 继续原动作。 */
async function submit(proof: AuthenticationProofSubmission) {
  switch (proof.methodCode) {
    case METHODS.passkey: {
      {
        await store.submitPasskey();
        // No default
      }
      break;
    }
    case METHODS.password: {
      await store.submitPassword(proof.password);
      break;
    }
    case METHODS.smsCode: {
      await store.submitSmsCode(proof.code);
      break;
    }
    case METHODS.totp: {
      await store.submitTotp(proof.code);
      break;
    }
  }
}
</script>
<template>
  <SjDialog
    :open="store.verificationOpen"
    :title="title"
    :saving="proofPanel?.submitting"
    :submit-disabled="!proofPanel?.canSubmit"
    :confirm-text="$t('page.security.verification.submit')"
    @update:open="
      (open) => {
        if (!open) store.cancelVerification();
      }
    "
    @cancel="store.cancelVerification"
    @submit="proofPanel?.submit()"
  >
    <a-alert
      class="mb-4"
      show-icon
      type="info"
      :message="
        $t('page.security.verification.remainingFactors', {
          count: store.verification.verification?.remainingFactorCount ?? 0,
        })
      "
    />
    <AuthenticationProofPanel
      ref="proofPanel"
      :methods="methods"
      :operation-id="store.verification.verification?.verificationId"
      :passkey-supported="store.passkeySupported"
      :notices="notices"
      :send-code="() => store.requestSmsCode()"
      :submit-proof="submit"
      :show-submit="false"
    />
  </SjDialog>
</template>
