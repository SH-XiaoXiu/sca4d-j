<script setup lang="ts">
import { Copy } from "lucide-vue-next";
import { message } from "ant-design-vue";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  open: boolean;
  accountTitle: string;
  codes: string[];
}>();
const emit = defineEmits<{ done: [] }>();
const { t } = useI18n();

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.codes.join("\n"));
    message.success(t("accountSecurity.recoveryCodesCopied"));
  } catch {
    message.error(t("accountSecurity.recoveryCodesCopyFailed"));
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="t('accountSecurity.recoveryCodesTitle')"
    :mask-closable="false"
    :keyboard="false"
    :closable="false"
    :footer="null"
  >
    <a-alert
      type="warning"
      show-icon
      :message="t('accountSecurity.recoveryCodesOnce', { account: accountTitle })"
    />
    <ol class="recovery-code-list">
      <li
        v-for="code in codes"
        :key="code"
      >
        <code>{{ code }}</code>
      </li>
    </ol>
    <div class="modal-actions">
      <a-button @click="copy">
        <Copy :size="16" />{{ t("accountSecurity.copyRecoveryCodes") }}
      </a-button>
      <a-button
        type="primary"
        @click="emit('done')"
      >
        {{ t("common.done") }}
      </a-button>
    </div>
  </a-modal>
</template>

<style scoped>
.recovery-code-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 24px;
  margin: 20px 0;
  padding-left: 28px;
}

.recovery-code-list code {
  color: var(--app-text);
  font-size: 13px;
  letter-spacing: 0;
  white-space: nowrap;
}

.modal-actions {
  display: flex;
  min-height: 44px;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 560px) {
  .recovery-code-list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
