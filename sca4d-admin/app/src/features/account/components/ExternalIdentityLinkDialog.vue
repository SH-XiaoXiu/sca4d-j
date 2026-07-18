<script setup lang="ts">
import type { IdentityProviderProtocol } from "@sca4d/admin-common";
import { Link2 } from "lucide-vue-next";
import { reactive, watch } from "vue";
import { useI18n } from "vue-i18n";

export interface ExternalIdentityLinkRequest {
  protocol: IdentityProviderProtocol;
  providerCode: string;
  username: string;
  password: string;
}

const props = defineProps<{
  open: boolean;
  protocol: IdentityProviderProtocol;
  providers: Array<{ code: string; name: string }>;
  submitting?: boolean;
}>();
const emit = defineEmits<{
  "update:open": [open: boolean];
  submit: [request: ExternalIdentityLinkRequest];
}>();
const { t } = useI18n();
const form = reactive({ providerCode: "", username: "", password: "" });

function reset(): void {
  form.providerCode = props.providers[0]?.code ?? "";
  form.username = "";
  form.password = "";
}

watch(
  () => [props.open, props.protocol, props.providers] as const,
  ([open]) => {
    if (open) reset();
  },
  { immediate: true }
);

function close(): void {
  if (props.submitting) return;
  reset();
  emit("update:open", false);
}

function submit(): void {
  emit("submit", {
    protocol: props.protocol,
    providerCode: form.providerCode,
    username: form.username,
    password: form.password
  });
  form.password = "";
}
</script>

<template>
  <a-modal
    :open="open"
    :title="t('accountSecurity.linkExternalIdentity', { protocol })"
    :footer="null"
    :mask-closable="!submitting"
    :keyboard="!submitting"
    :closable="!submitting"
    @cancel="close"
  >
    <a-form
      layout="vertical"
      :model="form"
      @finish="submit"
    >
      <a-form-item
        name="providerCode"
        :label="t('accountSecurity.identityProvider')"
        :rules="[{ required: true, message: t('accountSecurity.identityProviderRequired') }]"
      >
        <a-select
          v-model:value="form.providerCode"
          :disabled="submitting"
        >
          <a-select-option
            v-for="provider in providers"
            :key="provider.code"
            :value="provider.code"
          >
            {{ provider.name }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <template v-if="protocol === 'LDAP'">
        <a-form-item
          name="username"
          :label="t('accountSecurity.directoryUsername')"
          :rules="[{ required: true, message: t('accountSecurity.directoryUsernameRequired') }]"
        >
          <a-input
            v-model:value="form.username"
            autocomplete="username"
            :disabled="submitting"
          />
        </a-form-item>
        <a-form-item
          name="password"
          :label="t('accountSecurity.directoryPassword')"
          :rules="[{ required: true, message: t('accountSecurity.directoryPasswordRequired') }]"
        >
          <a-input-password
            v-model:value="form.password"
            autocomplete="current-password"
            :disabled="submitting"
          />
        </a-form-item>
      </template>
      <div class="modal-actions">
        <a-button
          :disabled="submitting"
          @click="close"
        >
          {{ t("common.cancel") }}
        </a-button>
        <a-button
          type="primary"
          html-type="submit"
          :loading="submitting"
        >
          <Link2 :size="16" />{{ t("accountSecurity.link") }}
        </a-button>
      </div>
    </a-form>
  </a-modal>
</template>

<style scoped>
.modal-actions {
  display: flex;
  min-height: 44px;
  justify-content: flex-end;
  gap: 8px;
}
</style>
