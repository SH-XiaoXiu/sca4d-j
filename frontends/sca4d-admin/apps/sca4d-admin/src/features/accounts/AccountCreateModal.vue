<script setup lang="ts">
import type { CreateAccountInput } from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { computed, reactive, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import { SjResourceEditor } from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const emit = defineEmits<{ /** 创建完成，调用方同步列表。 */ saved: [] }>();

const { t, errorText } = useSjPresentation();

/** 创建账号的完整表单；初始密码限于打开的工作区。 */
const createOpen = defineModel<boolean>('open', { required: true });
const session = useSjAccess();
const canManage = computed(() => session.value.capabilities.accountsManage);
const createSaving = ref(false);
const createFormRef = ref<FormInstance>();
const createForm = reactive({
  username: '',
  initialPassword: '',
  displayName: '',
  email: '',
  phoneNumber: '',
});
watch(createOpen, (open) => {
  if (!open) {
    createForm.initialPassword = '';
    createFormRef.value?.resetFields();
  }
});
/** 校验完整账号输入，一次提交后关闭并通知列表。 */
async function createAccount() {
  if (createSaving.value || !canManage.value) return;
  await createFormRef.value?.validate();
  createSaving.value = true;
  try {
    const input: CreateAccountInput = {
      username: createForm.username.trim(),
      initialPassword: createForm.initialPassword,
      displayName: createForm.displayName.trim(),
      email: createForm.email.trim() || null,
      phoneNumber: createForm.phoneNumber.trim() || null,
    };
    await businessApplication.accounts.create(input);
    createOpen.value = false;
    emit('saved');
    message.success(t('createSuccess'));
  } catch (error) {
    message.error(errorText(error));
  } finally {
    createSaving.value = false;
  }
}
</script>
<template>
  <SjResourceEditor
    create
    size="xl"
    v-model:open="createOpen"
    :title="t('createAccount')"
    :submit-disabled="!canManage"
    :saving="createSaving"
    @submit="createAccount"
  >
    <a-form ref="createFormRef" layout="vertical" :model="createForm">
      <a-form-item
        name="username"
        :label="t('username')"
        :rules="[{ required: true, message: t('required') }]"
      >
        <a-input v-model:value="createForm.username" autocomplete="username" />
      </a-form-item>
      <a-form-item
        name="initialPassword"
        :label="t('initialPassword')"
        :rules="[
          { required: true, message: t('required') },
          { min: 8, max: 128, message: t('passwordLength') },
        ]"
      >
        <a-input-password
          v-model:value="createForm.initialPassword"
          autocomplete="new-password"
        />
      </a-form-item>
      <a-form-item
        name="displayName"
        :label="t('displayName')"
        :rules="[{ required: true, message: t('required') }]"
      >
        <a-input v-model:value="createForm.displayName" autocomplete="name" />
      </a-form-item>
      <a-form-item
        name="email"
        :label="t('email')"
        :rules="[{ type: 'email', message: t('invalidEmail') }]"
      >
        <a-input v-model:value="createForm.email" autocomplete="email" />
      </a-form-item>
      <a-form-item
        name="phoneNumber"
        :label="t('phoneNumber')"
        :rules="[{ pattern: /^\+[1-9]\d{1,14}$/, message: t('invalidPhone') }]"
      >
        <a-input v-model:value="createForm.phoneNumber" autocomplete="tel" />
      </a-form-item>
    </a-form>
  </SjResourceEditor>
</template>
