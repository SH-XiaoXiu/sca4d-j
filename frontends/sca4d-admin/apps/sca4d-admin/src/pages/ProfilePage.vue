<script setup lang="ts">
import type { Int64String } from '@sca4d/admin';
import type { NotificationSettings } from '#/api/core/notifications';
import type { FormInstance } from 'ant-design-vue';

import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { AdminError } from '@sca4d/admin';
import { message } from 'ant-design-vue';
import { Camera, Save, Trash2, X } from 'lucide-vue-next';

import {
  getNotificationSettingsApi,
  replaceNotificationChannelsApi,
  replaceNotificationLanguageApi,
  replaceNotificationRegionApi,
} from '#/api/core/notifications';

import AvatarDisplay from '../components/AvatarDisplay.vue';
import ResourceState from '../components/ResourceState.vue';
import ActionVerificationDialog from '#/features/authentication/ActionVerificationDialog.vue';
import { securityActionSuccessText } from '#/features/authentication/presentation';
import { useI18n } from '../i18n';
import { useAdminStore } from '../store/business';
import { useSecurityStore } from '#/store/security';

const store = useAdminStore();
const securityStore = useSecurityStore();
const userStore = useUserStore();
const { t, errorText } = useI18n();
const formRef = ref<FormInstance>();
const saving = ref(false);
const notificationSaving = ref(false);
const notificationSettings = ref<NotificationSettings | null>(null);
const avatarInput = ref<HTMLInputElement>();
const selectedAvatar = ref<File | null>(null);
const avatarPreviewUrl = ref<null | string>(null);
const avatarUrl = ref<null | string>(null);
const avatarSaving = ref(false);
const avatarDeleting = ref(false);
const retriedAvatarFileId = ref<Int64String | null>(null);
const form = reactive({ displayName: '', email: '', phoneNumber: '' });
const notificationForm = reactive({
  channels: {} as Record<string, Record<string, boolean>>,
  languageTag: 'zh-CN',
  regionCode: 'GLOBAL',
});
const notificationTypes = computed(() =>
  Object.entries(notificationSettings.value?.definitions ?? {}),
);
const avatarTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);
const avatarMaxSize = 5 * 1024 * 1024;

watch(
  () => store.profile.profile,
  (profile) => {
    if (!profile) return;
    form.displayName = profile.displayName;
    form.email = profile.email ?? '';
    form.phoneNumber = profile.phoneNumber ?? '';
    void loadAvatar(profile.avatarFileId);
  },
  { immediate: true },
);
watch(
  () => securityStore.completedAction,
  (action) => {
    if (action !== 'phone.verify') return;
    message.success(securityActionSuccessText(action));
    securityStore.consumeCompletedAction();
  },
);
onMounted(() => void load());
onBeforeUnmount(clearSelectedAvatar);

/** 读取当前账号资料。 */
async function load(): Promise<void> {
  try {
    const [, settings] = await Promise.all([
      store.loadProfile(),
      getNotificationSettingsApi(),
    ]);
    notificationSettings.value = settings;
    notificationForm.languageTag = settings.languageTag;
    notificationForm.regionCode = settings.regionCode;
    notificationForm.channels = structuredClone(settings.channels);
  } catch {
    // Controller 已发布统一错误状态。
  }
}

/** 完整保存通知语言、业务区域和全部可选渠道覆盖值。 */
async function saveNotificationSettings(): Promise<void> {
  let current = notificationSettings.value;
  if (!current) return;
  notificationSaving.value = true;
  try {
    const languageTag = notificationForm.languageTag.trim();
    const regionCode = notificationForm.regionCode.trim();
    if (languageTag !== current.languageTag) {
      current = await replaceNotificationLanguageApi(current, languageTag);
    }
    if (regionCode !== current.regionCode) {
      current = await replaceNotificationRegionApi(current, regionCode);
    }
    if (
      JSON.stringify(notificationForm.channels) !==
      JSON.stringify(current.channels)
    ) {
      current = await replaceNotificationChannelsApi(
        current,
        notificationForm.channels,
      );
    }
    notificationSettings.value = current;
    notificationForm.channels = structuredClone(current.channels);
    message.success(t('notificationSettingsSaved'));
  } catch {
    message.error(t('notificationSettingsSaveFailed'));
  } finally {
    notificationSaving.value = false;
  }
}

