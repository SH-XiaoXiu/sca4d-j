<script setup lang="ts">
import type { IotThingValueType } from '@sca4d/admin';

import { computed } from 'vue';

import { useSjPresentation } from '#/features/sj-presentation';
defineOptions({ name: 'ThingValueTypeView' });

const props = defineProps<{
  value: IotThingValueType;
}>();

const { t } = useSjPresentation();

const constraints = computed(() => {
  const value = props.value;
  return [
    value.minimum === null ? null : `${t('minimum')}: ${value.minimum}`,
    value.maximum === null ? null : `${t('maximum')}: ${value.maximum}`,
    value.scale === null ? null : `${t('scale')}: ${value.scale}`,
    value.unit === null ? null : `${t('unit')}: ${value.unit}`,
    value.maximumLength === null
      ? null
      : `${t('maximumLength')}: ${value.maximumLength}`,
    value.maximumItems === null
      ? null
      : `${t('maximumItems')}: ${value.maximumItems}`,
  ].filter((item): item is string => item !== null);
});
</script>

<template>
  <div class="min-w-0 space-y-2">
    <div class="flex flex-wrap items-center gap-1.5">
      <a-tag color="blue">{{ value.kind }}</a-tag>
      <a-tag v-for="constraint in constraints" :key="constraint">
        {{ constraint }}
      </a-tag>
    </div>

    <div v-if="value.enumValues.length > 0" class="flex flex-wrap gap-1.5">
      <span class="text-muted-foreground text-xs">{{ t('enumValues') }}</span>
      <a-tag v-for="item in value.enumValues" :key="item.code" color="cyan">
        {{ item.displayName }} · {{ item.code }}
      </a-tag>
    </div>

    <div v-if="value.pattern !== null" class="text-muted-foreground text-xs">
      {{ t('pattern') }}:
      <code class="text-foreground break-all">{{ value.pattern }}</code>
    </div>

    <div
      v-if="value.requiredFields.length > 0 || value.optionalFields.length > 0"
      class="border-border space-y-3 border-l pl-3"
    >
      <div
        v-for="field in value.requiredFields"
        :key="`required:${field.code}`"
        class="space-y-1.5"
      >
        <div class="flex min-w-0 flex-wrap items-center gap-2">
          <strong class="truncate text-sm">{{ field.displayName }}</strong>
          <code class="text-muted-foreground text-xs">{{ field.code }}</code>
          <a-tag color="red">{{ t('requiredField') }}</a-tag>
        </div>
        <ThingValueTypeView :value="field.valueType" />
      </div>
      <div
        v-for="field in value.optionalFields"
        :key="`optional:${field.code}`"
        class="space-y-1.5"
      >
        <div class="flex min-w-0 flex-wrap items-center gap-2">
          <strong class="truncate text-sm">{{ field.displayName }}</strong>
          <code class="text-muted-foreground text-xs">{{ field.code }}</code>
          <a-tag>{{ t('optionalField') }}</a-tag>
        </div>
        <ThingValueTypeView :value="field.valueType" />
      </div>
    </div>

    <div v-if="value.elementType" class="border-border border-l pl-3">
      <div class="text-muted-foreground mb-1 text-xs">
        {{ t('elementType') }}
      </div>
      <ThingValueTypeView :value="value.elementType" />
    </div>
  </div>
</template>
