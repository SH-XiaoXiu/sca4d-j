<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';
import { LockKeyhole, UserRound } from 'lucide-vue-next';

import { SjPage, SjProfileSettingsLayout, SjTabs } from '#/components/sj';
import ActionVerificationDialog from '#/features/authentication/ActionVerificationDialog.vue';
import AuthenticationMethodsTab from '#/features/authentication/AuthenticationMethodsTab.vue';
import AuthenticationSettingsTab from '#/features/authentication/AuthenticationSettingsTab.vue';
import PasswordChangeTab from '#/features/authentication/PasswordChangeTab.vue';
import { securityActionSuccessText } from '#/features/authentication/presentation';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { useSecurityStore } from '#/store/security';

const { t } = useSjPresentation();

const navigation = computed(() => [
  { label: t('profile'), icon: UserRound, to: '/profile' },
  { label: t('authenticationSettings'), icon: LockKeyhole, to: '/security' },
]);
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
  <SjPage :title="$t('page.security.title')" auto-content-height mode="detail">
    <SjProfileSettingsLayout
      :navigation="navigation"
      :navigation-label="$t('page.security.title')"
    >
      <template #identity>
        <strong>{{ displayName }}</strong>
      </template>
      <SjTabs v-model:active-key="activeTab">
        <a-tab-pane v-for="tab in tabs" :key="tab.value" :tab="tab.label">
          <AuthenticationMethodsTab
            v-if="tab.value === 'methods'"
            :display-name="displayName"
          />
          <AuthenticationSettingsTab v-else-if="tab.value === 'settings'" />
          <PasswordChangeTab v-else />
        </a-tab-pane>
      </SjTabs>
    </SjProfileSettingsLayout>
    <ActionVerificationDialog />
  </SjPage>
</template>
