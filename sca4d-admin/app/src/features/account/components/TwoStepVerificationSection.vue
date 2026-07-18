<script setup lang="ts">
import { formatDateTime, type AccountSecurityView } from "@sca4d/admin-common";
import { RotateCw, ShieldCheck, ShieldOff } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

defineProps<{
  account: AccountSecurityView;
  secondStepAvailable: boolean;
}>();
const emit = defineEmits<{ change: [required: boolean] }>();
const { t } = useI18n();
</script>

<template>
  <section class="security-section">
    <div class="section-heading section-heading--actions">
      <div>
        <ShieldCheck :size="18" />
        <h3>{{ t("accountSecurity.twoFactor") }}</h3>
      </div>
      <div class="second-factor-actions">
        <a-button
          v-if="!account.secondFactorRequired"
          type="primary"
          :disabled="!secondStepAvailable"
          @click="emit('change', true)"
        >
          <ShieldCheck :size="16" />{{ t("accountSecurity.enableSecondFactor") }}
        </a-button>
        <template v-else>
          <a-button
            :disabled="!secondStepAvailable"
            @click="emit('change', true)"
          >
            <RotateCw :size="16" />{{ t("accountSecurity.regenerateRecoveryCodes") }}
          </a-button>
          <a-button
            danger
            :disabled="!secondStepAvailable"
            @click="emit('change', false)"
          >
            <ShieldOff :size="16" />{{ t("accountSecurity.disableSecondFactor") }}
          </a-button>
        </template>
      </div>
    </div>
    <div class="fact-list">
      <div class="fact-row fact-row--single">
        <span>{{ t("accountSecurity.loginRequirement") }}</span>
        <a-tag :color="account.secondFactorRequired ? 'success' : 'default'">
          {{ account.secondFactorRequired ? t("accountSecurity.required") : t("accountSecurity.notRequired") }}
        </a-tag>
      </div>
      <div
        v-if="account.recoveryCodesIssuedAt"
        class="fact-row"
      >
        <span>{{ t("accountSecurity.recoveryCodes") }}</span>
        <strong>{{ formatDateTime(account.recoveryCodesIssuedAt) }}</strong>
        <a-tag :color="account.recoveryCodesRemaining > 2 ? 'success' : 'warning'">
          {{ t("accountSecurity.recoveryCodesRemaining", { count: account.recoveryCodesRemaining }) }}
        </a-tag>
      </div>
    </div>
  </section>
</template>
