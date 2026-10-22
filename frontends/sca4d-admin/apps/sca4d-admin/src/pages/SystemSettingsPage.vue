<script setup lang="ts">
import type { SystemSetting } from '@sca4d/admin';

import { computed, onMounted, ref } from 'vue';

import { message } from 'ant-design-vue';
import { Pencil, RefreshCw, RotateCcw, Settings2 } from 'lucide-vue-next';

import { SjDataGrid, SjPage, SjResourceState } from '#/components/sj';
import { SjRowActions } from '#/components/sj';
import SystemSettingEditor from '#/features/settings/SystemSettingEditor.vue';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t as sjTranslate } from '#/locales';
import { businessApplication } from '#/platform/business-application';

const settingEditor = ref<InstanceType<typeof SystemSettingEditor>>();

const { t, errorText } = useSjPresentation();

const systemSettings = useSjControllerState(businessApplication.systemSettings);
const session = useSjAccess();

const canManage = computed(() => session.value.capabilities.settingsManage);
const settingColumns = computed(() => [
  {
    field: 'setting',
    title: t('settings'),
  },
  {
    field: 'value',
    title: t('settingsValue'),
  },
  {
    field: 'default',
    title: t('defaultValue'),
  },
  {
    field: 'source',
    title: t('settingSource'),
    width: 180,
  },
  {
    field: 'actions',
    title: t('actions'),
    width: 116,
  },
]);

onMounted(() => void reload());

/** 刷新当前应用已注册的设置分组。 */
async function reload(): Promise<void> {
  try {
    await businessApplication.systemSettings.load();
  } catch {
    // Controller 已保存统一错误和加载阶段。
  }
}

/** 删除数据库覆盖值并恢复 Java 默认值。 */
async function resetSetting(setting: SystemSetting): Promise<void> {
  try {
    await businessApplication.systemSettings.reset(setting);
    message.success(t('updateSuccess'));
  } catch {
    message.error(errorText(systemSettings.value.error));
  }
}

/** 把设置 JSON 值转换为表单和表格可以稳定展示的文本。 */
function settingInputValue(value: unknown): string {
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
}
</script>

<template>
  <SjPage auto-content-height>
    <SjResourceState
      :has-data="systemSettings.groups.length > 0"
      :error="systemSettings.error"
      :phase="systemSettings.phase"
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
        :default-active-key="systemSettings.groups.map((group) => group.key)"
      >
        <a-collapse-panel
          v-for="group in systemSettings.groups"
          :key="group.key"
          :header="group.label"
        >
          <SjDataGrid
            :columns="settingColumns"
            :items="group.settings"
            key-field="key"
          >
            <template #cell="{ column, row, value }">
              <div
                v-if="column.field === 'setting'"
                class="flex min-w-0 flex-col gap-0.5"
              >
                <strong class="truncate">{{ row.label }}</strong>
                <span class="text-muted-foreground truncate text-xs">
                  {{ row.description }}
                </span>
                <span class="text-muted-foreground font-mono text-xs">
                  {{ row.key }}
                </span>
              </div>
              <template v-else-if="column.field === 'value'">
                <span v-if="row.secret">{{ t('hiddenValue') }}</span>
                <span v-else class="font-mono text-xs">
                  {{ settingInputValue(row.value ?? row.defaultValue) }}
                </span>
              </template>
              <template v-else-if="column.field === 'default'">
                <span v-if="row.secret">{{ t('hiddenValue') }}</span>
                <span v-else class="font-mono text-xs">
                  {{ settingInputValue(row.defaultValue) }}
                </span>
              </template>
              <div
                v-else-if="column.field === 'source'"
                class="flex flex-wrap gap-1"
              >
                <a-tag v-if="row.secret" color="gold">
                  {{ t('secretSetting') }}
                </a-tag>
                <a-tag v-if="row.readOnly" color="orange">
                  {{ t('environmentSynchronized') }}
                </a-tag>
                <a-tag v-else-if="row.stored" color="cyan">
                  {{ t('storedValue') }}
                </a-tag>
                <a-tag v-else color="default">
                  {{ t('defaultSetting') }}
                </a-tag>
              </div>
              <SjRowActions
                v-else-if="column.field === 'actions'"
                :actions="[
                  {
                    key: 'action-0',
                    label: t('editSetting'),
                    icon: Pencil,
                    run: async () => {
                      await settingEditor?.open(row);
                    },
                    disabled: !canManage || row.readOnly,
                    disabledReason: sjTranslate('sj.actionUnavailable'),
                  },
                  {
                    key: 'action-1',
                    label: t('resetSetting'),
                    icon: RotateCcw,
                    run: async () => {
                      await resetSetting(row);
                    },
                    disabled: !canManage || row.readOnly || !row.stored,
                    disabledReason: sjTranslate('sj.actionUnavailable'),
                    confirm: {
                      title: t('confirmResetSetting'),
                      content: row.label,
                      okText: t('resetSetting'),
                    },
                  },
                ]"
              />
              <span
                v-if="
                  ![
                    'actions',
                    'default',
                    'setting',
                    'source',
                    'value',
                  ].includes(column.field)
                "
              >
                {{ value ?? '-' }}
              </span>
            </template>
          </SjDataGrid>
        </a-collapse-panel>
      </a-collapse>
    </SjResourceState>
  </SjPage>

  <SystemSettingEditor ref="settingEditor" />
</template>

<!-- 系统设置编辑 -->
