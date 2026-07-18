<script setup lang="ts">
defineOptions({ name: "QueryToolbar" });
</script>

<template>
  <div class="query-toolbar-container">
    <div class="query-toolbar">
      <div
        v-if="$slots.search"
        class="query-toolbar__search"
      >
        <slot name="search" />
      </div>
      <div
        v-if="$slots.filters"
        class="query-toolbar__filters"
      >
        <slot name="filters" />
      </div>
      <div
        v-if="$slots.actions"
        class="query-toolbar__actions"
      >
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.query-toolbar-container {
  container-type: inline-size;
  margin-bottom: 16px;
}

.query-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(0, auto) auto;
  align-items: center;
  gap: 10px;
}

.query-toolbar__search {
  min-width: 0;
}

.query-toolbar__search :deep(.ant-input),
.query-toolbar__search :deep(.ant-input-affix-wrapper) {
  width: 100%;
}

.query-toolbar__filters,
.query-toolbar__actions {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.query-toolbar__filters :deep(.ant-select),
.query-toolbar__filters :deep(.ant-picker) {
  width: clamp(140px, 18cqi, 220px);
}

.query-toolbar__actions {
  justify-content: flex-end;
}

@container (max-width: 760px) {
  .query-toolbar {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .query-toolbar__search {
    grid-column: 1 / -1;
  }
}

@container (max-width: 520px) {
  .query-toolbar {
    grid-template-columns: minmax(0, 1fr);
  }

  .query-toolbar__filters,
  .query-toolbar__actions {
    width: 100%;
  }

  .query-toolbar__filters :deep(.ant-select),
  .query-toolbar__filters :deep(.ant-picker) {
    width: 100%;
    flex: 1 1 100%;
  }
}
</style>
