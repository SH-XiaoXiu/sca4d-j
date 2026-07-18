<script setup lang="ts">
import { ArrowUpRight } from "lucide-vue-next";
import type { RouteLocationRaw } from "vue-router";

defineOptions({ name: "ResourceLink" });

defineProps<{
  label?: string | null | undefined;
  to?: RouteLocationRaw | undefined;
  navigationMessage?: string | undefined;
}>();
</script>

<template>
  <RouterLink
    v-if="to && label"
    class="resource-link"
    :to="to"
  >
    <span>{{ label }}</span>
    <ArrowUpRight
      class="resource-link__icon"
      :size="14"
      aria-hidden="true"
    />
  </RouterLink>
  <a-tooltip
    v-else-if="navigationMessage"
    :title="navigationMessage"
  >
    <span class="resource-link resource-link--unavailable">
      <span v-if="label">{{ label }}</span>
      <span
        v-if="label"
        class="resource-link__state"
      >{{ navigationMessage }}</span>
      <span v-else>{{ navigationMessage }}</span>
    </span>
  </a-tooltip>
  <span
    v-else-if="label"
    class="resource-link resource-link--plain"
  >{{ label }}</span>
</template>

<style scoped>
.resource-link {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 3px;
  border-radius: 4px;
  color: var(--app-primary);
  font-weight: 600;
  line-height: 1.5;
  text-decoration: underline;
  text-decoration-color: color-mix(in srgb, var(--app-primary) 45%, transparent);
  text-underline-offset: 3px;
}

.resource-link:hover {
  color: var(--app-primary-hover);
  text-decoration-color: currentColor;
}

.resource-link:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 3px;
}

.resource-link__icon {
  flex: 0 0 auto;
}

.resource-link--unavailable,
.resource-link--plain {
  color: var(--app-text-secondary);
  font-weight: 500;
  text-decoration: none;
}

.resource-link__state {
  color: var(--app-text-tertiary);
  font-size: 12px;
  font-weight: 400;
}

@media (max-width: 900px) {
  a.resource-link {
    min-height: 44px;
  }
}
</style>