/** 使用 SMS Code 验证并绑定当前 Profile 手机号。 */
async function verifyPhone(): Promise<void> {
  if (!form.phoneNumber.trim()) return;
  try {
    await securityStore.verifyPhone();
  } catch {
    message.error(t('phoneVerificationFailed'));
  }
}

/** 返回一个通知类型渠道的覆盖值或 Java 默认值。 */
function channelEnabled(
  notificationCode: string,
  channel: string,
  definition: { defaultEnabled?: boolean; required: boolean },
): boolean {
  if (definition.required) return true;
  return (
    notificationForm.channels[notificationCode]?.[channel] ??
    definition.defaultEnabled ??
    false
  );
}

/** 写入一个可选渠道的 Account 覆盖值。 */
function setChannelEnabled(
  notificationCode: string,
  channel: string,
  enabled: boolean,
): void {
  notificationForm.channels[notificationCode] = {
    ...(notificationForm.channels[notificationCode] ?? {}),
    [channel]: enabled,
  };
}

/** 校验并使用当前资料版本完整替换资料。 */
async function save(): Promise<void> {
  await formRef.value?.validate();
  saving.value = true;
  try {
    await store.saveProfile(
      form.displayName.trim(),
      form.email.trim() || null,
      form.phoneNumber.trim() || null,
    );
    message.success(t('saveSuccess'));
  } catch {
    message.error(errorText(store.profile.error));
  } finally {
    saving.value = false;
  }
}

/** 打开头像文件选择器，并允许再次选择同一个文件。 */
function chooseAvatar(): void {
  if (!avatarInput.value) return;
  avatarInput.value.value = '';
  avatarInput.value.click();
}

/** 校验头像媒体类型和大小，并生成只在确认前使用的本地预览。 */
function stageAvatar(event: Event): void {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  if (!avatarTypes.has(file.type)) {
    message.warning(t('avatarInvalidType'));
    return;
  }
  if (file.size > avatarMaxSize) {
    message.warning(t('avatarTooLarge'));
    return;
  }
  clearSelectedAvatar();
  selectedAvatar.value = file;
  avatarPreviewUrl.value = URL.createObjectURL(file);
}

/** 上传预览中的文件，完成后把文件绑定为当前账号头像。 */
async function saveAvatar(): Promise<void> {
  const file = selectedAvatar.value;
  const accountId = store.session.session?.account.accountId;
  if (!file || !accountId) return;
  avatarSaving.value = true;
  try {
    const fileId = await store.uploadAvatar(accountId, file);
    await store.replaceProfileAvatar(fileId);
    clearSelectedAvatar();
    retriedAvatarFileId.value = null;
    const resolvedUrls = await store.resolveAvatarUrls([fileId], true);
    syncAvatarUrl(resolvedUrls.get(fileId) ?? null);
    message.success(t('avatarUpdated'));
  } catch (error) {
    message.error(
      error instanceof AdminError ? errorText(error) : t('avatarUploadFailed'),
    );
  } finally {
    avatarSaving.value = false;
  }
}

/** 删除当前账号头像并保留资料表单中的其他修改。 */
async function deleteAvatar(): Promise<void> {
  avatarDeleting.value = true;
  try {
    await store.deleteProfileAvatar();
    clearSelectedAvatar();
    syncAvatarUrl(null);
    retriedAvatarFileId.value = null;
    message.success(t('avatarDeleted'));
  } catch (error) {
    message.error(
      error instanceof AdminError ? errorText(error) : t('avatarDeleteFailed'),
    );
  } finally {
    avatarDeleting.value = false;
  }
}

