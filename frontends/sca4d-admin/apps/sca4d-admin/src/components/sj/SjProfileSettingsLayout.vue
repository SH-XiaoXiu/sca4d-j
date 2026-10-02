<script setup lang="ts">
import type { Component } from 'vue';
import type { RouteLocationRaw } from 'vue-router';

import { RouterLink } from 'vue-router';

defineOptions({ name: 'SjProfileSettingsLayout' });

defineProps<{
  /** 左侧设置导航；每项必须对应一个可直接访问的页面路由。 */
  navigation: readonly {
    icon: Component;
    label: string;
    to: RouteLocationRaw;
  }[];
  /** 设置导航的可访问名称。 */
  navigationLabel: string;
}>();
</script>

<template>
  <div
    class="grid h-full min-h-[560px] w-full max-w-6xl overflow-hidden rounded-md border border-border bg-card lg:grid-cols-[240px_minmax(0,1fr)]"
  >
    <aside class="min-w-0 border-b border-border lg:border-r lg:border-b-0">
      <div class="p-6">
        <slot name="identity"></slot>
      </div>

      <nav
        class="grid grid-cols-2 gap-1 border-t border-border p-3 lg:grid-cols-1"
        :aria-label="navigationLabel"
      >
        <RouterLink
          v-for="item in navigation"
          :key="item.label"
          :to="item.to"
          custom
          v-slot="{ href, isExactActive, navigate }"
        >
          <a
            :href="href"
            :aria-current="isExactActive ? 'page' : undefined"
            class="flex min-h-11 min-w-0 items-center justify-center gap-2 rounded-md px-3 py-2 text-center text-sm font-medium text-foreground/70 transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring lg:justify-start lg:text-left"
            :class="[
              isExactActive &&
                'bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground',
            ]"
            @click="navigate"
          >
            <component :is="item.icon" :size="18" aria-hidden="true" />
            <span class="min-w-0 leading-5">{{ item.label }}</span>
          </a>
        </RouterLink>
      </nav>
    </aside>

    <main class="min-w-0 overflow-y-auto bg-background p-5 sm:p-7 lg:p-8">
      <slot></slot>
    </main>
  </div>
</template>
