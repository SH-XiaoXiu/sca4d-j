<script setup lang="ts">
import { get as getPasskey, type CredentialRequestOptionsJSON } from "@github/webauthn-json";
import type { AuthMethodType } from "@sca4d/admin-common";
import { ArrowLeft, Fingerprint, KeyRound, MailCheck, RotateCw, Smartphone } from "lucide-vue-next";
import { computed, onUnmounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useAuthenticationStore } from "@/stores/authentication";

type SecondStepMethod = Extract<AuthMethodType, "PASSWORD" | "TOTP" | "EMAIL_CODE" | "PASSKEY">;

const props = defineProps<{
  methods: SecondStepMethod[];
  primaryMethod: "PASSWORD" | "PASSKEY" | "OIDC" | "OAUTH2" | "LDAP" | null;
  loading?: boolean;
}>();
const emit = defineEmits<{
  completed: [];
  back: [];
  error: [message: string];
}>();
const authentication = useAuthenticationStore();
const { t } = useI18n();
const webAuthnAvailable = Boolean(window.PublicKeyCredential && navigator.credentials);
const method = ref<SecondStepMethod>("TOTP");
const code = ref("");
const password = ref("");
const resendLoading = ref(false);
const now = ref(Date.now());
const clock = window.setInterval(() => { now.value = Date.now(); }, 1000);
const availableMethods = computed(() => props.methods.filter(
  (candidate) => (candidate !== "PASSKEY" || (webAuthnAvailable && props.primaryMethod !== "PASSKEY"))
    && (candidate !== "PASSWORD" || (props.primaryMethod !== "PASSWORD" && props.primaryMethod !== "LDAP"))
));
const resendSeconds = computed(() => {
  const resendAt = authentication.pendingEmailCode?.resendAt;
  return resendAt ? Math.max(0, Math.ceil((new Date(resendAt).getTime() - now.value) / 1000)) : 0;
});

watch(
  availableMethods,
  (methods) => {
    if (!methods.includes(method.value)) method.value = methods.includes("TOTP") ? "TOTP" : methods[0] ?? "TOTP";
    code.value = "";
    password.value = "";
  },
  { immediate: true }
);
watch(method, () => {
  code.value = "";
  password.value = "";
});
onUnmounted(() => window.clearInterval(clock));

async function complete(): Promise<void> {
  try {
    if (method.value === "PASSWORD") {
      await authentication.completePasswordSecondStep(password.value);
    } else if (method.value === "TOTP") {
      await authentication.completeTotp(code.value);
    } else if (method.value === "PASSKEY") {
      const assertion = await authentication.startPasskeySecondStep();
      const credential = await getPasskey(assertion.options as CredentialRequestOptionsJSON);
      await authentication.completePasskeySecondStep(assertion.assertionId, credential);
    } else {
      await authentication.completeEmailCode(code.value);
    }
    code.value = "";
    password.value = "";
    emit("completed");
  } catch (error) {
    emit("error", error instanceof Error ? error.message : t("login.verificationFailed"));
  }
}

async function sendEmailCode(): Promise<void> {
  resendLoading.value = true;
  try {
    await authentication.startEmailCode();
  } catch (error) {
    emit("error", error instanceof Error ? error.message : t("login.resendFailed"));
  } finally {
    resendLoading.value = false;
  }
}

async function resendEmailCode(): Promise<void> {
  resendLoading.value = true;
  try {
    await authentication.resendEmailCode();
  } catch (error) {
    emit("error", error instanceof Error ? error.message : t("login.resendFailed"));
  } finally {
    resendLoading.value = false;
  }
}
</script>

