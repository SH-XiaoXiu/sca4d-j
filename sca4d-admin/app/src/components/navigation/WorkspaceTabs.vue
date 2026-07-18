<script setup lang="ts">
import { X } from "lucide-vue-next";
import { Modal } from "ant-design-vue";
import { nextTick, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import {
  useWorkspaceStore,
  type WorkspaceDropPosition,
  type WorkspaceTab
} from "@/stores/workspace";

const router = useRouter();
const workspace = useWorkspaceStore();
const { t } = useI18n();
const scroller = ref<HTMLElement | null>(null);
const draggingKey = ref<string | null>(null);
const dropTargetKey = ref<string | null>(null);
const dropPosition = ref<WorkspaceDropPosition>("after");

function navigate(tab: WorkspaceTab): void {
  void router.push(tab.path).then((failure) => {
    if (!failure) {
      workspace.activate(tab.key);
    }
  });
}

function confirmDirty(tabs: WorkspaceTab[], action: () => void): void {
  const dirtyCount = tabs.filter((tab) => tab.dirty).length;
  if (dirtyCount === 0) {
    action();
    return;
  }
  Modal.confirm({
    title: t("workspace.discardTitle"),
    content: t("workspace.discardContent", { count: dirtyCount }),
    okText: t("workspace.closeAnyway"),
    cancelText: t("common.cancel"),
    okType: "danger",
    onOk: action
  });
}

function tabTitle(tab: WorkspaceTab): string {
  return tab.titleKey ? t(tab.titleKey) : tab.title;
}

function closeTab(key: string): void {
  const tab = workspace.tabs.find((item) => item.key === key);
  if (!tab || tab.pinned) {
    return;
  }
  confirmDirty([tab], () => {
    const wasActive = workspace.activeKey === key;
    const next = workspace.close(key);
    if (wasActive && next) {
      void router.replace(next.path);
    }
  });
}

function closeOthers(key: string): void {
  const closing = workspace.tabs.filter((tab) => !tab.pinned && tab.key !== key);
  confirmDirty(closing, () => {
    const next = workspace.closeOthers(key);
    if (next) {
      void router.replace(next.path);
    }
  });
}

function closeAll(): void {
  const closing = workspace.tabs.filter((tab) => !tab.pinned);
  confirmDirty(closing, () => {
    const next = workspace.closeAll();
    if (next) {
      void router.replace(next.path);
    }
  });
}

function contextAction(info: { key: string | number }, tab: WorkspaceTab): void {
  if (info.key === "close") {
    closeTab(tab.key);
  } else if (info.key === "others") {
    closeOthers(tab.key);
  } else if (info.key === "all") {
    closeAll();
  }
}

function onWheel(event: WheelEvent): void {
  const element = scroller.value;
  if (!element || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) {
    return;
  }
  const maximum = element.scrollWidth - element.clientWidth;
  const next = Math.max(0, Math.min(maximum, element.scrollLeft + event.deltaY));
  if (next === element.scrollLeft) {
    return;
  }
  event.preventDefault();
  element.scrollLeft = next;
}

function focusTab(index: number): void {
  const buttons = Array.from(
    scroller.value?.querySelectorAll<HTMLButtonElement>("[role='tab']") ?? []
  );
  buttons[index]?.focus();
}

async function focusTabByKey(key: string): Promise<void> {
  await nextTick();
  const tab = Array.from(scroller.value?.querySelectorAll<HTMLElement>(".workspace-tab") ?? []).find(
    (item) => item.dataset.tabKey === key
  );
  tab?.querySelector<HTMLButtonElement>("[role='tab']")?.focus();
}

function onDragStart(event: DragEvent, tab: WorkspaceTab): void {
  if (tab.pinned || !event.dataTransfer) {
    event.preventDefault();
    return;
  }
  draggingKey.value = tab.key;
  dropTargetKey.value = null;
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("text/plain", tab.key);
}

function onDragOver(event: DragEvent, tab: WorkspaceTab): void {
  if (!draggingKey.value || draggingKey.value === tab.key) {
    return;
  }
  event.preventDefault();
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = "move";
  }
  const target = event.currentTarget as HTMLElement;
  dropTargetKey.value = tab.key;
  dropPosition.value =
    tab.pinned || event.clientX >= target.getBoundingClientRect().left + target.offsetWidth / 2
      ? "after"
      : "before";
}

function onDrop(event: DragEvent, tab: WorkspaceTab): void {
  event.preventDefault();
  if (draggingKey.value && draggingKey.value !== tab.key) {
    workspace.move(draggingKey.value, tab.key, dropPosition.value);
  }
  onDragEnd();
}

function onDragEnd(): void {
  draggingKey.value = null;
  dropTargetKey.value = null;
}

