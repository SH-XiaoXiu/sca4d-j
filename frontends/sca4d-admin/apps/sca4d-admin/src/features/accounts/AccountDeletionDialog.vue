<script setup lang="ts">
import type { AccountDeletionOutcome, PlatformAccount } from '@sca4d/admin';

import { computed, onScopeDispose, reactive, ref, watch } from 'vue';

import { AUTHENTICATION_METHOD_CODES } from '@sca4d/admin';
import { message } from 'ant-design-vue';
import { KeyRound } from 'lucide-vue-next';

import { SjDialog } from '#/components/sj';
import { useSjControllerState } from '#/features/sj-controller-state';
import { submitSjAccountDeletionPasskey } from '#/features/sj-files';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const emit = defineEmits<{ /** 删除完成后同步列表。 */ deleted: [] }>();

const { t, errorText } = useSjPresentation();

/** 通过后端 verification 删除已确认的账号，取消后清除认证输入。 */
const target = defineModel<null | PlatformAccount>('account', {
  required: true,
});
const accounts = useSjControllerState(businessApplication.accounts);
const deleteOpen = ref(false);
const deleteSaving = ref(false);
const deleteStage = ref<string>(AUTHENTICATION_METHOD_CODES.password);
const deleteForm = reactive({ code: '', password: '' });
const deletionMethodOptions = computed(() =>
  (accounts.value.pendingDeletion?.verification.availableMethods ?? []).flatMap(
    (method) => {
      let label: null | string = null;
      switch (method) {
        case AUTHENTICATION_METHOD_CODES.passkey: {
          {
            label = t('passkeys');
            // No default
          }
          break;
        }
        case AUTHENTICATION_METHOD_CODES.password: {
          label = t('passwordCredential');
          break;
        }
        case AUTHENTICATION_METHOD_CODES.totp: {
          label = t('totpCredential');
          break;
        }
      }
      return label ? [{ value: method, label }] : [];
    },
  ),
);
watch(target, async (account) => {
  if (!account) return;
  deleteForm.code = '';
  deleteForm.password = '';
  deleteSaving.value = true;
  try {
    const outcome = await businessApplication.accounts.beginDeletion(account);
    if (outcome === 'deleted') finish();
    else {
      selectMethod();
      deleteOpen.value = true;
    }
  } catch (error) {
    message.error(errorText(error));
    target.value = null;
  } finally {
    deleteSaving.value = false;
  }
});
onScopeDispose(cancelDeletion);
/** 按后端允许的方法选取第一个支持的因素。 */
function selectMethod() {
  deleteStage.value = deletionMethodOptions.value[0]?.value ?? '';
}
/** 删除成功后清除所有输入，不再次执行已经由 Controller 完成的列表刷新。 */
function finish() {
  deleteOpen.value = false;
  target.value = null;
  deleteForm.code = '';
  deleteForm.password = '';
  emit('deleted');
  message.success(t('deleteSuccess'));
}
/** 取消认证，不保留秘密或待删除对象。 */
function cancelDeletion() {
  businessApplication.accounts.cancelDeletion();
  deleteOpen.value = false;
  target.value = null;
  deleteForm.code = '';
  deleteForm.password = '';
}
/** 提交当前因素，只有服务端返回已删除才展示最终成功。 */
async function submitDeletion() {
  if (deleteSaving.value || !target.value || !deleteStage.value) return;
  deleteSaving.value = true;
  try {
    let outcome: AccountDeletionOutcome;
    if (deleteStage.value === AUTHENTICATION_METHOD_CODES.totp)
      outcome = await businessApplication.accounts.submitDeletionTotp(
        deleteForm.code.trim(),
      );
    else if (deleteStage.value === AUTHENTICATION_METHOD_CODES.passkey)
      outcome = await submitSjAccountDeletionPasskey();
    else
      outcome = await businessApplication.accounts.submitDeletionPassword(
        deleteForm.password,
      );
    deleteForm.code = '';
    deleteForm.password = '';
    if (outcome === 'factorRequired') selectMethod();
    else finish();
  } catch (error) {
    message.error(errorText(error));
  } finally {
    deleteSaving.value = false;
  }
}
</script>
<template>
  <SjDialog
    v-model:open="deleteOpen"
    :title="t('deleteAccount')"
    :saving="deleteSaving"
    :confirm-text="t('deletePermanently')"
    danger
    @submit="submitDeletion"
    @cancel="cancelDeletion"
  >
    <a-alert type="error" show-icon :message="t('deleteAccountWarning')" />
    <a-segmented
      v-if="deletionMethodOptions.length > 1"
      v-model:value="deleteStage"
      class="mt-5"
      :options="deletionMethodOptions"
    />
    <a-form
      class="mt-5"
      layout="vertical"
      :model="deleteForm"
      @finish="submitDeletion"
    >
      <a-form-item
        v-if="deleteStage === AUTHENTICATION_METHOD_CODES.totp"
        :label="t('totp')"
        required
      >
        <a-input
          v-model:value="deleteForm.code"
          :aria-label="t('totp')"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="6"
          autofocus
        >
          <template #prefix>
            <KeyRound :size="17" aria-hidden="true" />
          </template>
        </a-input>
      </a-form-item>
      <a-form-item
        v-else-if="deleteStage === AUTHENTICATION_METHOD_CODES.password"
        :label="t('currentPassword')"
        required
      >
        <a-input-password
          v-model:value="deleteForm.password"
          :aria-label="t('currentPassword')"
          autocomplete="current-password"
          autofocus
        />
      </a-form-item>
      <a-alert
        v-else
        type="info"
        show-icon
        :message="t('usePasskeyToContinue')"
      />
    </a-form>
  </SjDialog>
</template>