<template>
  <a-form
    layout="vertical"
    :model="{ code }"
    @finish="complete"
  >
    <a-segmented
      v-if="availableMethods.length > 1"
      v-model:value="method"
      block
      :disabled="loading || resendLoading || Boolean(authentication.pendingEmailCode)"
      :options="availableMethods.map((candidate) => ({
        value: candidate,
        label: candidate === 'TOTP'
          ? t('login.totp')
          : candidate === 'PASSWORD'
            ? t('login.password')
            : candidate === 'PASSKEY'
              ? t('login.passkey')
              : t('login.emailCode')
      }))"
      class="verification-methods"
    />
    <a-alert
      v-if="method === 'EMAIL_CODE' && authentication.pendingEmailCode"
      type="info"
      show-icon
      :message="t('login.codeSent', { recipient: authentication.pendingEmailCode.maskedRecipient })"
      class="verification-status"
    />
    <a-form-item
      v-if="method === 'PASSWORD' || method === 'TOTP' || (method === 'EMAIL_CODE' && authentication.pendingEmailCode)"
      name="code"
      :label="method === 'PASSWORD' ? t('login.password') : method === 'TOTP' ? t('login.totp') : t('login.emailCode')"
      :rules="[{ required: true, message: method === 'PASSWORD' ? t('login.passwordRequired') : method === 'TOTP' ? t('login.totpRequired') : t('login.emailCodeRequired') }]"
    >
      <a-input-password
        v-if="method === 'PASSWORD'"
        v-model:value="password"
        size="large"
        autocomplete="current-password"
        :placeholder="t('login.passwordPlaceholder')"
      >
        <template #prefix>
          <KeyRound :size="17" />
        </template>
      </a-input-password>
      <a-input
        v-else
        v-model:value="code"
        size="large"
        autocomplete="one-time-code"
        inputmode="numeric"
        :maxlength="method === 'TOTP' ? 6 : 32"
        :placeholder="method === 'TOTP' ? t('login.totpPlaceholder') : t('login.emailCodePlaceholder')"
      >
        <template #prefix>
          <Smartphone
            v-if="method === 'TOTP'"
            :size="17"
          />
          <MailCheck
            v-else
            :size="17"
          />
        </template>
      </a-input>
    </a-form-item>
    <a-alert
      v-if="method === 'PASSKEY'"
      type="info"
      show-icon
      :message="t('login.passkeySecondFactorReady')"
      class="verification-status"
    />
    <div class="verification-actions">
      <a-tooltip :title="t('login.backToPassword')">
        <a-button
          class="icon-action"
          :aria-label="t('login.backToPassword')"
          :disabled="loading || resendLoading"
          @click="emit('back')"
        >
          <ArrowLeft :size="18" />
        </a-button>
      </a-tooltip>
      <a-button
        v-if="method === 'EMAIL_CODE' && authentication.pendingEmailCode"
        type="link"
        class="resend-action"
        :loading="resendLoading"
        :disabled="loading || resendLoading || resendSeconds > 0"
        @click="resendEmailCode"
      >
        <RotateCw :size="16" />
        {{ resendSeconds > 0 ? t("login.resendCountdown", { seconds: resendSeconds }) : t("login.resend") }}
      </a-button>
      <a-button
        v-else-if="method === 'EMAIL_CODE'"
        type="primary"
        :loading="resendLoading"
        :disabled="loading || resendLoading"
        @click="sendEmailCode"
      >
        <MailCheck :size="16" />{{ t("login.sendCode") }}
      </a-button>
    </div>
    <a-button
      v-if="method === 'PASSWORD' || method === 'TOTP' || method === 'PASSKEY' || authentication.pendingEmailCode"
      type="primary"
      html-type="submit"
      size="large"
      block
      :loading="loading"
      :disabled="method === 'PASSWORD' ? !password : method !== 'PASSKEY' && !code"
    >
      <Fingerprint
        v-if="method === 'PASSKEY'"
        :size="18"
      />
      {{ method === "PASSKEY" ? t("login.verifyPasskey") : t("login.verify") }}
    </a-button>
  </a-form>
</template>

<style scoped>
.verification-methods,
.verification-status {
  margin-bottom: 18px;
}

.verification-actions {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.icon-action {
  width: 44px;
  min-width: 44px;
  height: 44px;
}

.resend-action {
  min-height: 44px;
}
</style>
