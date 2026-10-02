<script setup lang="ts">
import type { ManagedDictionary, ManagedDictionaryOption } from '@sca4d/admin';

import type { SjRowAction } from '#/components/sj';

import { computed, onMounted, ref, watch } from 'vue';

import { message } from 'ant-design-vue';
import { Pencil, Plus, RefreshCw, Trash2 } from 'lucide-vue-next';

import { SjDataGrid } from '#/components/sj';
import { SjRowActions } from '#/components/sj';
import { SjMasterDetail, SjSection } from '#/components/sj';
import DictionaryEditor from '#/features/dictionaries/DictionaryEditor.vue';
import DictionaryOptionEditor from '#/features/dictionaries/DictionaryOptionEditor.vue';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const { t, errorText } = useSjPresentation();
const dictionaryEditor = ref<InstanceType<typeof DictionaryEditor>>();
const optionEditor = ref<InstanceType<typeof DictionaryOptionEditor>>();

const systemDictionaries = useSjControllerState(
  businessApplication.systemDictionaries,
);
const session = useSjAccess();
const optionDictionaryKey = ref<null | string>(null);

const canManage = computed(() => session.value.capabilities.dictionariesManage);
const optionDictionary = computed(() => {
  const key = optionDictionaryKey.value;
  return key
    ? (systemDictionaries.value.dictionaries.find(
        (dictionary) => dictionary.key === key,
      ) ?? null)
    : null;
});
const optionColumns = computed(() => [
  {
    field: 'value',
    title: t('optionValue'),
  },
  {
    field: 'label',
    title: t('optionLabel'),
  },
  {
    field: 'source',
    title: t('optionSource'),
    width: 130,
  },
  {
    field: 'enabled',
    title: t('status'),
    width: 110,
  },
  {
    field: 'sortOrder',
    title: t('sortOrder'),
    width: 116,
  },
  {
    field: 'actions',
    title: t('actions'),
    width: 128,
  },
]);

onMounted(() => void reload());

/** 刷新完整字典管理结果，并让 ResourceState 呈现失败状态。 */
async function reload(): Promise<void> {
  try {
    await businessApplication.systemDictionaries.load();
  } catch {
    // Controller 已保存统一错误和加载阶段。
  }
}

/** 删除指定动态字典及其数据库选项。 */
async function removeDictionary(dictionary: ManagedDictionary): Promise<void> {
  try {
    await businessApplication.systemDictionaries.delete(dictionary);
    message.success(t('deleteSuccess'));
  } catch {
    message.error(errorText(systemDictionaries.value.error));
  }
}

/** 删除一个数据库维护的字典项。 */
async function removeOption(
  dictionary: ManagedDictionary,
  option: ManagedDictionaryOption,
): Promise<void> {
  try {
    await businessApplication.systemDictionaries.deleteOption(
      dictionary,
      option,
    );
    message.success(t('deleteSuccess'));
  } catch {
    message.error(errorText(systemDictionaries.value.error));
  }
}
const search = ref('');
const filtered = computed(() =>
  systemDictionaries.value.dictionaries.filter((dictionary) =>
    `${dictionary.label} ${dictionary.key}`
      .toLocaleLowerCase()
      .includes(search.value.trim().toLocaleLowerCase()),
  ),
);
watch(
  () => systemDictionaries.value.dictionaries,
  (items) => {
    if (!items.some((item) => item.key === optionDictionaryKey.value))
      optionDictionaryKey.value = items[0]?.key ?? null;
  },
);
/** 字典的代码来源限制编辑，未授权的修改动作不展示。 */
function dictionaryActions(
  dictionary: ManagedDictionary,
): readonly SjRowAction[] {
  return [
    {
      key: 'edit',
      label: t('editDictionary'),
      icon: Pencil,
      visible: canManage.value,
      disabled: dictionary.codeDefined,
      disabledReason: dictionary.codeDefined ? t('codeDictionary') : undefined,
      run: () => dictionaryEditor.value?.open(dictionary),
    },
    {
      key: 'delete',
      label: t('deleteDictionary'),
      icon: Trash2,
      visible: canManage.value,
      disabled: dictionary.codeDefined,
      disabledReason: dictionary.codeDefined ? t('codeDictionary') : undefined,
      danger: true,
      confirm: {
        title: t('confirmDeleteDictionary'),
        content: dictionary.label,
        okText: t('remove'),
      },
      run: () => removeDictionary(dictionary),
    },
  ];
}
/** 选项使用当前字典及读取的 Version 执行修改和删除。 */
function optionActions(
  option: ManagedDictionaryOption,
): readonly SjRowAction[] {
  return [
    {
      key: 'edit',
      label: t('editOption'),
      icon: Pencil,
      visible: canManage.value,
      disabled: option.codeDefined,
      disabledReason: option.codeDefined ? t('codeOption') : undefined,
      run: () => optionEditor.value?.open(option),
    },
    {
      key: 'delete',
      label: t('deleteOption'),
      icon: Trash2,
      visible: canManage.value,
      disabled: option.codeDefined,
      disabledReason: option.codeDefined ? t('codeOption') : undefined,
      danger: true,
      confirm: {
        title: t('confirmDeleteOption'),
        content: option.label,
        okText: t('remove'),
      },
      run: async () => {
        if (optionDictionary.value)
          await removeOption(optionDictionary.value, option);
      },
    },
  ];
}
</script>

