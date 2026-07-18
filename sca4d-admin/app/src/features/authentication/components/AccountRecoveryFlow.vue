<script setup lang="ts">
import {
  isApiError,
  type AccountIdentifierType,
  type AuthenticationScopeSelection
} from "@sca4d/admin-common";
import { ArrowLeft, CheckCircle2, KeyRound, LockKeyhole, UserRound } from "lucide-vue-next";
import { computed, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import { authenticationApi } from "@/api/client";

const props = defineProps<{
  scope: AuthenticationScopeSelection;
  initialIdentifierType: AccountIdentifierType;
  initialIdentifier: string;
}>();
const emit = defineEmits<{ back: [] }>();
const { t } = useI18n();
const step = ref<"RECOVERY_CODE" | "NEW_PASSWORD" | "COMPLETE">("RECOVERY_CODE");
const loading = ref(false);
const errorMessage = ref("");
const authenticationId = ref<string | null>(null);
const form = reactive({
  identifierType: props.initialIdentifierType,
  identifier: props.initialIdentifier,
  recoveryCode: "",
  password: "",
  confirmPassword: ""
});
const title = computed(() => step.value === "RECOVERY_CODE"
  ? t("login.recoveryTitle")
  : step.value === "NEW_PASSWORD"
    ? t("login.newPasswordTitle")
    : t("login.recoveryCompleteTitle"));
const description = computed(() => step.value === "RECOVERY_CODE"
  ? t("login.recoveryDescription")
  : step.value === "NEW_PASSWORD"
    ? t("login.newPasswordDescription")
    : t("login.recoveryCompleteDescription"));

async function submitRecoveryCode(): Promise<void> {
  errorMessage.value = "";
  loading.value = true;
  try {
    const recovery = await authenticationApi.createAuthentication({
      purpose: "RECOVERY",
      ...props.scope
    });
    const verified = await authenticationApi.recoveryCodeProof(recovery.authenticationId, {
      identifierType: form.identifierType,
      identifier: form.identifier,
      recoveryCode: form.recoveryCode
    });
    if (verified.activityId !== "completion") {
      throw new Error(`Authentication entered unsupported activity: ${verified.activityId}`);
    }
    authenticationId.value = recovery.authenticationId;
    step.value = "NEW_PASSWORD";
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : t("login.recoveryFailed");
  } finally {
    form.recoveryCode = "";
    loading.value = false;
  }
}

async function submitPasswordReplacement(): Promise<void> {
  if (!authenticationId.value) {
    step.value = "RECOVERY_CODE";
    return;
  }
  errorMessage.value = "";
  loading.value = true;
  try {
    await authenticationApi.replacePassword(authenticationId.value, { password: form.password });
    authenticationId.value = null;
    step.value = "COMPLETE";
  } catch (error) {
    errorMessage.value = isApiError(error) ? error.message : t("login.passwordReplacementFailed");
    authenticationId.value = null;
    step.value = "RECOVERY_CODE";
  } finally {
    form.password = "";
    form.confirmPassword = "";
    loading.value = false;
  }
}
</script>

<template>
  <div class="recovery-flow">
    <header class="recovery-heading">
      <h1 id="account-recovery-title">
        {{ title }}
      </h1>
      <p>{{ description }}</p>
    </header>
    <a-alert
      v-if="errorMessage"
      type="error"
      show-icon
      :message="errorMessage"
      class="recovery-error"
    />
    <a-form
      v-if="step === 'RECOVERY_CODE'"
      layout="vertical"
      :model="form"
      @finish="submitRecoveryCode"
    >
      <a-form-item :label="t('login.accountType')">
        <a-segmented
          v-model:value="form.identifierType"
          block
          :disabled="loading"
          :options="[
            { value: 'USERNAME', label: t('login.username') },
            { value: 'EMAIL', label: t('login.email') }
          ]"
        />
      </a-form-item>
      <a-form-item
        :label="t('login.account')"
        name="identifier"
        :rules="[{ required: true, message: t('login.accountRequired') }]"
      >
        <a-input
          v-model:value="form.identifier"
          size="large"
          autocomplete="username"
          :placeholder="t('login.accountPlaceholder')"
          :disabled="loading"
        >
          <template #prefix>
            <UserRound :size="17" />
          </template>
        </a-input>
      </a-form-item>
      <a-form-item
        :label="t('login.recoveryCode')"
        name="recoveryCode"
        :rules="[{ required: true, message: t('login.recoveryCodeRequired') }]"
      >
        <a-input
          v-model:value="form.recoveryCode"
          size="large"
          autocomplete="one-time-code"
          :maxlength="32"
          :placeholder="t('login.recoveryCodePlaceholder')"
          :disabled="loading"
        >
          <template #prefix>
            <KeyRound :size="17" />
          </template>
        </a-input>
      </a-form-item>
      <div class="recovery-actions">
        <a-tooltip :title="t('login.backToLogin')">
          <a-button
            class="icon-action"
            :aria-label="t('login.backToLogin')"
            :disabled="loading"
            @click="emit('back')"
          >
            <ArrowLeft :size="18" />
          </a-button>
        </a-tooltip>
      </div>
      <a-button
        type="primary"
        html-type="submit"
        size="large"
        block
        :loading="loading"
      >
        {{ t("login.verifyRecoveryCode") }}
      </a-button>
    </a-form>
    <a-form
      v-else-if="step === 'NEW_PASSWORD'"
      layout="vertical"
      :model="form"
      @finish="submitPasswordReplacement"
    >
      <a-form-item
        :label="t('login.newPassword')"
        name="password"
        :rules="[{ required: true, message: t('login.newPasswordRequired') }]"
      >
        <a-input-password
          v-model:value="form.password"
          size="large"
          autocomplete="new-password"
          :placeholder="t('login.newPasswordPlaceholder')"
          :disabled="loading"
        >
          <template #prefix>
            <LockKeyhole :size="17" />
          </template>
        </a-input-password>
      </a-form-item>
      <a-form-item
        :label="t('login.confirmPassword')"
        name="confirmPassword"
        :rules="[
          { required: true, message: t('login.confirmPasswordRequired') },
          {
            validator: async () => {
              if (form.confirmPassword !== form.password) throw new Error(t('login.passwordMismatch'));
            },
            trigger: 'change'
          }
        ]"
      >
        <a-input-password
          v-model:value="form.confirmPassword"
          size="large"
          autocomplete="new-password"
          :placeholder="t('login.confirmPasswordPlaceholder')"
          :disabled="loading"
        >
          <template #prefix>
            <LockKeyhole :size="17" />
          </template>
        </a-input-password>
      </a-form-item>
      <div class="recovery-actions">
        <a-tooltip :title="t('login.backToLogin')">
          <a-button
            class="icon-action"
            :aria-label="t('login.backToLogin')"
            :disabled="loading"
            @click="emit('back')"
          >
            <ArrowLeft :size="18" />
          </a-button>
        </a-tooltip>
      </div>
      <a-button
        type="primary"
        html-type="submit"
        size="large"
        block
        :loading="loading"
      >
        {{ t("login.replacePassword") }}
      </a-button>
    </a-form>
    <div
      v-else
      class="recovery-complete"
    >
      <CheckCircle2
        :size="34"
        aria-hidden="true"
      />
      <a-button
        type="primary"
        size="large"
        block
        @click="emit('back')"
      >
        {{ t("login.backToLogin") }}
      </a-button>
    </div>
  </div>
</template>

<style scoped>
.recovery-heading {
  margin: 28px 0 22px;
}

.recovery-heading h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.recovery-heading p {
  margin: 7px 0 0;
  color: var(--app-text-secondary);
}

.recovery-error {
  margin-bottom: 18px;
}

.recovery-actions {
  display: flex;
  min-height: 44px;
  align-items: center;
  margin: -4px 0 18px;
}

.icon-action {
  width: 44px;
  min-width: 44px;
  height: 44px;
  padding: 0;
}

.recovery-complete {
  display: grid;
  gap: 24px;
  justify-items: center;
  color: var(--app-success);
}
</style>