/** 读取当前头像地址；读取失败时使用固定占位，不阻止资料表单使用。 */
async function loadAvatar(fileId: Int64String | null): Promise<void> {
  if (!fileId) {
    syncAvatarUrl(null);
    retriedAvatarFileId.value = null;
    return;
  }
  try {
    const resolvedUrls = await store.resolveAvatarUrls([fileId]);
    syncAvatarUrl(resolvedUrls.get(fileId) ?? null);
  } catch {
    syncAvatarUrl(null);
  }
}

/** 图片加载失败时只为同一个文件重新申请一次读取地址。 */
async function retryAvatar(): Promise<void> {
  const fileId = store.profile.profile?.avatarFileId ?? null;
  if (!fileId || retriedAvatarFileId.value === fileId) {
    syncAvatarUrl(null);
    return;
  }
  retriedAvatarFileId.value = fileId;
  try {
    const resolvedUrls = await store.resolveAvatarUrls([fileId], true);
    syncAvatarUrl(resolvedUrls.get(fileId) ?? null);
  } catch {
    syncAvatarUrl(null);
  }
}

/** 同步个人资料头像和 Vben 顶部用户菜单。 */
function syncAvatarUrl(value: null | string): void {
  avatarUrl.value = value;
  const current = userStore.userInfo;
  if (current) userStore.setUserInfo({ ...current, avatar: value ?? '' });
}

/** 释放本地预览地址并清除尚未提交的文件。 */
function clearSelectedAvatar(): void {
  if (avatarPreviewUrl.value) URL.revokeObjectURL(avatarPreviewUrl.value);
  avatarPreviewUrl.value = null;
  selectedAvatar.value = null;
}
</script>

