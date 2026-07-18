<script setup lang="ts">
defineOptions({ name: "DashboardPage" });

import { ArrowRight, CircleUserRound, ScrollText } from "lucide-vue-next";
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import PageHeader from "@/components/page/PageHeader.vue";
import { useSessionStore } from "@/stores/session";

const router = useRouter();
const auth = useSessionStore();
const { t } = useI18n();
const displayName = computed(() => auth.me?.subject?.displayName || t("navigation.currentUser"));
const quickLinks = computed(() =>
  [
    {
      title: t("route.operationLogs"),
      description: t("dashboard.operationLogsDescription"),
      path: "/system/operation-logs",
      icon: ScrollText,
      visible: auth.hasPermission("system:operation-logs:read")
    },
    {
      title: t("route.profile"),
      description: t("dashboard.profileDescription"),
      path: "/profile",
      icon: CircleUserRound,
      visible: true
    }
  ].filter((item) => item.visible)
);
</script>

<template>
  <div>
    <PageHeader
      :title="t('route.dashboard')"
      :description="t('dashboard.welcome', { name: displayName })"
    />
    <section class="page-panel workspace-panel">
      <h2 class="workspace-panel__title">
        {{ t("dashboard.quickLinks") }}
      </h2>
      <a-list
        :data-source="quickLinks"
        class="workspace-list"
      >
        <template #renderItem="{ item }">
          <a-list-item class="workspace-list__item">
            <button
              type="button"
              class="workspace-list__target"
              @click="router.push(item.path)"
            >
              <a-list-item-meta :description="item.description">
                <template #avatar>
                  <span class="workspace-list__icon">
                    <component
                      :is="item.icon"
                      :size="18"
                    />
                  </span>
                </template>
                <template #title>
                  {{ item.title }}
                </template>
              </a-list-item-meta>
              <ArrowRight
                :size="17"
                class="workspace-list__arrow"
              />
            </button>
          </a-list-item>
        </template>
      </a-list>
    </section>
  </div>
</template>

<style scoped>
.workspace-panel {
  overflow: hidden;
}

.workspace-panel__title {
  margin: 0;
  padding: 14px 16px;
  color: var(--app-text);
  border-bottom: 1px solid var(--app-border);
  font-size: 15px;
  font-weight: 600;
}

.workspace-list__item {
  padding: 0 !important;
}

.workspace-list__target {
  display: flex;
  width: 100%;
  min-height: 66px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  color: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;
  text-align: left;
  transition: background 160ms ease;
}

.workspace-list__target:hover {
  background: var(--app-surface-muted);
}

.workspace-list__target:focus-visible {
  position: relative;
  z-index: 1;
}

.workspace-list__icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: var(--app-primary);
  background: var(--app-primary-soft);
  border-radius: var(--app-radius);
}

.workspace-list__arrow {
  color: var(--app-text-tertiary);
}
</style>
