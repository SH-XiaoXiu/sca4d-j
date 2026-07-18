<script setup lang="ts">
defineOptions({ name: "DynamicMenuIcon" });

import type { MenuIcon } from "@sca4d/admin-common";
import { CircleHelp } from "lucide-vue-next";
import { shallowRef, watch, type Component } from "vue";
import { loadMenuIconCatalog } from "./menu-icon-catalog";

const props = withDefaults(defineProps<{ name: MenuIcon; size?: number }>(), { size: 18 });
const icon = shallowRef<Component>(CircleHelp);
let requestedName = props.name;

watch(
  () => props.name,
  async (name) => {
    requestedName = name;
    try {
      const catalog = await loadMenuIconCatalog();
      if (requestedName === name) icon.value = catalog[name] ?? CircleHelp;
    } catch {
      if (requestedName === name) icon.value = CircleHelp;
    }
  },
  { immediate: true }
);
</script>

<template>
  <component
    :is="icon"
    :size="size"
    :data-icon-name="name"
  />
</template>