<template>
  <Page auto-content-height>
    <a-card :title="t('profile')" :bordered="false" class="w-full">
      <ResourceState
        :phase="store.profile.phase"
        :error="store.profile.error"
        @retry="load"
      >
        <div
          class="mb-6 flex max-w-xl items-center gap-5 border-b pb-6 max-sm:flex-col max-sm:items-start"
        >
          <button
            class="group relative inline-flex shrink-0 overflow-hidden rounded-full border-0 bg-transparent p-0"
            type="button"
            :aria-label="t('chooseAvatar')"
            :disabled="avatarSaving || avatarDeleting"
            @click="chooseAvatar"
          >
            <AvatarDisplay
              :name="store.profile.profile?.displayName || form.displayName"
              :src="avatarPreviewUrl || avatarUrl"
              :size="88"
              @image-error="retryAvatar"
            />
            <span
              class="absolute inset-0 grid place-items-center rounded-full bg-slate-950/60 text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100"
              aria-hidden="true"
            >
              <Camera :size="21" />
            </span>
          </button>
          <div class="flex min-w-0 flex-1 flex-col items-start gap-2">
            <strong>{{ t('avatar') }}</strong>
            <span
              v-if="selectedAvatar"
              class="text-muted-foreground block max-w-full truncate text-xs"
            >
              {{ selectedAvatar.name }}
            </span>
            <div class="flex flex-wrap items-center gap-2">
              <input
                ref="avatarInput"
                class="hidden"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                @change="stageAvatar"
              />
              <a-button
                v-if="selectedAvatar"
                type="primary"
                :loading="avatarSaving"
                @click="saveAvatar"
              >
                <Save :size="17" aria-hidden="true" />{{ t('useAvatar') }}
              </a-button>
              <a-tooltip v-if="selectedAvatar" :title="t('cancel')">
                <a-button
                  class="w-9 px-0"
                  :aria-label="t('cancel')"
                  :disabled="avatarSaving"
                  @click="clearSelectedAvatar"
                >
                  <X :size="17" aria-hidden="true" />
                </a-button>
              </a-tooltip>
              <a-popconfirm
                v-if="store.profile.profile?.avatarFileId && !selectedAvatar"
                :title="t('confirmDeleteAvatar')"
                :ok-text="t('remove')"
                :cancel-text="t('cancel')"
                @confirm="deleteAvatar"
              >
                <a-button danger :loading="avatarDeleting">
                  <Trash2 :size="17" aria-hidden="true" />{{
                    t('removeAvatar')
                  }}
                </a-button>
              </a-popconfirm>
            </div>
            <small class="text-muted-foreground">{{
              t('avatarRequirements')
            }}</small>
          </div>
        </div>
        <a-form
          ref="formRef"
          class="max-w-xl"
          layout="vertical"
          :model="form"
          @finish="save"
        >
          <a-form-item
            name="displayName"
            :label="t('displayName')"
            :rules="[{ required: true, message: t('required') }]"
          >
            <a-input v-model:value="form.displayName" autocomplete="name" />
          </a-form-item>
          <a-form-item
            name="email"
            :label="t('email')"
            :rules="[{ type: 'email', message: t('invalidEmail') }]"
          >
            <a-input v-model:value="form.email" autocomplete="email" />
          </a-form-item>
          <a-form-item
            name="phoneNumber"
            :label="t('phoneNumber')"
            :rules="[
              { pattern: /^\+[1-9]\d{1,14}$/, message: t('invalidPhone') },
            ]"
          >
            <a-input-group compact>
              <a-input
                v-model:value="form.phoneNumber"
                autocomplete="tel"
                style="width: calc(100% - 120px)"
              />
              <a-button
                :disabled="
                  !form.phoneNumber.trim() ||
                  form.phoneNumber.trim() !== store.profile.profile?.phoneNumber
                "
                @click="verifyPhone"
                >{{ t('verifyPhone') }}</a-button
              >
            </a-input-group>
          </a-form-item>
          <a-button type="primary" html-type="submit" :loading="saving">
            <Save :size="17" aria-hidden="true" />{{ t('save') }}
          </a-button>
        </a-form>
      </ResourceState>
    </a-card>
    <a-card
      :title="t('notificationSettings')"
      :bordered="false"
      class="mt-4 w-full"
    >
      <a-form
        class="max-w-2xl"
        layout="vertical"
        :model="notificationForm"
        @finish="saveNotificationSettings"
      >
        <div class="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
          <a-form-item
            name="languageTag"
            :label="t('notificationLanguage')"
            :rules="[{ required: true, message: t('required') }]"
          >
            <a-input
              v-model:value="notificationForm.languageTag"
              placeholder="zh-CN"
            />
          </a-form-item>
          <a-form-item
            name="regionCode"
            :label="t('notificationRegion')"
            :rules="[{ required: true, message: t('required') }]"
          >
            <a-input
              v-model:value="notificationForm.regionCode"
              placeholder="GLOBAL"
            />
          </a-form-item>
        </div>
        <div v-if="notificationTypes.length > 0" class="space-y-4">
          <section
            v-for="[notificationCode, channels] in notificationTypes"
            :key="notificationCode"
            class="rounded border p-4"
          >
            <h3 class="mb-3 font-mono text-sm font-medium">
              {{ notificationCode }}
            </h3>
            <div class="flex flex-wrap gap-5">
              <label
                v-for="(definition, channel) in channels"
                :key="channel"
                class="inline-flex items-center gap-2"
              >
                <a-switch
                  :checked="
                    channelEnabled(notificationCode, channel, definition)
                  "
                  :disabled="definition.required"
                  @change="
                    setChannelEnabled(
                      notificationCode,
                      channel,
                      $event as boolean,
                    )
                  "
                />
                <span>{{ channel }}</span>
                <a-tag v-if="definition.required" color="red">{{
                  t('requiredChannel')
                }}</a-tag>
              </label>
            </div>
          </section>
        </div>
        <a-empty v-else :description="t('noOptionalNotificationChannels')" />
        <a-button
          class="mt-4"
          type="primary"
          html-type="submit"
          :loading="notificationSaving"
        >
          <Save :size="17" aria-hidden="true" />{{
            t('saveNotificationSettings')
          }}
        </a-button>
      </a-form>
    </a-card>
    <ActionVerificationDialog />
  </Page>
</template>
