<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { Profile } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import ActionVerificationDialog from '#/features/authentication/ActionVerificationDialog.vue';
import AuthenticationMethodsTab from '#/features/authentication/AuthenticationMethodsTab.vue';
import AuthenticationSettingsTab from '#/features/authentication/AuthenticationSettingsTab.vue';
import PasswordChangeTab from '#/features/authentication/PasswordChangeTab.vue';
import { securityActionSuccessText } from '#/features/authentication/presentation';
import { $t } from '#/locales';
import { useSecurityStore } from '#/store/security';

const store = useSecurityStore();
const userStore = useUserStore();
const activeTab = ref('methods');
const tabs = computed(() => [
  {
    label: $t('page.security.tabs.methods'),
    value: 'methods',
  },
  {
    label: $t('page.security.tabs.settings'),
    value: 'settings',
  },
  {
    label: $t('page.security.tabs.password'),
    value: 'password',
  },
]);
const displayName = computed(
  () =>
    userStore.userInfo?.realName || userStore.userInfo?.username || 'Account',
);

onMounted(() => {
  void store.load();
});
onBeforeUnmount(store.cancelVerification);

watch(
  () => store.completedAction,
  (action) => {
    if (!action) return;
    message.success(securityActionSuccessText(action));
    store.consumeCompletedAction();
  },
);
</script>

<template>
  <div class="size-full">
    <Profile
      v-model:model-value="activeTab"
      :tabs="tabs"
      :title="$t('page.security.title')"
      :user-info="userStore.userInfo"
    >
      <template #content>
        <AuthenticationMethodsTab
          v-if="activeTab === 'methods'"
          :display-name="displayName"
        />
        <AuthenticationSettingsTab v-else-if="activeTab === 'settings'" />
        <PasswordChangeTab v-else />
      </template>
    </Profile>
    <ActionVerificationDialog />
  </div>
</template>
