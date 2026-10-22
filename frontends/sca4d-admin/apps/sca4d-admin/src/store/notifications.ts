import { onScopeDispose, shallowRef } from 'vue';

import { defineStore } from 'pinia';

import { businessApplication } from '#/platform/business-application';

/** 顶部铃铛与完整列表共享的通知摘要，完整列表的游标不存入这里。 */
export const useNotificationStore = defineStore('notification-summary', () => {
  const state = shallowRef(businessApplication.inbox.state);
  const unsubscribe = businessApplication.inbox.subscribe((next) => {
    state.value = next;
  });
  onScopeDispose(unsubscribe);
  return { state };
});
