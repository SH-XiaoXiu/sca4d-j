<script setup lang="ts">
defineOptions({ name: "AvatarCropper" });

import type { Id } from "@sca4d/admin-common";
import { ImagePlus, RotateCcw, RotateCw, ZoomIn, ZoomOut } from "lucide-vue-next";
import { message } from "ant-design-vue";
import { onBeforeUnmount, ref } from "vue";
import { useI18n } from "vue-i18n";
import { CircleStencil, Cropper } from "vue-advanced-cropper";
import "vue-advanced-cropper/dist/style.css";
import { fileApi } from "@/api/client";

const props = withDefaults(defineProps<{
  subjectId?: Id | undefined;
  disabled?: boolean;
}>(), {
  subjectId: undefined,
  disabled: false
});
const emit = defineEmits<{
  uploaded: [value: { fileId: Id; uploadGrant: string; previewUrl: string }];
}>();
defineSlots<{ trigger(): unknown }>();
const { t } = useI18n();
const input = ref<HTMLInputElement | null>(null);
const cropper = ref<{
  getResult: () => { canvas?: HTMLCanvasElement };
  zoom: (factor: number) => void;
  rotate: (angle: number) => void;
} | null>(null);
const open = ref(false);
const sourceUrl = ref<string | null>(null);
const uploading = ref(false);
const stencilSize = ({ boundaries }: { boundaries: { width: number; height: number } }) => {
  const size = Math.round(Math.min(boundaries.width, boundaries.height) * 0.72);
  return { width: size, height: size };
};
const stencilProps = {
  aspectRatio: 1,
  handlers: {},
  lines: {},
  movable: false,
  resizable: false
};
const resizeImage = {
  adjustStencil: false,
  touch: true,
  wheel: { ratio: 0.1 }
};
const moveImage = {
  mouse: true,
  touch: true
};

function revokeSource(): void {
  if (sourceUrl.value) URL.revokeObjectURL(sourceUrl.value);
  sourceUrl.value = null;
}

function chooseFile(): void {
  input.value?.click();
}

function selected(event: Event): void {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  target.value = "";
  if (!file) return;
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
    message.warning(t("avatar.invalidType"));
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    message.warning(t("avatar.tooLarge"));
    return;
  }
  revokeSource();
  sourceUrl.value = URL.createObjectURL(file);
  open.value = true;
}

function close(): void {
  if (uploading.value) return;
  open.value = false;
  revokeSource();
}

async function confirm(): Promise<void> {
  const result = cropper.value?.getResult();
  if (!result?.canvas) {
    message.error(t("avatar.cropFailed"));
    return;
  }
  const output = document.createElement("canvas");
  output.width = 512;
  output.height = 512;
  const context = output.getContext("2d");
  if (!context) {
    message.error(t("avatar.cropFailed"));
    return;
  }
  context.drawImage(result.canvas, 0, 0, 512, 512);
  uploading.value = true;
  try {
    const blob = await new Promise<Blob>((resolve, reject) => {
      output.toBlob(
        (value) => (value ? resolve(value) : reject(new Error("WebP conversion failed"))),
        "image/webp",
        0.9
      );
    });
    const file = new File([blob], `avatar-${Date.now()}.webp`, { type: "image/webp" });
    const ticket = await fileApi.signAvatarUpload(file, props.subjectId);
    await fileApi.upload(ticket, file);
    await fileApi.complete(ticket.fileId);
    const previewUrl = URL.createObjectURL(file);
    emit("uploaded", {
      fileId: ticket.fileId,
      uploadGrant: ticket.uploadGrant,
      previewUrl
    });
    open.value = false;
    revokeSource();
  } catch {
    message.error(t("avatar.uploadFailed"));
  } finally {
    uploading.value = false;
  }
}

onBeforeUnmount(() => {
  revokeSource();
});
</script>

