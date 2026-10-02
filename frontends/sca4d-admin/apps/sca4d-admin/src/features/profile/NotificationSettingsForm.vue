<script setup lang="ts">
import type { Component } from 'vue';

import { computed, onMounted, reactive, ref, useId } from 'vue';

import { message } from 'ant-design-vue';
import { Check, Inbox, Mail, MessageSquare, Save } from 'lucide-vue-next';

import { SjResourceState, SjSection } from '#/components/sj';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjDraftGuard } from '#/features/sj-draft-guard';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const { t, language, errorText } = useSjPresentation();
const settings = useSjControllerState(businessApplication.notificationSettings);
const baseline = ref('');
const saving = ref(false);
const fieldId = useId();
const form = reactive({
  languageTag: '',
  channels: {} as Record<string, Record<string, boolean>>,
});
const dirty = computed(
  () => Boolean(baseline.value) && JSON.stringify(form) !== baseline.value,
);
useSjDraftGuard(
  () => dirty.value,
  () => saving.value,
);
const localeOptions = computed(() => {
  const names = new Intl.DisplayNames([language.value], { type: 'language' });
  const labels: Readonly<Record<string, string>> = {
    'zh-CN': t('simplifiedChinese'),
    'en-US': t('englishLanguage'),
  };
  return (settings.value.localeCatalog?.supportedLocales ?? []).map(
    (value) => ({
      value,
      label: labels[value] ?? names.of(value) ?? value,
    }),
  );
});
const regionLabel = computed(() =>
  settings.value.settings?.regionCode === 'GLOBAL'
    ? t('defaultBusinessRegion')
    : t('configuredBusinessRegion'),
);
const definitions = computed(() => {
  const labels: Readonly<
    Record<string, { description: string; title: string }>
  > = {
    'AUTHENTICATION/EMAIL_CODE': {
      title: t('emailVerificationNotification'),
      description: t('emailVerificationNotificationDescription'),
    },
    'AUTHENTICATION/SMS_CODE': {
      title: t('smsVerificationNotification'),
      description: t('smsVerificationNotificationDescription'),
    },
  };
  const channelLabels: Readonly<Record<string, string>> = {
    'NOTIFICATION/CHANNEL/EMAIL': t('emailChannel'),
    'NOTIFICATION/CHANNEL/SMS': t('smsChannel'),
    'NOTIFICATION/CHANNEL/INBOX': t('inboxChannel'),
  };
  const channelIcons: Readonly<Record<string, Component>> = {
    'NOTIFICATION/CHANNEL/EMAIL': Mail,
    'NOTIFICATION/CHANNEL/SMS': MessageSquare,
    'NOTIFICATION/CHANNEL/INBOX': Inbox,
  };
  return Object.entries(settings.value.settings?.definitions ?? {}).map(
    ([code, channels]) => ({
      code,
      title: labels[code]?.title ?? t('otherNotification'),
      description: labels[code]?.description,
      channels: Object.entries(channels).map(([channel, definition]) => ({
        channel,
        definition,
        id: `${fieldId}-${code}-${channel}`,
        label: channelLabels[channel] ?? t('otherChannel'),
        icon: channelIcons[channel] ?? Mail,
      })),
    }),
  );
});
onMounted(() => {
  void load();
});
/** 显式重新读取建立草稿基线，冲突后由用户确认是否放弃输入。 */
async function load() {
  try {
    await businessApplication.notificationSettings.load();
    const value = settings.value.settings;
    if (!value || dirty.value) return;
    form.languageTag = value.languageTag;
    form.channels = structuredClone(value.channels);
    baseline.value = JSON.stringify(form);
  } catch {
    /* SjResourceState 展示读取失败，并保留用户输入。 */
  }
}
/** 可选渠道未保存覆盖值时使用服务端声明的默认值。 */
function enabled(
  code: string,
  channel: string,
  definition: { defaultEnabled?: boolean },
) {
  return form.channels[code]?.[channel] ?? definition.defaultEnabled ?? false;
}
/** 只修改可选渠道，必发渠道不写入用户覆盖值。 */
function setChannel(code: string, channel: string, value: boolean) {
  form.channels[code] = { ...form.channels[code], [channel]: value };
}
/** 保存成功字段立即使用 Controller 返回的新版本；失败时保留草稿。 */
async function save() {
  if (saving.value || !settings.value.settings || !dirty.value) return;
  saving.value = true;
  try {
    if (form.languageTag !== settings.value.settings.languageTag)
      await businessApplication.notificationSettings.replaceLanguage(
        form.languageTag,
      );
    if (
      JSON.stringify(form.channels) !==
      JSON.stringify(settings.value.settings!.channels)
    )
      await businessApplication.notificationSettings.replaceChannels(
        form.channels,
      );
    baseline.value = JSON.stringify(form);
    message.success(t('notificationSettingsSaved'));
  } catch (error) {
    message.error(errorText(error));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <SjSection :title="t('notificationSettings')">
    <SjResourceState
      :phase="settings.phase"
      :error="settings.error"
      :has-data="Boolean(settings.settings)"
      @retry="load"
    >
      <a-form class="max-w-3xl" layout="vertical" :model="form" @finish="save">
        <div class="grid grid-cols-1 gap-x-6 sm:grid-cols-2">
          <a-form-item
            name="languageTag"
            :label="t('notificationLanguage')"
            :rules="[{ required: true, message: t('required') }]"
          >
            <a-select
              v-model:value="form.languageTag"
              :options="localeOptions"
              :disabled="saving"
            />
          </a-form-item>
          <div class="mb-6">
            <p class="mb-2 text-sm">{{ t('notificationRegion') }}</p>
            <p class="flex min-h-8 items-center text-sm text-muted-foreground">
              {{ regionLabel }}
            </p>
          </div>
        </div>
        <p
          v-if="
            definitions.some((item) =>
              item.channels.some((channel) => channel.definition.required),
            )
          "
          class="mb-1 text-sm text-muted-foreground"
        >
          {{ t('requiredNotificationDescription') }}
        </p>
        <ul
          v-if="definitions.length > 0"
          class="m-0 list-none divide-y divide-border p-0"
        >
          <li
            v-for="item in definitions"
            :key="item.code"
            class="flex min-w-0 flex-col gap-4 py-5 sm:flex-row sm:items-center sm:justify-between"
          >
            <div class="min-w-0">
              <h3 class="m-0 text-sm font-medium">{{ item.title }}</h3>
              <p
                v-if="item.description"
                class="mt-1 mb-0 text-sm text-muted-foreground"
              >
                {{ item.description }}
              </p>
            </div>
            <div class="flex shrink-0 flex-wrap items-center gap-4">
              <div
                v-for="channel in item.channels"
                :key="channel.channel"
                class="flex items-center gap-2 text-sm"
              >
                <component
                  :is="channel.icon"
                  :size="16"
                  class="text-muted-foreground"
                  aria-hidden="true"
                />
                <span :id="channel.id">{{ channel.label }}</span>
                <span
                  v-if="channel.definition.required"
                  class="inline-flex items-center gap-1 rounded bg-muted px-2 py-1 text-xs text-muted-foreground"
                >
                  <Check :size="13" aria-hidden="true" />
                  {{ t('alwaysSend') }}
                </span>
                <a-switch
                  v-else
                  :checked="
                    enabled(item.code, channel.channel, channel.definition)
                  "
                  :disabled="saving"
                  :aria-labelledby="channel.id"
                  @change="
                    setChannel(item.code, channel.channel, Boolean($event))
                  "
                />
              </div>
            </div>
          </li>
        </ul>
        <a-empty v-else :description="t('noOptionalNotificationChannels')" />
        <a-button
          class="mt-4"
          type="primary"
          html-type="submit"
          :loading="saving"
          :disabled="!dirty"
        >
          <Save :size="17" aria-hidden="true" />
          {{ t('saveNotificationSettings') }}
        </a-button>
      </a-form>
    </SjResourceState>
  </SjSection>
</template>
