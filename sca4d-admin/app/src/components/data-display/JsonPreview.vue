<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{ value: unknown }>();
const text = computed(() =>
  props.value === null || props.value === undefined ? "--" : JSON.stringify(props.value, null, 2)
);
</script>

<template>
  <pre class="json-preview">{{ text }}</pre>
</template>