<template>
  <div class="avatar-upload">
    <input
      ref="input"
      class="avatar-file-input"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      :disabled="disabled"
      @change="selected"
    >
    <button
      class="avatar-upload__trigger"
      type="button"
      :disabled="disabled"
      :aria-label="t('avatar.choose')"
      @click="chooseFile"
    >
      <slot name="trigger" />
      <span
        class="avatar-upload__hint"
        aria-hidden="true"
      >
        <ImagePlus :size="22" />
      </span>
    </button>

    <a-modal
      :open="open"
      :title="t('avatar.cropTitle')"
      :width="560"
      :mask-closable="!uploading"
      :closable="!uploading"
      class="avatar-crop-modal"
      @cancel="close"
    >
      <Cropper
        v-if="sourceUrl"
        ref="cropper"
        class="avatar-cropper"
        :src="sourceUrl"
        :stencil-component="CircleStencil"
        :stencil-size="stencilSize"
        :stencil-props="stencilProps"
        :resize-image="resizeImage"
        :move-image="moveImage"
        :transitions="true"
        image-restriction="stencil"
      />
      <div class="avatar-crop-controls">
        <a-tooltip :title="t('avatar.zoomOut')">
          <a-button
            class="avatar-control-button"
            :aria-label="t('avatar.zoomOut')"
            :disabled="uploading"
            @click="cropper?.zoom(0.8)"
          >
            <ZoomOut :size="18" />
          </a-button>
        </a-tooltip>
        <a-tooltip :title="t('avatar.zoomIn')">
          <a-button
            class="avatar-control-button"
            :aria-label="t('avatar.zoomIn')"
            :disabled="uploading"
            @click="cropper?.zoom(1.25)"
          >
            <ZoomIn :size="18" />
          </a-button>
        </a-tooltip>
        <a-tooltip :title="t('avatar.rotateLeft')">
          <a-button
            class="avatar-control-button"
            :aria-label="t('avatar.rotateLeft')"
            :disabled="uploading"
            @click="cropper?.rotate(-90)"
          >
            <RotateCcw :size="18" />
          </a-button>
        </a-tooltip>
        <a-tooltip :title="t('avatar.rotateRight')">
          <a-button
            class="avatar-control-button"
            :aria-label="t('avatar.rotateRight')"
            :disabled="uploading"
            @click="cropper?.rotate(90)"
          >
            <RotateCw :size="18" />
          </a-button>
        </a-tooltip>
      </div>
      <template #footer>
        <a-button
          :disabled="uploading"
          @click="close"
        >
          {{ t("common.cancel") }}
        </a-button>
        <a-button
          type="primary"
          :loading="uploading"
          @click="confirm"
        >
          {{ t("avatar.use") }}
        </a-button>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.avatar-file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

.avatar-upload__trigger {
  position: relative;
  display: inline-grid;
  padding: 0;
  overflow: hidden;
  background: transparent;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

.avatar-upload__trigger:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.avatar-upload__trigger:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 3px;
}

.avatar-upload__hint {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #ffffff;
  background: color-mix(in srgb, #000000 46%, transparent);
  border-radius: 50%;
  opacity: 0;
  transition: opacity 160ms ease;
}

.avatar-upload__trigger:not(:disabled):hover .avatar-upload__hint,
.avatar-upload__trigger:not(:disabled):focus-visible .avatar-upload__hint,
.avatar-upload__trigger:not(:disabled):active .avatar-upload__hint {
  opacity: 1;
}

.avatar-cropper {
  width: 100%;
  height: min(56vh, 420px);
  min-height: 280px;
  background: var(--app-surface-muted);
}

.avatar-crop-controls {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 16px;
}

.avatar-control-button {
  display: inline-flex;
  width: 44px;
  height: 44px;
  padding: 0;
  align-items: center;
  justify-content: center;
}

@media (max-width: 560px) {
  :global(.avatar-crop-modal.ant-modal) {
    width: 340px !important;
  }

  .avatar-cropper {
    height: 50dvh;
    min-height: 260px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .avatar-upload__hint {
    transition: none;
  }
}

@media (max-width: 400px) {
  :global(.avatar-crop-modal.ant-modal) {
    width: 288px !important;
  }
}
</style>
