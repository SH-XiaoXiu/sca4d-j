<script setup lang="ts">
import type { InboxMessage } from '#/api/core/notifications';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import { CheckCheck, RefreshCw } from 'lucide-vue-next';

import {
  getInboxApi,
  markAllInboxReadApi,
  markInboxReadApi,
} from '#/api/core/notifications';
import { $t } from '#/locales';

const messages = ref<readonly InboxMessage[]>([]);
const unreadCount = ref(0);
const loading = ref(false);
const beforeId = ref<string>();
const hasMore = computed(
  () => messages.value.length > 0 && beforeId.value !== undefined,
);

onMounted(() => void reload());

/** 从第一页重新读取站内信。 */
async function reload(): Promise<void> {
  loading.value = true;
  try {
    const page = await getInboxApi(undefined, 50);
    messages.value = page.messages;
    unreadCount.value = page.unreadCount;
    beforeId.value =
      page.messages.length === 50 ? page.messages.at(-1)?.id : undefined;
  } finally {
    loading.value = false;
  }
}

/** 追加更早的站内信页。 */
async function loadMore(): Promise<void> {
  if (!beforeId.value) return;
  const page = await getInboxApi(beforeId.value, 50);
  messages.value = [...messages.value, ...page.messages];
  unreadCount.value = page.unreadCount;
  beforeId.value =
    page.messages.length === 50 ? page.messages.at(-1)?.id : undefined;
}

/** 首次展开未读站内信时保存已读状态。 */
async function read(item: InboxMessage): Promise<void> {
  if (item.readAt) return;
  await markInboxReadApi(item);
  await reload();
}

/** 展开一条站内信时读取对应消息。 */
function panelChanged(key: string | string[]): void {
  const selected = Array.isArray(key) ? key[0] : key;
  const item = messages.value.find(
    (value) => String(value.id) === String(selected),
  );
  if (item) void read(item);
}

/** 把全部站内信标记已读。 */
async function readAll(): Promise<void> {
  await markAllInboxReadApi();
  await reload();
  message.success(t('markedAllRead'));
}

function t(key: string, values?: Record<string, unknown>): string {
  return values ? $t(`page.inbox.${key}`, values) : $t(`page.inbox.${key}`);
}
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 flex items-center justify-between gap-3">
      <div>
        <h2 class="text-base font-medium">{{ t('title') }}</h2>
        <p class="text-muted-foreground text-sm">
          {{ t('unread', { count: unreadCount }) }}
        </p>
      </div>
      <div class="flex gap-2">
        <a-button @click="reload"
          ><RefreshCw :size="17" />{{ t('refresh') }}</a-button
        >
        <a-button :disabled="unreadCount === 0" @click="readAll"
          ><CheckCheck :size="17" />{{ t('markAllRead') }}</a-button
        >
      </div>
    </div>
    <a-spin :spinning="loading">
      <a-empty v-if="messages.length === 0" :description="t('empty')" />
      <a-collapse v-else accordion @change="panelChanged">
        <a-collapse-panel v-for="item in messages" :key="item.id">
          <template #header>
            <div class="flex min-w-0 flex-1 items-center gap-2">
              <span
                v-if="!item.readAt"
                class="size-2 shrink-0 rounded-full bg-primary"
              ></span>
              <strong class="truncate">{{ item.title }}</strong>
              <span class="text-muted-foreground ml-auto text-xs">{{
                new Date(item.createdAt).toLocaleString()
              }}</span>
            </div>
          </template>
          <div class="prose prose-sm max-w-none" v-html="item.html"></div>
        </a-collapse-panel>
      </a-collapse>
      <a-button v-if="hasMore" class="mt-4 w-full" @click="loadMore">{{
        t('loadMore')
      }}</a-button>
    </a-spin>
  </Page>
</template>
