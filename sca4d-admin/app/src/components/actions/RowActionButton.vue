<script setup lang="ts">
defineOptions({ name: "RowActionButton" });

withDefaults(defineProps<{
  label: string;
  danger?: boolean;
  disabled?: boolean;
}>(), {
  danger: false,
  disabled: false
});
defineEmits<{ click: [event: MouseEvent] }>();
</script>

<template>
  <a-tooltip :title="label">
    <span class="row-action-button__wrapper">
      <a-button
        type="text"
        class="row-action-button"
        :danger="danger"
        :disabled="disabled"
        :aria-label="label"
        @click="$emit('click', $event)"
      >
        <slot />
      </a-button>
    </span>
  </a-tooltip>
</template>

<style scoped>
.row-action-button__wrapper {
  display: inline-flex;
}

.row-action-button.ant-btn {
  width: 40px;
  min-width: 40px;
  height: 40px;
  padding: 0;
}
</style>
