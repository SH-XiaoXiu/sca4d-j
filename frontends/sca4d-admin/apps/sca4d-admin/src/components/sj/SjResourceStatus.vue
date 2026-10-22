<script setup lang="ts">
defineOptions({ name: 'SjResourceStatus' });

defineProps<{
  /** Tag 颜色；与 dotStatus 二选一。 */
  color?: string;
  /** Badge 状态；适合在线、可达等点状态。 */
  dotStatus?: 'default' | 'error' | 'processing' | 'success' | 'warning';
  /** 状态字段名称。 */
  label: string;
  /** 状态值。 */
  value: string;
}>();
</script>

<template>
  <span class="sj-resource-status">
    <span class="sj-resource-status__label">{{ label }}</span>
    <a-badge v-if="dotStatus" :status="dotStatus" :text="value" />
    <a-tag v-else class="m-0" :color="color">{{ value }}</a-tag>
  </span>
</template>

<style scoped>
.sj-resource-status {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  min-width: 0;
  padding-right: 14px;
  border-right: 1px solid hsl(var(--border));
}

.sj-resource-status__label {
  flex: none;
  font-size: 12px;
  line-height: 1.4;
  color: hsl(var(--muted-foreground));
}

.sj-resource-status :deep(.ant-badge-status-text) {
  margin-inline-start: 6px;
  font-size: 13px;
  color: hsl(var(--foreground));
}
</style>
