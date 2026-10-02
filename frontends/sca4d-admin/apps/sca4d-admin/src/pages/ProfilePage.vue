<script setup lang="ts">
import { computed, onScopeDispose } from 'vue';

import { useUserStore } from '@vben/stores';

import { LockKeyhole, UserRound } from 'lucide-vue-next';

import { SjFileAvatar, SjPage, SjProfileSettingsLayout } from '#/components/sj';
import ActionVerificationDialog from '#/features/authentication/ActionVerificationDialog.vue';
import NotificationSettingsForm from '#/features/profile/NotificationSettingsForm.vue';
import ProfileDetailsForm from '#/features/profile/ProfileDetailsForm.vue';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';
import { useSecurityStore } from '#/store/security';

const { t } = useSjPresentation();

const security = useSecurityStore();
const userStore = useUserStore();
const profile = useSjControllerState(businessApplication.profile);
const navigation = computed(() => [
  { label: t('profile'), icon: UserRound, to: '/profile' },
  { label: t('authenticationSettings'), icon: LockKeyhole, to: '/security' },
]);
onScopeDispose(security.cancelVerification);
/** 只同步当前 Profile 文件对应的地址，不保存另一个资源的头像。 */
function resolved(url: string | undefined) {
  if (userStore.userInfo)
    userStore.setUserInfo({ ...userStore.userInfo, avatar: url ?? '' });
}
</script>
<template>
  <SjPage :title="t('profile')" auto-content-height mode="detail">
    <SjProfileSettingsLayout
      :navigation="navigation"
      :navigation-label="t('profile')"
    >
      <template #identity>
        <div class="flex items-center gap-3">
          <SjFileAvatar
            :file-id="profile.profile?.avatarFileId"
            :name="
              profile.profile?.displayName || userStore.userInfo?.realName || ''
            "
            :size="48"
            @resolved="resolved"
          />
          <strong>
            {{ profile.profile?.displayName || userStore.userInfo?.realName }}
          </strong>
        </div>
      </template>
      <div class="space-y-6">
        <ProfileDetailsForm />
        <NotificationSettingsForm />
      </div>
    </SjProfileSettingsLayout>
    <ActionVerificationDialog />
  </SjPage>
</template>