<template>
  <SjMasterDetail :title="t('dictionaries')" :left-width="28">
    <template #left>
      <div class="flex h-full min-h-0 flex-col gap-3 p-3">
        <a-input-search
          v-model:value="search"
          :placeholder="t('dictionaryLabel')"
          :aria-label="t('dictionaryLabel')"
        />
        <div class="flex justify-between gap-2">
          <a-button
            :aria-label="t('refresh')"
            :loading="systemDictionaries.phase === 'loading'"
            @click="reload"
          >
            <RefreshCw :size="17" aria-hidden="true" />
          </a-button>
          <a-button
            v-if="canManage"
            type="primary"
            @click="dictionaryEditor?.open()"
          >
            <Plus :size="17" aria-hidden="true" />
            {{ t('createDictionary') }}
          </a-button>
        </div>
        <a-alert
          v-if="systemDictionaries.error"
          :message="errorText(systemDictionaries.error)"
          type="warning"
        />
        <div class="min-h-0 flex-1 overflow-y-auto">
          <div
            v-for="dictionary in filtered"
            :key="dictionary.key"
            class="mb-1 rounded-md p-2"
            :class="dictionary.key === optionDictionaryKey ? 'bg-accent' : ''"
          >
            <button
              type="button"
              class="block w-full text-left focus-visible:ring-2 focus-visible:ring-ring"
              :aria-pressed="dictionary.key === optionDictionaryKey"
              @click="optionDictionaryKey = dictionary.key"
            >
              <strong class="block truncate">{{ dictionary.label }}</strong>
              <span class="break-all text-xs text-muted-foreground">
                {{ dictionary.key }}
              </span>
            </button>
            <SjRowActions :actions="dictionaryActions(dictionary)" />
          </div>
        </div>
      </div>
    </template>
    <template #mobile>
      <div class="flex flex-wrap gap-2">
        <a-select
          v-model:value="optionDictionaryKey"
          class="min-w-48 flex-1"
          :aria-label="t('dictionaries')"
          :options="
            filtered.map((dictionary) => ({
              value: dictionary.key,
              label: dictionary.label,
            }))
          "
        />
        <a-button :aria-label="t('refresh')" @click="reload">
          <RefreshCw :size="17" aria-hidden="true" />
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          @click="dictionaryEditor?.open()"
        >
          <Plus :size="17" aria-hidden="true" />
          {{ t('createDictionary') }}
        </a-button>
        <SjRowActions
          v-if="optionDictionary"
          :actions="dictionaryActions(optionDictionary)"
        />
      </div>
    </template>
    <SjSection
      v-if="optionDictionary"
      :title="optionDictionary.label"
      :divided="false"
    >
      <template #actions>
        <a-button
          v-if="canManage && optionDictionary.optionsEditable"
          type="primary"
          @click="optionEditor?.open()"
        >
          <Plus :size="17" aria-hidden="true" />
          {{ t('createOption') }}
        </a-button>
      </template>
      <SjDataGrid
        :columns="optionColumns"
        :items="optionDictionary.options"
        key-field="value"
      >
        <template #cell="{ column, row, value }">
          <a-tag v-if="column.field === 'source'">
            {{ row.codeDefined ? t('codeOption') : t('databaseOption') }}
          </a-tag>
          <a-tag v-else-if="column.field === 'enabled'">
            {{ row.enabled ? t('active') : t('disabled') }}
          </a-tag>
          <SjRowActions
            v-else-if="column.field === 'actions'"
            :actions="optionActions(row)"
          />
          <span v-else>{{ value ?? '-' }}</span>
        </template>
      </SjDataGrid>
    </SjSection>
    <a-empty v-else :description="t('noData')" />
  </SjMasterDetail>
  <DictionaryEditor ref="dictionaryEditor" />
  <DictionaryOptionEditor ref="optionEditor" :dictionary="optionDictionary" />
</template>
