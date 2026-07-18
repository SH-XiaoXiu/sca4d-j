<script setup lang="ts">
import { get as getPasskey, type CredentialRequestOptionsJSON } from "@github/webauthn-json";
import type { AccountIdentifierView, AuthMethodType } from "@sca4d/admin-common";
import { Fingerprint, Mail, Smartphone } from "lucide-vue-next";
import { message } from "ant-design-vue";
import { computed, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { authenticationApi } from "@/api/client";
import { useAuthenticationStore } from "@/stores/authentication";

export type SecondStepMethod = Extract<AuthMethodType, "PASSWORD" | "EMAIL_CODE" | "TOTP" | "PASSKEY">;

const props = defineProps<{
  open: boolean;
  methods: SecondStepMethod[];
  passwordIdentifier: AccountIdentifierView | null;
  title: string;
  impact: string;
  submitLabel: string;
  danger?: boolean;
  submitting?: boolean;
}>();
const emit = defineEmits<{
  "update:open": [open: boolean];
  verified: [authenticationId: string];
}>();
const { t } = useI18n();
const authenticationStore = useAuthenticationStore();
const verifying = ref(false);
const challenge = ref<{
  authenticationId: string;
  challengeId: string;
  maskedRecipient: string;
} | null>(null);
const form = reactive<{ method: SecondStepMethod; password: string; code: string }>({
  method: "PASSWORD",
  password: "",
  code: ""
});
const busy = computed(() => verifying.value || Boolean(props.submitting));

function reset(): void {
  challenge.value = null;
  form.password = "";
  form.code = "";
  form.method = props.methods[0] ?? "PASSWORD";
}

watch(
  () => props.open,
  (open) => {
    if (open) reset();
  },
  { immediate: true }
);
watch(
  () => props.methods,
  (methods) => {
    if (!methods.includes(form.method)) form.method = methods[0] ?? "PASSWORD";
  },
  { immediate: true }
);
watch(() => form.method, () => {
  form.password = "";
  form.code = "";
  challenge.value = null;
});

function close(): void {
  if (busy.value) return;
  emit("update:open", false);
  reset();
}

/** 为当前登录账号创建邮件验证码二步验证。 */
async function sendEmailCode(): Promise<void> {
  verifying.value = true;
  try {
    const authentication = await authenticationApi.createAuthentication({ purpose: "STEP_UP" });
    const issued = await authenticationApi.createEmailCodeChallenge(authentication.authenticationId);
    challenge.value = {
      authenticationId: authentication.authenticationId,
      challengeId: issued.challengeId,
      maskedRecipient: issued.maskedRecipient
    };
    message.success(t("accountSecurity.codeSent"));
  } catch (error) {
    message.error(error instanceof Error ? error.message : t("accountSecurity.codeSendFailed"));
  } finally {
    verifying.value = false;
  }
}

/** 完成当前操作要求的二步验证，并把一次性 Authentication ID 交给调用页面。 */
async function verify(): Promise<void> {
  verifying.value = true;
  try {
    let process;
    if (form.method === "PASSWORD") {
      if (!props.passwordIdentifier || !form.password) return;
      const authentication = await authenticationApi.createAuthentication({ purpose: "STEP_UP" });
      process = await authenticationApi.passwordProof(authentication.authenticationId, {
        identifierType: props.passwordIdentifier.identifierType,
        identifier: props.passwordIdentifier.value,
        password: form.password
      });
      if (process.activityId !== "completion") {
        throw new Error(`Second-step verification entered unsupported activity: ${process.activityId}`);
      }
    } else if (form.method === "TOTP") {
      if (!form.code) return;
      const authentication = await authenticationApi.createAuthentication({ purpose: "STEP_UP" });
      process = await authenticationApi.totpProof(authentication.authenticationId, { code: form.code });
      if (process.activityId !== "completion") {
        throw new Error(`Second-step verification entered unsupported activity: ${process.activityId}`);
      }
    } else if (form.method === "PASSKEY") {
      const authentication = await authenticationApi.createAuthentication({ purpose: "STEP_UP" });
      const assertion = await authenticationApi.createPasskeyAssertion(authentication.authenticationId);
      const credential = await getPasskey(assertion.options as CredentialRequestOptionsJSON);
      process = await authenticationApi.passkeyProof(authentication.authenticationId, {
        assertionId: assertion.assertionId,
        credential
      });
      if (process.activityId !== "completion") {
        throw new Error(`Second-step verification entered unsupported activity: ${process.activityId}`);
      }
    } else {
      if (!challenge.value || !form.code) return;
      process = await authenticationApi.emailCodeProof(challenge.value.authenticationId, {
        challengeId: challenge.value.challengeId,
        code: form.code
      });
      if (process.activityId !== "completion") {
        throw new Error(`Second-step verification entered unsupported activity: ${process.activityId}`);
      }
    }
    form.password = "";
    form.code = "";
    authenticationStore.rememberStepUp(process);
    emit("verified", process.authenticationId);
  } catch (error) {
    message.error(error instanceof Error ? error.message : t("accountSecurity.secondFactorChangeFailed"));
  } finally {
    verifying.value = false;
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="title"
    :mask-closable="false"
    :keyboard="!busy"
    :closable="!busy"
    :footer="null"
    @cancel="close"
  >
    <a-alert
      v-if="danger"
      type="warning"
      show-icon
      :message="t('accountSecurity.sensitiveActionWarning')"
      :description="impact"
      class="sensitive-action-warning"
    />
    <p
      v-else
      class="second-step-impact"
    >
      {{ impact }}
    </p>
    <a-segmented
      v-model:value="form.method"
      block
      :disabled="busy || Boolean(challenge)"
      :options="methods.map((method) => ({
        value: method,
        label: method === 'PASSWORD'
          ? t('accountSecurity.password')
          : method === 'TOTP'
            ? t('accountSecurity.totp')
            : method === 'PASSKEY'
              ? t('accountSecurity.passkey')
              : t('accountSecurity.emailCode')
      }))"
    />

    <a-form
      v-if="form.method === 'PASSWORD'"
      :model="form"
      layout="vertical"
      class="second-step-form"
      @finish="verify"
    >
      <a-form-item
        name="password"
        :label="t('accountSecurity.password')"
        :rules="[{ required: true, message: t('accountSecurity.passwordRequired') }]"
      >
        <a-input-password
          v-model:value="form.password"
          autocomplete="current-password"
          :placeholder="t('accountSecurity.passwordPlaceholder')"
          :disabled="busy"
        />
      </a-form-item>
      <div class="modal-actions">
        <a-button
          :disabled="busy"
          @click="close"
        >
          {{ t("common.cancel") }}
        </a-button>
        <a-button
          type="primary"
          :danger="danger"
          html-type="submit"
          :loading="busy"
        >
          {{ submitLabel }}
        </a-button>
      </div>
    </a-form>

    <a-form
      v-else-if="form.method === 'TOTP'"
      :model="form"
      layout="vertical"
      class="second-step-form"
      @finish="verify"
    >
      <a-form-item
        name="code"
        :label="t('accountSecurity.totpCode')"
        :rules="[{ required: true, message: t('accountSecurity.totpCodeRequired') }]"
      >
        <a-input
          v-model:value="form.code"
          inputmode="numeric"
          autocomplete="one-time-code"
          :maxlength="6"
          :placeholder="t('accountSecurity.totpCodePlaceholder')"
          :disabled="busy"
        >
          <template #prefix>
            <Smartphone :size="16" />
          </template>
        </a-input>
      </a-form-item>
      <div class="modal-actions">
        <a-button
          :disabled="busy"
          @click="close"
        >
          {{ t("common.cancel") }}
        </a-button>
        <a-button
          type="primary"
          :danger="danger"
          html-type="submit"
          :loading="busy"
        >
          {{ submitLabel }}
        </a-button>
      </div>
    </a-form>

    <div
      v-else-if="form.method === 'PASSKEY'"
      class="second-step-form"
    >
      <a-alert
        type="info"
        show-icon
        :message="t('accountSecurity.passkeySecondStepReady')"
      />
      <div class="modal-actions">
        <a-button
          :disabled="busy"
          @click="close"
        >
          {{ t("common.cancel") }}
        </a-button>
        <a-button
          type="primary"
          :danger="danger"
          :loading="busy"
          @click="verify"
        >
          <Fingerprint :size="16" />{{ submitLabel }}
        </a-button>
      </div>
    </div>

    <div
      v-else
      class="second-step-form"
    >
      <a-alert
        v-if="challenge"
        type="info"
        show-icon
        :message="t('accountSecurity.codeRecipient', { recipient: challenge.maskedRecipient })"
      />
      <a-form
        v-if="challenge"
        :model="form"
        layout="vertical"
        @finish="verify"
      >
        <a-form-item
          name="code"
          :label="t('accountSecurity.emailCode')"
          :rules="[{ required: true, message: t('accountSecurity.codeRequired') }]"
        >
          <a-input
            v-model:value="form.code"
            inputmode="numeric"
            autocomplete="one-time-code"
            :maxlength="32"
            :placeholder="t('accountSecurity.codePlaceholder')"
            :disabled="busy"
          />
        </a-form-item>
        <div class="modal-actions">
          <a-button
            :disabled="busy"
            @click="close"
          >
            {{ t("common.cancel") }}
          </a-button>
          <a-button
            type="primary"
            :danger="danger"
            html-type="submit"
            :loading="busy"
          >
            {{ submitLabel }}
          </a-button>
        </div>
      </a-form>
      <div
        v-else
        class="modal-actions"
      >
        <a-button
          :disabled="busy"
          @click="close"
        >
          {{ t("common.cancel") }}
        </a-button>
        <a-button
          type="primary"
          :loading="busy"
          @click="sendEmailCode"
        >
          <Mail :size="16" />{{ t("accountSecurity.sendCode") }}
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.second-step-impact {
  margin: 0 0 18px;
  color: var(--app-text-secondary);
  line-height: 1.6;
}

.sensitive-action-warning {
  margin-bottom: 18px;
}

.second-step-form {
  display: grid;
  gap: 16px;
  margin-top: 20px;
}

.modal-actions {
  display: flex;
  min-height: 44px;
  justify-content: flex-end;
  gap: 8px;
}
</style>
