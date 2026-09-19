<script setup lang="ts">
import type {
  LocalizedFields,
  ManagedDictionary,
  ManagedDictionaryOption,
} from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import {
  BookOpenCheck,
  Pencil,
  Plus,
  RefreshCw,
  SlidersHorizontal,
  Trash2,
} from 'lucide-vue-next';

import ResourceState from '../components/ResourceState.vue';
import { useI18n } from '../i18n';
import { useAdminStore } from '../store/business';

const store = useAdminStore();
const { errorText, t } = useI18n();
const dictionaryModalOpen = ref(false);
const dictionarySaving = ref(false);
const dictionaryFormRef = ref<FormInstance>();
const editingDictionary = ref<ManagedDictionary | null>(null);
const dictionaryForm = reactive({
  enabled: true,
  key: '',
  label: '',
  sortOrder: 0,
});
const optionDrawerOpen = ref(false);
const optionDictionaryKey = ref<null | string>(null);
const optionModalOpen = ref(false);
const optionSaving = ref(false);
const optionFormRef = ref<FormInstance>();
const editingOption = ref<ManagedDictionaryOption | null>(null);
const optionForm = reactive({
  enabled: true,
  label: '',
  labels: {} as Record<string, string>,
  sortOrder: 0,
  value: '',
});

const canManage = computed(() => store.session.capabilities.dictionariesManage);
const supportedLocales = computed(
  () => store.systemDictionaries.localeCatalog?.supportedLocales ?? [],
);
const optionDictionary = computed(() => {
  const key = optionDictionaryKey.value;
  return key
    ? (store.systemDictionaries.dictionaries.find(
        (dictionary) => dictionary.key === key,
      ) ?? null)
    : null;
});
const dictionaryColumns = computed(() => [
  {
    dataIndex: 'key',
    key: 'key',
    title: t('dictionaryKey'),
  },
  {
    dataIndex: 'label',
    key: 'label',
    title: t('dictionaryLabel'),
  },
  {
    key: 'source',
    title: t('dictionarySource'),
    width: 136,
  },
  {
    key: 'enabled',
    title: t('status'),
    width: 110,
  },
  {
    key: 'options',
    title: t('optionCount'),
    width: 110,
  },
  {
    key: 'actions',
    title: t('actions'),
    width: 148,
  },
]);
const optionColumns = computed(() => [
  {
    dataIndex: 'value',
    key: 'value',
    title: t('optionValue'),
  },
  {
    dataIndex: 'label',
    key: 'label',
    title: t('optionLabel'),
  },
  {
    key: 'source',
    title: t('optionSource'),
    width: 130,
  },
  {
    key: 'enabled',
    title: t('status'),
    width: 110,
  },
  {
    dataIndex: 'sortOrder',
    key: 'sortOrder',
    title: t('sortOrder'),
    width: 116,
  },
  {
    key: 'actions',
    title: t('actions'),
    width: 128,
  },
]);

onMounted(() => void reload());

/** 刷新完整字典管理结果，并让 ResourceState 呈现失败状态。 */
async function reload(): Promise<void> {
  try {
    await store.loadDictionaries();
  } catch {
    // Controller 已保存统一错误和加载阶段。
  }
}

/** 打开新建或编辑动态字典窗口。 */
function openDictionaryModal(
  dictionary: ManagedDictionary | null = null,
): void {
  editingDictionary.value = dictionary;
  dictionaryForm.key = dictionary?.key ?? '';
  dictionaryForm.label = dictionary?.label ?? '';
  dictionaryForm.enabled = dictionary?.enabled ?? true;
  dictionaryForm.sortOrder = dictionary?.sortOrder ?? 0;
  dictionaryModalOpen.value = true;
}

