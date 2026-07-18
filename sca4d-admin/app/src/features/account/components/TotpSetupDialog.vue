<script setup lang="ts">
import { Smartphone } from "lucide-vue-next";
import { computed, reactive, watch } from "vue";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  open: boolean;
  replacing: boolean;
  qrUrl: string | null;
  submitting?: boolean;
}>();
const emit = defineEmits<{
  close: [];
  activate: [code: string];
}>();
const { t } = useI18n();
const form = reactive({ code: "" });
const activationReady = computed(() => /^\d{6}$/.test(form.code));

watch(
  () => props.open,
  (open) => {
    if (open) form.code = "";
  },
  { immediate: true }
);

function close(): void {
  if (!props.submitting) emit("close");
}

function activate(): void {
  if (!activationReady.value) return;
  emit("activate", form.code);
}
</script>

<template>
  <a-modal
    :open="open"
    :title="replacing ? t('accountSecurity.replaceTotp') : t('accountSecurity.setupTotp')"
    :mask-closable="false"
    :keyboard="!submitting"
    :closable="!submitting"
    :footer="null"
    @cancel="close"
  >
    <div class="totp-setup">
      <img
        v-if="qrUrl"
        :src="qrUrl"
        :alt="t('accountSecurity.totpQrCode')"
        class="totp-qr-code"
      />
      <a-form
        :model="form"
        layout="vertical"
        @finish="activate"
      >
        <a-form-item
          name="code"
          :label="t('accountSecurity.totpCode')"
          :rules="[
            { required: true, message: t('accountSecurity.totpCodeRequired') },
            { pattern: /^\d{6}$/, message: t('accountSecurity.totpCodeRequired') }
          ]"
        >
          <a-input
            v-model:value="form.code"
            inputmode="numeric"
            autocomplete="one-time-code"
            :maxlength="6"
            :placeholder="t('accountSecurity.totpCodePlaceholder')"
            :disabled="submitting"
          >
            <template #prefix>
              <Smartphone :size="16" />
            </template>
          </a-input>
        </a-form-item>
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
            :disabled="!activationReady"
          >
            {{ t("accountSecurity.activateTotp") }}
          </a-button>
        </div>
      </a-form>
    </div>
  </a-modal>
</template>

<style scoped>
.totp-setup {
  display: grid;
  gap: 20px;
}

.totp-qr-code {
  width: min(320px, 100%);
  aspect-ratio: 1;
  justify-self: center;
  object-fit: contain;
}

.modal-actions {
  display: flex;
  min-height: 44px;
  justify-content: flex-end;
  gap: 8px;
}
</style>
