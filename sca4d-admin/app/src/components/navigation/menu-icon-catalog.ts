import type { Component } from "vue";

let pending: Promise<Record<string, Component>> | null = null;

/** 延迟加载完整 Lucide 目录，并让导航图标与选择器复用同一次请求。 */
export function loadMenuIconCatalog(): Promise<Record<string, Component>> {
  pending ??= import("lucide-vue-next")
    .then(({ icons }) => icons as Record<string, Component>)
    .catch((error: unknown) => {
      pending = null;
      throw error;
    });
  return pending;
}