/** 校验字典表单并创建或更新动态字典。 */
async function saveDictionary(): Promise<void> {
  await dictionaryFormRef.value?.validate();
  dictionarySaving.value = true;
  try {
    const dictionary = editingDictionary.value;
    if (dictionary) {
      if (dictionary.version === null) return;
      await store.updateDictionary(dictionary, {
        enabled: dictionaryForm.enabled,
        label: dictionaryForm.label.trim(),
        sortOrder: dictionaryForm.sortOrder,
        version: dictionary.version,
      });
      message.success(t('updateSuccess'));
    } else {
      await store.createDictionary({
        enabled: dictionaryForm.enabled,
        key: dictionaryForm.key.trim(),
        label: dictionaryForm.label.trim(),
        sortOrder: dictionaryForm.sortOrder,
      });
      message.success(t('createSuccess'));
    }
    dictionaryModalOpen.value = false;
  } catch {
    message.error(errorText(store.systemDictionaries.error));
  } finally {
    dictionarySaving.value = false;
  }
}

/** 删除指定动态字典及其数据库选项。 */
async function removeDictionary(dictionary: ManagedDictionary): Promise<void> {
  try {
    await store.deleteDictionary(dictionary);
    message.success(t('deleteSuccess'));
  } catch {
    message.error(errorText(store.systemDictionaries.error));
  }
}

/** 打开一个字典的只读或可维护选项抽屉。 */
function openOptionDrawer(dictionary: ManagedDictionary): void {
  optionDictionaryKey.value = dictionary.key;
  optionDrawerOpen.value = true;
}

/** 打开新建或编辑数据库字典项窗口。 */
function openOptionModal(option: ManagedDictionaryOption | null = null): void {
  editingOption.value = option;
  optionForm.value = option?.value ?? '';
  optionForm.label = option?.fallbackLabel ?? '';
  optionForm.labels = Object.fromEntries(
    supportedLocales.value.map((locale) => [
      locale,
      option?.i18n?.[locale]?.label ?? '',
    ]),
  );
  optionForm.enabled = option?.enabled ?? true;
  optionForm.sortOrder = option?.sortOrder ?? 0;
  optionModalOpen.value = true;
}

/** 校验选项表单并创建或更新数据库字典项。 */
async function saveOption(): Promise<void> {
  const dictionary = optionDictionary.value;
  if (!dictionary) return;
  await optionFormRef.value?.validate();
  optionSaving.value = true;
  try {
    const option = editingOption.value;
    if (option) {
      if (option.version === null) return;
      await store.updateDictionaryOption(dictionary, option, {
        enabled: optionForm.enabled,
        i18n: optionI18n(),
        label: optionForm.label.trim(),
        sortOrder: optionForm.sortOrder,
        version: option.version,
      });
      message.success(t('updateSuccess'));
    } else {
      await store.createDictionaryOption(dictionary.key, {
        enabled: optionForm.enabled,
        i18n: optionI18n(),
        label: optionForm.label.trim(),
        sortOrder: optionForm.sortOrder,
        value: optionForm.value.trim(),
      });
      message.success(t('createSuccess'));
    }
    optionModalOpen.value = false;
  } catch {
    message.error(errorText(store.systemDictionaries.error));
  } finally {
    optionSaving.value = false;
  }
}

/** 把已填写的 Locale 标签转换成后端完整替换使用的 LocalizedFields。 */
function optionI18n(): LocalizedFields {
  return Object.fromEntries(
    supportedLocales.value.flatMap((locale) => {
      const label = optionForm.labels[locale]?.trim();
      return label ? [[locale, { label }]] : [];
    }),
  );
}

/** 删除一个数据库维护的字典项。 */
async function removeOption(
  dictionary: ManagedDictionary,
  option: ManagedDictionaryOption,
): Promise<void> {
  try {
    await store.deleteDictionaryOption(dictionary, option);
    message.success(t('deleteSuccess'));
  } catch {
    message.error(errorText(store.systemDictionaries.error));
  }
}
</script>

