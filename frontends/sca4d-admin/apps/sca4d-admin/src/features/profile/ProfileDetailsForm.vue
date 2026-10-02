<script setup lang="ts">
import type { FormInstance } from 'ant-design-vue';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';
import { Save } from 'lucide-vue-next';

import { SjAvatarEditor, SjResourceState, SjSection } from '#/components/sj';
import { securityActionSuccessText } from '#/features/authentication/presentation';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjDraftGuard } from '#/features/sj-draft-guard';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';
import { useSecurityStore } from '#/store/security';

const { t, errorText } = useSjPresentation();

/** 当前账号的资料表单；头像变化不会覆盖其他字段的未保存输入。 */
const profile = useSjControllerState(businessApplication.profile);
const session = useSjAccess();
const security = useSecurityStore();
const userStore = useUserStore();
const formRef = ref<FormInstance>();
const form = reactive({ displayName: '', email: '', phoneNumber: '' });
const baseline = ref('');
const saving = ref(false);
const dirty = computed(
  () => Boolean(baseline.value) && JSON.stringify(form) !== baseline.value,
);
useSjDraftGuard(
  () => dirty.value,
  () => saving.value,
);
watch(
  () => profile.value.profile,
  (value) => {
    if (!value) return;
    if (!dirty.value) {
      form.displayName = value.displayName;
      form.email = value.email ?? '';
      form.phoneNumber = value.phoneNumber ?? '';
      baseline.value = JSON.stringify(form);
    }
    if (userStore.userInfo)
      userStore.setUserInfo({
        ...userStore.userInfo,
        realName: value.displayName,
      });
  },
  { immediate: true },
);
watch(
  () => security.completedAction,
  (action) => {
    if (action === 'phone.verify') {
      message.success(securityActionSuccessText(action));
      security.consumeCompletedAction();
    }
  },
);
onMounted(() => {
  void load();
});
/** 读取资料，失败仍保留用户可继续编辑的草稿。 */
async function load() {
  try {
    await businessApplication.profile.load();
  } catch {
    /* SjResourceState 展示 Controller 的错误。 */
  }
}
/** 校验并完整保存资料，写入成功后以实际输入建立新基线。 */
async function save() {
  if (saving.value) return;
  await formRef.value?.validate();
  saving.value = true;
  try {
    await businessApplication.profile.save(
      form.displayName.trim(),
      form.email.trim() || null,
      form.phoneNumber.trim() || null,
    );
    baseline.value = JSON.stringify(form);
    message.success(t('saveSuccess'));
  } catch (error) {
    message.error(errorText(error));
  } finally {
    saving.value = false;
  }
}
/** 只有当前输入已经保存为服务器手机号时才进入 SMS 动作认证。 */
async function verifyPhone() {
  try {
    await security.verifyPhone();
  } catch (error) {
    message.error(errorText(error));
  }
}
</script>
<template>
  <SjSection :title="t('profile')" :divided="false">
    <SjResourceState
      :phase="profile.phase"
      :error="profile.error"
      :has-data="Boolean(profile.profile)"
      @retry="load"
    >
      <SjAvatarEditor
        v-if="profile.profile && session.session"
        :account-id="session.session.account.accountId"
        :file-id="profile.profile.avatarFileId"
        :name="profile.profile.displayName"
        editable
        :replace="(id) => businessApplication.profile.replaceAvatar(id)"
        :remove="() => businessApplication.profile.deleteAvatar()"
      />
      <a-form
        ref="formRef"
        class="max-w-3xl"
        layout="vertical"
        :model="form"
        @finish="save"
      >
        <a-form-item
          name="displayName"
          :label="t('displayName')"
          :rules="[{ required: true, message: t('required') }]"
        >
          <a-input v-model:value="form.displayName" autocomplete="name" />
        </a-form-item>
        <a-form-item
          name="email"
          :label="t('email')"
          :rules="[{ type: 'email', message: t('invalidEmail') }]"
        >
          <a-input v-model:value="form.email" autocomplete="email" />
        </a-form-item>
        <a-form-item
          name="phoneNumber"
          :label="t('phoneNumber')"
          :rules="[
            { pattern: /^\+[1-9]\d{1,14}$/, message: t('invalidPhone') },
          ]"
        >
          <div class="flex gap-2">
            <a-input v-model:value="form.phoneNumber" autocomplete="tel" />
            <a-button
              :disabled="
                !form.phoneNumber.trim() ||
                form.phoneNumber.trim() !== profile.profile?.phoneNumber
              "
              @click="verifyPhone"
            >
              {{ t('verifyPhone') }}
            </a-button>
          </div>
        </a-form-item>
        <a-button type="primary" html-type="submit" :loading="saving">
          <Save :size="17" aria-hidden="true" />
          {{ t('save') }}
        </a-button>
      </a-form>
    </SjResourceState>
  </SjSection>
</template>
