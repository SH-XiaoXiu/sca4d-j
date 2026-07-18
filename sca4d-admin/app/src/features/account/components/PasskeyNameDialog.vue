<script setup lang="ts">
import { Fingerprint } from "lucide-vue-next";
import { reactive, watch } from "vue";
import { useI18n } from "vue-i18n";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{
  "update:open": [open: boolean];
  continue: [displayName: string];
}>();
const { t } = useI18n();
const form = reactive({ displayName: "" });

watch(
  () => props.open,
  (open) => {
    if (open) form.displayName = "";
  },
  { immediate: true }
);

function close(): void {
  form.displayName = "";
  emit("update:open", false);
}

function submit(): void {
  const displayName = form.displayName.trim();
  if (!displayName) return;
  emit("continue", displayName);
  form.displayName = "";
}
</script>

<template>
  <a-modal
    :open="open"
    :title="t('accountSecurity.addPasskey')"
    :footer="null"
    @cancel="close"
  >
    <a-form
      :model="form"
      layout="vertical"
      @finish="submit"
    >
      <a-form-item
        name="displayName"
        :label="t('accountSecurity.passkeyName')"
        :rules="[{ required: true, whitespace: true, message: t('accountSecurity.passkeyNameRequired') }]"
      >
        <a-input
          v-model:value="form.displayName"
          :maxlength="128"
          :placeholder="t('accountSecurity.passkeyNamePlaceholder')"
          autocomplete="off"
        >
          <template #prefix>
            <Fingerprint :size="16" />
          </template>
        </a-input>
      </a-form-item>
      <div class="modal-actions">
        <a-button @click="close">
          {{ t("common.cancel") }}
        </a-button>
        <a-button
          type="primary"
          html-type="submit"
          :disabled="!form.displayName.trim()"
        >
          {{ t("common.continue") }}
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
