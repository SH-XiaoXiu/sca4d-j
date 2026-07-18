<script setup lang="ts">
defineOptions({ name: "DefaultProfileAvatar" });

import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    name: string;
    src?: string | null;
    size?: number;
  }>(),
  { src: null, size: 36 }
);
const emit = defineEmits<{ loadError: [] }>();
const initial = computed(() => props.name.trim().slice(0, 1).toUpperCase() || "?");

function handleError(): void {
  emit("loadError");
}
</script>

<template>
  <a-avatar
    v-if="src"
    class="default-profile-avatar-image"
    :size="size"
    :src="src"
    :alt="name"
    @error="handleError"
  >
    {{ initial }}
  </a-avatar>
  <a-avatar
    v-else
    class="default-profile-avatar-image"
    :size="size"
    :alt="name"
  >
    {{ initial }}
  </a-avatar>
</template>

<style scoped>
.default-profile-avatar-image {
  flex: 0 0 auto;
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-weight: 600;
}

.default-profile-avatar-image :deep(img) {
  object-fit: cover;
}
</style>
