<script lang="ts" setup>
import type { NotificationItem } from '@vben/layouts';

import { computed, onBeforeUnmount, onMounted } from 'vue';
import { useRouter } from 'vue-router';

import { BasicLayout, Notification, UserDropdown } from '@vben/layouts';
import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { message as notificationMessage } from 'ant-design-vue';

import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { businessApplication } from '#/platform/business-application';
import { useAuthStore } from '#/store';
import { useNotificationStore } from '#/store/notifications';

const userStore = useUserStore();
const authStore = useAuthStore();
const router = useRouter();
const notifications = useNotificationStore();
const inbox = computed(() => notifications.state.messages);
const unreadCount = computed(() => notifications.state.unreadCount);
const { errorText } = useSjPresentation();

const avatar = computed(
  () => userStore.userInfo?.avatar || preferences.app.defaultAvatar,
);
const description = computed(() => userStore.userInfo?.desc ?? '');
const menus = computed(() => [
  {
    handler: () => router.push({ name: 'Profile' }),
    icon: 'lucide:user-round',
    text: $t('page.business.profile'),
  },
  {
    handler: () => router.push({ name: 'Security' }),
    icon: 'lucide:lock-keyhole',
    text: $t('page.business.security'),
  },
]);
const notificationItems = computed<NotificationItem[]>(() =>
  inbox.value.map((item) => ({
    avatar: preferences.app.defaultAvatar,
    date: new Intl.DateTimeFormat(preferences.app.locale, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(item.createdAt)),
    id: item.id,
    isRead: item.readAt !== null,
    message: item.text,
    title: item.title,
  })),
);

onMounted(() => {
  void loadInbox();
  window.addEventListener('sca4d:notification-inbox-changed', loadInbox);
});
onBeforeUnmount(() =>
  window.removeEventListener('sca4d:notification-inbox-changed', loadInbox),
);

/** 读取顶部铃铛显示的最近站内信。 */
async function loadInbox(): Promise<void> {
  try {
    await businessApplication.inbox.load();
  } catch {
    // Controller 保留此前摘要和错误，网络失败不改变未读数量。
  }
}

/** 标记一条站内信已读并刷新未读数量。 */
async function markRead(item: NotificationItem): Promise<void> {
  const message = inbox.value.find((candidate) => candidate.id === item.id);
  if (!message || message.readAt) return;
  try {
    await businessApplication.inbox.markRead(message);
  } catch (error) {
    notificationMessage.error(errorText(error));
  }
}

/** 标记全部站内信已读。 */
async function markAllRead(): Promise<void> {
  try {
    await businessApplication.inbox.markAllRead();
  } catch (error) {
    notificationMessage.error(errorText(error));
  }
}

/** 打开完整站内信页面，并先标记点击项已读。 */
async function openNotification(item: NotificationItem): Promise<void> {
  await markRead(item);
  await router.push({ name: 'Inbox', query: { messageId: String(item.id) } });
}

/** 注销远端 Session，并直接返回登录页。 */
async function handleLogout(): Promise<void> {
  await authStore.logout(false);
}
</script>

<template>
  <BasicLayout @clear-preferences-and-logout="handleLogout">
    <template #notification>
      <Notification
        :clearable="false"
        :dot="unreadCount > 0"
        :notifications="notificationItems"
        :removable="false"
        @make-all="markAllRead"
        @on-click="openNotification"
        @read="markRead"
        @view-all="router.push({ name: 'Inbox' })"
      />
    </template>
    <template #user-dropdown>
      <UserDropdown
        :avatar="avatar"
        :description="description"
        :menus="menus"
        :text="userStore.userInfo?.realName"
        @clear-preferences-and-logout="handleLogout"
        @logout="handleLogout"
      />
    </template>
  </BasicLayout>
</template>
