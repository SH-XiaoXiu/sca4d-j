<script setup lang="ts">
import type { Int64String } from '@sca4d/admin';

import { onScopeDispose, ref } from 'vue';

import { message } from 'ant-design-vue';
import { Camera, Save, Trash2, X } from 'lucide-vue-next';

import { uploadSjAvatar, useSjFileAccessUrl } from '#/features/sj-files';
import { useSjPresentation } from '#/features/sj-presentation';

import SjAvatar from './SjAvatar.vue';
/** 头像选择、预览、签名上传与清理；资源绑定行为由调用方明确提供。 */
const props = defineProps<{
  /** 上传头像所属 Account。 */ accountId: Int64String;
  /** 当前已绑定的文件。 */ editable?: boolean;
  /** 占位名称。 */ fileId: Int64String | null;
  /** 当前对象是否允许维护。 */ name: string;
  /** 将上传完成的文件绑定到目标资源。 */ remove: () => Promise<unknown>;
  /** 删除当前资源的头像绑定。 */ replace: (
    fileId: Int64String,
  ) => Promise<unknown>;
}>();
const emit = defineEmits<{
  /** 绑定变化后通知调用方同步相关摘要。 */ changed: [];
}>();
const { t, errorText } = useSjPresentation();
const { url, retry } = useSjFileAccessUrl(() => props.fileId);
const input = ref<HTMLInputElement>();
const selected = ref<File>();
const preview = ref<string>();
const saving = ref(false);
let active = true;
onScopeDispose(() => {
  active = false;
  clear();
});
/** 释放本地预览，防止已关闭的工作区继续保留浏览器文件。 */
function clear() {
  if (preview.value) URL.revokeObjectURL(preview.value);
  selected.value = undefined;
  preview.value = undefined;
}
/** 校验媒体类型和大小后才创建预览。 */
function select(event: Event) {
  const target = event.target;
  if (!(target instanceof HTMLInputElement)) return;
  const file = target.files?.[0];
  target.value = '';
  if (!file) return;
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    message.warning(t('avatarInvalidType'));
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    message.warning(t('avatarTooLarge'));
    return;
  }
  clear();
  selected.value = file;
  preview.value = URL.createObjectURL(file);
}
/** 完成上传和绑定；关闭后不再发布当前工作区的反馈。 */
async function save() {
  const file = selected.value;
  if (!file || saving.value) return;
  saving.value = true;
  try {
    const id = await uploadSjAvatar(props.accountId, file);
    if (!active) return;
    await props.replace(id);
    if (active) {
      clear();
      emit('changed');
      message.success(t('avatarUpdated'));
    }
  } catch (error) {
    if (active) message.error(errorText(error));
  } finally {
    saving.value = false;
  }
}
/** 删除绑定，不改动调用方的其他草稿。 */
async function removeAvatar() {
  if (saving.value) return;
  saving.value = true;
  try {
    await props.remove();
    if (active) {
      clear();
      emit('changed');
      message.success(t('avatarDeleted'));
    }
  } catch (error) {
    if (active) message.error(errorText(error));
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <section
    class="mb-6 flex flex-wrap items-center gap-5 border-b border-border pb-6"
  >
    <button
      type="button"
      class="group relative rounded-full focus-visible:ring-2 focus-visible:ring-ring"
      :disabled="!editable || saving"
      :aria-label="t('chooseAvatar')"
      @click="input?.click()"
    >
      <SjAvatar
        :name="name"
        :src="preview || url"
        :size="88"
        @image-error="retry"
      />
      <Camera
        v-if="editable"
        class="absolute bottom-0 right-0 rounded-full bg-card p-1"
        :size="24"
        aria-hidden="true"
      />
    </button>
    <div class="flex min-w-0 flex-col gap-2">
      <strong>{{ name }}</strong>
      <small class="text-muted-foreground">
        {{ selected?.name || t('avatarRequirements') }}
      </small>
      <input
        ref="input"
        class="hidden"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        @change="select"
      />
      <div v-if="editable" class="flex gap-2">
        <a-button
          v-if="selected"
          type="primary"
          :loading="saving"
          @click="save"
        >
          <Save :size="17" aria-hidden="true" />
          {{ t('useAvatar') }}
        </a-button>
        <a-button
          v-if="selected"
          :disabled="saving"
          :aria-label="t('cancel')"
          @click="clear"
        >
          <X :size="17" aria-hidden="true" />
        </a-button>
        <a-popconfirm
          v-if="fileId && !selected"
          :title="t('confirmDeleteAvatar')"
          :ok-text="t('remove')"
          :cancel-text="t('cancel')"
          @confirm="removeAvatar"
        >
          <a-button danger :loading="saving">
            <Trash2 :size="17" aria-hidden="true" />
            {{ t('removeAvatar') }}
          </a-button>
        </a-popconfirm>
      </div>
    </div>
  </section>
</template>
