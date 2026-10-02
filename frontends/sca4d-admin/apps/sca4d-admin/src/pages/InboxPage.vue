<script setup lang="ts">
import type { InboxMessage } from '@sca4d/admin';

import { computed, onMounted, onScopeDispose, ref } from 'vue';
import { useRoute } from 'vue-router';

import { message } from 'ant-design-vue';
import { CheckCheck, RefreshCw } from 'lucide-vue-next';

import { SjPage, SjResourceState } from '#/components/sj';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { businessApplication } from '#/platform/business-application';

const { dateTime, errorText } = useSjPresentation();

const inbox = businessApplication.createInbox(50);
const state = useSjControllerState(inbox);
const route = useRoute();
const busy = ref(new Set<string>());
const active = ref(
  typeof route.query.messageId === 'string' ? route.query.messageId : undefined,
);
const hasMore = computed(() => Boolean(state.value.beforeId));
onMounted(() => {
  void load();
  window.addEventListener('sca4d:notification-inbox-changed', load);
});
onScopeDispose(() => {
  window.removeEventListener('sca4d:notification-inbox-changed', load);
  inbox.resetState();
});
/** 只读取当前列表；摘要由另一个 Controller 负责。 */
async function load() {
  try {
    await inbox.load();
  } catch {
    /* SjResourceState 展示实际失败。 */
  }
}
/** 追加有界游标页，失败保留已经读取的消息。 */
async function loadMore() {
  try {
    await inbox.loadMore();
  } catch (error) {
    message.error(errorText(error));
  }
}
/** 只保护对应的已读动作，完成后更新同一 Account 的通知摘要。 */
async function markRead(item: InboxMessage) {
  if (item.readAt || busy.value.has(item.id)) return;
  busy.value.add(item.id);
  try {
    await inbox.markRead(item);
    await businessApplication.inbox.load();
  } catch (error) {
    message.error(errorText(error));
  } finally {
    busy.value.delete(item.id);
  }
}
/** 展开消息后使用其读取版本标记已读。 */
function changed(key: (number | string)[] | number | string) {
  const id = Array.isArray(key) ? key[0] : key;
  const item = state.value.messages.find(
    (candidate) => candidate.id === String(id),
  );
  if (item) void markRead(item);
}
/** 标记全部已读，成功后同步完整列表和铃铛。 */
async function readAll() {
  if (busy.value.has('all')) return;
  busy.value.add('all');
  try {
    await inbox.markAllRead();
    await businessApplication.inbox.load();
    message.success($t('page.inbox.markedAllRead'));
  } catch (error) {
    message.error(errorText(error));
  } finally {
    busy.value.delete('all');
  }
}
</script>
<template>
  <SjPage :title="$t('page.inbox.title')" mode="detail">
    <template #description>
      {{ $t('page.inbox.unread', { count: state.unreadCount }) }}
    </template>
    <template #extra>
      <a-button :loading="state.phase === 'loading'" @click="load">
        <RefreshCw :size="17" aria-hidden="true" />
        {{ $t('page.inbox.refresh') }}
      </a-button>
      <a-button
        :disabled="state.unreadCount === 0"
        :loading="busy.has('all')"
        @click="readAll"
      >
        <CheckCheck :size="17" aria-hidden="true" />
        {{ $t('page.inbox.markAllRead') }}
      </a-button>
    </template>
    <SjResourceState
      :phase="state.phase"
      :error="state.error"
      :has-data="state.messages.length > 0"
      @retry="load"
    >
      <a-collapse v-model:active-key="active" accordion @change="changed">
        <a-collapse-panel v-for="item in state.messages" :key="item.id">
          <template #header>
            <div class="flex min-w-0 flex-wrap items-center gap-2">
              <a-badge
                v-if="!item.readAt"
                :text="$t('page.inbox.unread', { count: 1 })"
                status="processing"
              />
              <strong class="min-w-0 break-words">{{ item.title }}</strong>
              <span class="ml-auto text-xs text-muted-foreground">
                {{ dateTime(item.createdAt) }}
              </span>
            </div>
          </template>
          <iframe
            class="h-96 w-full border-0"
            sandbox=""
            :srcdoc="item.html"
            :title="item.title"
          ></iframe>
        </a-collapse-panel>
      </a-collapse>
      <a-button
        v-if="hasMore"
        class="mt-4 w-full"
        :loading="state.loadingMore"
        @click="loadMore"
      >
        {{ $t('page.inbox.loadMore') }}
      </a-button>
    </SjResourceState>
  </SjPage>
</template>
