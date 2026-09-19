<script lang="ts" setup>
import type { NotificationItem } from '@vben/layouts';
import type { InboxMessage } from '#/api/core/notifications';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { BasicLayout, Notification, UserDropdown } from '@vben/layouts';
import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { $t } from '#/locales';
import {
  getInboxApi,
  markAllInboxReadApi,
  markInboxReadApi,
} from '#/api/core/notifications';
import { useAuthStore } from '#/store';

const userStore = useUserStore();
const authStore = useAuthStore();
const router = useRouter();
const inbox = ref<readonly InboxMessage[]>([]);
const unreadCount = ref(0);

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
    const page = await getInboxApi(undefined, 20);
    inbox.value = page.messages;
    unreadCount.value = page.unreadCount;
  } catch {
    inbox.value = [];
    unreadCount.value = 0;
  }
}

/** 标记一条站内信已读并刷新未读数量。 */
async function markRead(item: NotificationItem): Promise<void> {
  const message = inbox.value.find((candidate) => candidate.id === item.id);
  if (!message || message.readAt) return;
  await markInboxReadApi(message);
  await loadInbox();
}

/** 标记全部站内信已读。 */
async function markAllRead(): Promise<void> {
  await markAllInboxReadApi();
  await loadInbox();
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
