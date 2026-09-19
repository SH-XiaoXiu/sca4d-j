<script setup lang="ts">
import type { SystemSetting } from '@sca4d/admin';
import type { FormInstance } from 'ant-design-vue';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { message } from 'ant-design-vue';
import { Pencil, RefreshCw, RotateCcw, Settings2 } from 'lucide-vue-next';

import ResourceState from '../components/ResourceState.vue';
import { useI18n } from '../i18n';
import { useAdminStore } from '../store/business';

const store = useAdminStore();
const { errorText, t } = useI18n();
const settingModalOpen = ref(false);
const settingSaving = ref(false);
const settingFormRef = ref<FormInstance>();
const editingSetting = ref<null | SystemSetting>(null);
const settingForm = reactive({ value: '' });

const canManage = computed(() => store.session.capabilities.settingsManage);
const settingColumns = computed(() => [
  {
    key: 'setting',
    title: t('settings'),
  },
  {
    key: 'value',
    title: t('settingsValue'),
  },
  {
    key: 'default',
    title: t('defaultValue'),
  },
  {
    key: 'source',
    title: t('settingSource'),
    width: 180,
  },
  {
    key: 'actions',
    title: t('actions'),
    width: 116,
  },
]);

onMounted(() => void reload());

/** 刷新当前应用已注册的设置分组。 */
async function reload(): Promise<void> {
  try {
    await store.loadSystemSettings();
  } catch {
    // Controller 已保存统一错误和加载阶段。
  }
}

/** 打开设置覆盖窗口；敏感设置不回填当前值。 */
function openSettingModal(setting: SystemSetting): void {
  editingSetting.value = setting;
  settingForm.value = setting.secret
    ? ''
    : settingInputValue(setting.value ?? setting.defaultValue);
  settingModalOpen.value = true;
}

/** 把文本提交给后端对应 SettingCodec 校验和保存。 */
async function saveSetting(): Promise<void> {
  const setting = editingSetting.value;
  if (!setting) return;
  await settingFormRef.value?.validate();
  settingSaving.value = true;
  try {
    const value: unknown =
      setting.valueType === 'object'
        ? JSON.parse(settingForm.value)
        : settingForm.value;
    await store.replaceSystemSetting(setting, value);
    settingModalOpen.value = false;
    message.success(t('saveSuccess'));
  } catch {
    message.error(errorText(store.systemSettings.error));
  } finally {
    settingSaving.value = false;
  }
}

/** 删除数据库覆盖值并恢复 Java 默认值。 */
async function resetSetting(setting: SystemSetting): Promise<void> {
  try {
    await store.resetSystemSetting(setting);
    message.success(t('updateSuccess'));
  } catch {
    message.error(errorText(store.systemSettings.error));
  }
}

/** 把设置 JSON 值转换为表单和表格可以稳定展示的文本。 */
function settingInputValue(value: unknown): string {
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
}
</script>

<template>
  <Page auto-content-height>
    <ResourceState
      :error="store.systemSettings.error"
      :phase="store.systemSettings.phase"
      @retry="reload"
    >
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-2 text-base font-medium">
          <Settings2 :size="19" aria-hidden="true" />
          <span>{{ t('settings') }}</span>
        </div>
        <a-tooltip :title="t('refresh')">
          <a-button :aria-label="t('refresh')" @click="reload">
            <RefreshCw :size="17" aria-hidden="true" />
          </a-button>
        </a-tooltip>
      </div>

      <a-collapse
        :bordered="false"
        class="bg-transparent"
        :default-active-key="
          store.systemSettings.groups.map((group) => group.key)
        "
      >
        <a-collapse-panel
          v-for="group in store.systemSettings.groups"
          :key="group.key"
          :header="group.label"
        >
          <a-table
            :columns="settingColumns"
            :data-source="group.settings"
            :pagination="false"
            row-key="key"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <div
                v-if="column.key === 'setting'"
                class="flex min-w-0 flex-col gap-0.5"
              >
                <strong class="truncate">{{ record.label }}</strong>
                <span class="text-muted-foreground truncate text-xs">{{
                  record.description
                }}</span>
                <span class="text-muted-foreground font-mono text-xs">{{
                  record.key
                }}</span>
              </div>
              <template v-else-if="column.key === 'value'">
                <span v-if="record.secret">{{ t('hiddenValue') }}</span>
                <span v-else class="font-mono text-xs">{{
                  settingInputValue(record.value ?? record.defaultValue)
                }}</span>
              </template>
              <template v-else-if="column.key === 'default'">
                <span v-if="record.secret">{{ t('hiddenValue') }}</span>
                <span v-else class="font-mono text-xs">{{
                  settingInputValue(record.defaultValue)
                }}</span>
              </template>
              <div
                v-else-if="column.key === 'source'"
                class="flex flex-wrap gap-1"
              >
                <a-tag v-if="record.secret" color="gold">
                  {{ t('secretSetting') }}
                </a-tag>
                <a-tag v-if="record.readOnly" color="orange">
                  {{ t('environmentSynchronized') }}
                </a-tag>
                <a-tag v-else-if="record.stored" color="cyan">
                  {{ t('storedValue') }}
                </a-tag>
                <a-tag v-else color="default">
                  {{ t('defaultSetting') }}
                </a-tag>
              </div>
              <div
                v-else-if="column.key === 'actions'"
                class="flex justify-center gap-1"
              >
                <a-tooltip :title="t('editSetting')">
                  <a-button
                    type="text"
                    :aria-label="t('editSetting')"
                    :disabled="!canManage || record.readOnly"
                    @click="openSettingModal(record)"
                  >
                    <Pencil :size="17" aria-hidden="true" />
                  </a-button>
                </a-tooltip>
                <a-popconfirm
                  :title="t('confirmResetSetting')"
                  :ok-text="t('resetSetting')"
                  :cancel-text="t('cancel')"
                  @confirm="resetSetting(record)"
                >
                  <a-tooltip :title="t('resetSetting')">
                    <a-button
                      type="text"
                      :aria-label="t('resetSetting')"
                      :disabled="
                        !canManage || record.readOnly || !record.stored
                      "
                    >
                      <RotateCcw :size="17" aria-hidden="true" />
                    </a-button>
                  </a-tooltip>
                </a-popconfirm>
              </div>
            </template>
          </a-table>
        </a-collapse-panel>
      </a-collapse>
    </ResourceState>

    <a-modal
      v-model:open="settingModalOpen"
      :cancel-text="t('cancel')"
      :confirm-loading="settingSaving"
      :ok-text="t('save')"
      :title="t('editSetting')"
      @ok="saveSetting"
    >
      <a-form ref="settingFormRef" layout="vertical" :model="settingForm">
        <a-form-item :label="editingSetting?.label">
          <a-input :value="editingSetting?.key" disabled />
        </a-form-item>
        <a-form-item :label="t('valueType')">
          <a-tag color="blue">{{ editingSetting?.valueType }}</a-tag>
        </a-form-item>
        <a-form-item
          name="value"
          :label="t('settingsValue')"
          :rules="[{ required: true, message: t('required') }]"
        >
          <a-input-password
            v-if="editingSetting?.secret"
            v-model:value="settingForm.value"
          />
          <a-textarea
            v-else-if="editingSetting?.valueType === 'object'"
            v-model:value="settingForm.value"
            :auto-size="{ minRows: 5, maxRows: 14 }"
          />
          <a-input v-else v-model:value="settingForm.value" />
        </a-form-item>
      </a-form>
    </a-modal>
  </Page>
</template>