function onKeydown(event: KeyboardEvent, tab: WorkspaceTab, index: number): void {
  if (event.altKey && (event.key === "ArrowLeft" || event.key === "ArrowRight")) {
    event.preventDefault();
    workspace.moveBy(tab.key, event.key === "ArrowLeft" ? -1 : 1);
    void focusTabByKey(tab.key);
  } else if (event.key === "ArrowLeft") {
    event.preventDefault();
    focusTab(index > 0 ? index - 1 : workspace.tabs.length - 1);
  } else if (event.key === "ArrowRight") {
    event.preventDefault();
    focusTab(index < workspace.tabs.length - 1 ? index + 1 : 0);
  } else if (event.key === "Home") {
    event.preventDefault();
    focusTab(0);
  } else if (event.key === "End") {
    event.preventDefault();
    focusTab(workspace.tabs.length - 1);
  } else if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    navigate(tab);
  } else if (event.key === "Delete" && !tab.pinned) {
    event.preventDefault();
    closeTab(tab.key);
  }
}

watch(
  () => workspace.activeKey,
  async (key) => {
    await nextTick();
    const element = scroller.value;
    const tab = Array.from(element?.querySelectorAll<HTMLElement>(".workspace-tab") ?? []).find(
      (item) => item.dataset.tabKey === key
    );
    if (!element || !tab) {
      return;
    }
    const left = tab.offsetLeft;
    const right = left + tab.offsetWidth;
    const behavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
    if (left < element.scrollLeft) {
      element.scrollTo({ left, behavior });
    } else if (right > element.scrollLeft + element.clientWidth) {
      element.scrollTo({ left: right - element.clientWidth, behavior });
    }
  },
  { immediate: true }
);
</script>

<template>
  <nav
    class="workspace-tabs"
    :aria-label="t('workspace.label')"
  >
    <div
      ref="scroller"
      class="workspace-tabs__scroller"
      role="tablist"
      :aria-label="t('workspace.tabList')"
      @wheel="onWheel"
    >
      <a-dropdown
        v-for="(tab, index) in workspace.tabs"
        :key="tab.key"
        :trigger="['contextmenu']"
      >
        <div
          class="workspace-tab"
          :class="{
            'workspace-tab--active': workspace.activeKey === tab.key,
            'workspace-tab--closable': !tab.pinned,
            'workspace-tab--dragging': draggingKey === tab.key,
            'workspace-tab--drop-before': dropTargetKey === tab.key && dropPosition === 'before',
            'workspace-tab--drop-after': dropTargetKey === tab.key && dropPosition === 'after'
          }"
          :data-tab-key="tab.key"
          :draggable="!tab.pinned"
          role="presentation"
          @dragstart="onDragStart($event, tab)"
          @dragover="onDragOver($event, tab)"
          @drop="onDrop($event, tab)"
          @dragend="onDragEnd"
        >
          <button
            type="button"
            class="workspace-tab__target"
            role="tab"
            :aria-selected="workspace.activeKey === tab.key"
            :aria-label="tab.dirty ? t('workspace.dirtyLabel', { title: tabTitle(tab) }) : tabTitle(tab)"
            :aria-keyshortcuts="tab.pinned ? undefined : 'Alt+ArrowLeft Alt+ArrowRight Delete'"
            :tabindex="workspace.activeKey === tab.key ? 0 : -1"
            :title="tabTitle(tab)"
            @click="navigate(tab)"
            @keydown="onKeydown($event, tab, index)"
          >
            <span
              v-if="tab.dirty"
              class="workspace-tab__dirty"
              aria-hidden="true"
            />
            <span class="workspace-tab__title">{{ tabTitle(tab) }}</span>
          </button>
          <button
            v-if="!tab.pinned"
            type="button"
            class="workspace-tab__close"
            :aria-label="t('workspace.closeLabel', { title: tabTitle(tab) })"
            :title="t('workspace.closeLabel', { title: tabTitle(tab) })"
            tabindex="-1"
            @click.stop="closeTab(tab.key)"
          >
            <X :size="14" />
          </button>
        </div>
        <template #overlay>
          <a-menu @click="contextAction($event, tab)">
            <a-menu-item
              key="close"
              :disabled="tab.pinned"
            >
              {{ t("workspace.closeCurrent") }}
            </a-menu-item>
            <a-menu-item
              key="others"
              :disabled="!workspace.tabs.some((item) => !item.pinned && item.key !== tab.key)"
            >
              {{ t("workspace.closeOthers") }}
            </a-menu-item>
            <a-menu-item
              key="all"
              :disabled="!workspace.tabs.some((item) => !item.pinned)"
            >
              {{ t("workspace.closeAll") }}
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </nav>
</template>