<template>
  <Page auto-content-height>
    <ResourceState
      :error="store.systemDictionaries.error"
      :phase="store.systemDictionaries.phase"
      @retry="reload"
    >
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-2 text-base font-medium">
          <BookOpenCheck :size="19" aria-hidden="true" />
          <span>{{ t('dictionaries') }}</span>
        </div>
        <div class="flex items-center gap-2">
          <a-tooltip :title="t('refresh')">
            <a-button :aria-label="t('refresh')" @click="reload">
              <RefreshCw :size="17" aria-hidden="true" />
            </a-button>
          </a-tooltip>
          <a-button
            v-if="canManage"
            type="primary"
            @click="openDictionaryModal()"
          >
            <Plus :size="17" aria-hidden="true" />{{ t('createDictionary') }}
          </a-button>
        </div>
      </div>

      <a-table
        :columns="dictionaryColumns"
        :data-source="store.systemDictionaries.dictionaries"
        :pagination="false"
        row-key="key"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <a-tag
            v-if="column.key === 'source'"
            :color="record.codeDefined ? 'blue' : 'cyan'"
          >
            {{
              record.codeDefined ? t('codeDictionary') : t('dynamicDictionary')
            }}
          </a-tag>
          <a-tag
            v-else-if="column.key === 'enabled'"
            :color="record.enabled ? 'green' : 'default'"
          >
            {{ record.enabled ? t('active') : t('disabled') }}
          </a-tag>
          <span v-else-if="column.key === 'options'">{{
            record.options.length
          }}</span>
          <div
            v-else-if="column.key === 'actions'"
            class="flex justify-center gap-1"
          >
            <a-tooltip :title="t('manageOptions')">
              <a-button
                type="text"
                :aria-label="t('manageOptions')"
                @click="openOptionDrawer(record)"
              >
                <SlidersHorizontal :size="17" aria-hidden="true" />
              </a-button>
            </a-tooltip>
            <a-tooltip :title="t('editDictionary')">
              <a-button
                type="text"
                :aria-label="t('editDictionary')"
                :disabled="!canManage || record.codeDefined"
                @click="openDictionaryModal(record)"
              >
                <Pencil :size="17" aria-hidden="true" />
              </a-button>
            </a-tooltip>
            <a-popconfirm
              :title="t('confirmDeleteDictionary')"
              :ok-text="t('remove')"
              :cancel-text="t('cancel')"
              @confirm="removeDictionary(record)"
            >
              <a-tooltip :title="t('deleteDictionary')">
                <a-button
                  type="text"
                  danger
                  :aria-label="t('deleteDictionary')"
                  :disabled="!canManage || record.codeDefined"
                >
                  <Trash2 :size="17" aria-hidden="true" />
                </a-button>
              </a-tooltip>
            </a-popconfirm>
          </div>
        </template>
      </a-table>
    </ResourceState>

    <a-modal
      v-model:open="dictionaryModalOpen"
      :cancel-text="t('cancel')"
      :confirm-loading="dictionarySaving"
      :ok-text="t('save')"
      :title="editingDictionary ? t('editDictionary') : t('createDictionary')"
      @ok="saveDictionary"
    >
      <a-form ref="dictionaryFormRef" layout="vertical" :model="dictionaryForm">
        <a-form-item
          name="key"
          :label="t('dictionaryKey')"
          :rules="[
            { required: true, message: t('required') },
            {
              pattern: /^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+$/,
              message: t('required'),
            },
          ]"
        >
          <a-input
            v-model:value="dictionaryForm.key"
            :disabled="editingDictionary !== null"
          />
        </a-form-item>
        <a-form-item
          name="label"
          :label="t('dictionaryLabel')"
          :rules="[{ required: true, message: t('required') }]"
        >
          <a-input v-model:value="dictionaryForm.label" />
        </a-form-item>
        <a-form-item name="enabled" :label="t('status')">
          <a-switch
            v-model:checked="dictionaryForm.enabled"
            :checked-children="t('active')"
            :un-checked-children="t('disabled')"
          />
        </a-form-item>
        <a-form-item name="sortOrder" :label="t('sortOrder')">
          <a-input-number
            v-model:value="dictionaryForm.sortOrder"
            :max="1_000_000"
            :min="0"
            class="w-full"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-drawer
      v-model:open="optionDrawerOpen"
      :title="optionDictionary?.label ?? t('manageOptions')"
      :width="860"
    >
      <template #extra>
        <a-button
          v-if="canManage && optionDictionary?.optionsEditable"
          type="primary"
          @click="openOptionModal()"
        >
          <Plus :size="17" aria-hidden="true" />{{ t('createOption') }}
        </a-button>
      </template>
      <a-table
        v-if="optionDictionary"
        :columns="optionColumns"
        :data-source="optionDictionary.options"
        :pagination="false"
        row-key="value"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <a-tag
            v-if="column.key === 'source'"
            :color="record.codeDefined ? 'blue' : 'cyan'"
          >
            {{ record.codeDefined ? t('codeOption') : t('databaseOption') }}
          </a-tag>
          <a-tag
            v-else-if="column.key === 'enabled'"
            :color="record.enabled ? 'green' : 'default'"
          >
            {{ record.enabled ? t('active') : t('disabled') }}
          </a-tag>
          <div
            v-else-if="column.key === 'actions'"
            class="flex justify-center gap-1"
          >
            <a-tooltip :title="t('editOption')">
              <a-button
                type="text"
                :aria-label="t('editOption')"
                :disabled="!canManage || record.codeDefined"
                @click="openOptionModal(record)"
              >
                <Pencil :size="17" aria-hidden="true" />
              </a-button>
            </a-tooltip>
            <a-popconfirm
              :title="t('confirmDeleteOption')"
              :ok-text="t('remove')"
              :cancel-text="t('cancel')"
              @confirm="removeOption(optionDictionary, record)"
            >
              <a-tooltip :title="t('deleteOption')">
                <a-button
                  type="text"
                  danger
                  :aria-label="t('deleteOption')"
                  :disabled="!canManage || record.codeDefined"
                >
                  <Trash2 :size="17" aria-hidden="true" />
                </a-button>
              </a-tooltip>
            </a-popconfirm>
          </div>
        </template>
      </a-table>
    </a-drawer>

    <a-modal
      v-model:open="optionModalOpen"
      :cancel-text="t('cancel')"
      :confirm-loading="optionSaving"
      :ok-text="t('save')"
      :title="editingOption ? t('editOption') : t('createOption')"
      @ok="saveOption"
    >
      <a-form ref="optionFormRef" layout="vertical" :model="optionForm">
        <a-form-item
          name="value"
          :label="t('optionValue')"
          :rules="[
            { required: true, message: t('required') },
            {
              pattern: /^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$/,
              message: t('required'),
            },
          ]"
        >
          <a-input
            v-model:value="optionForm.value"
            :disabled="editingOption !== null"
          />
        </a-form-item>
        <a-form-item
          name="label"
          :label="t('optionLabel')"
          :rules="[{ required: true, message: t('required') }]"
        >
          <a-input v-model:value="optionForm.label" />
        </a-form-item>
        <a-form-item
          v-for="locale in supportedLocales"
          :key="locale"
          :name="['labels', locale]"
          :label="t('optionLabelForLocale', { locale })"
        >
          <a-input
            v-model:value="optionForm.labels[locale]"
            :maxlength="160"
            show-count
          />
        </a-form-item>
        <a-form-item name="enabled" :label="t('status')">
          <a-switch
            v-model:checked="optionForm.enabled"
            :checked-children="t('active')"
            :un-checked-children="t('disabled')"
          />
        </a-form-item>
        <a-form-item name="sortOrder" :label="t('sortOrder')">
          <a-input-number
            v-model:value="optionForm.sortOrder"
            :max="1_000_000"
            :min="0"
            class="w-full"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </Page>
</template>
