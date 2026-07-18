<script setup lang="ts">
defineOptions({ name: "ProfilePage" });

import { formatDateTime, isApiError, type Id } from "@sca4d/admin-common";
import { RefreshCw } from "lucide-vue-next";
import { message, Modal } from "ant-design-vue";
import { onBeforeUnmount, ref } from "vue";
import AvatarCropper from "@/components/avatar/AvatarCropper.vue";
import PageHeader from "@/components/page/PageHeader.vue";
import DefaultProfileAvatar from "@/components/data-display/DefaultProfileAvatar.vue";
import { meApi } from "@/api/client";
import { useI18n } from "vue-i18n";
import { useSessionStore } from "@/stores/session";

const auth = useSessionStore();
const { t } = useI18n();
const submitting = ref(false);
const pendingAvatar = ref<{ fileId: Id; uploadGrant: string; previewUrl: string } | null>(null);
const committedAvatarProjection = ref<string | null | undefined>(undefined);
const avatarRefreshRequired = ref(false);

function discardPendingAvatar(): void {
  if (pendingAvatar.value) URL.revokeObjectURL(pendingAvatar.value.previewUrl);
  pendingAvatar.value = null;
}

async function refreshAvatarProfile(notifySuccess = false): Promise<boolean> {
  try {
    await auth.bootstrap(true, auth.selection);
    if (typeof committedAvatarProjection.value === "string") {
      URL.revokeObjectURL(committedAvatarProjection.value);
    }
    committedAvatarProjection.value = undefined;
    avatarRefreshRequired.value = false;
    if (notifySuccess) message.success(t("avatar.refreshed"));
    return true;
  } catch {
    avatarRefreshRequired.value = true;
    message.warning(t("avatar.refreshFailed"));
    return false;
  }
}

async function retryAvatarRefresh(): Promise<void> {
  submitting.value = true;
  try {
    await refreshAvatarProfile(true);
  } finally {
    submitting.value = false;
  }
}

async function usePendingAvatar(): Promise<void> {
  if (!auth.me || !pendingAvatar.value) return;
  const value = pendingAvatar.value;
  submitting.value = true;
  try {
    await meApi.replaceAvatar(value.fileId, value.uploadGrant, auth.me.profileVersion);
  } catch (error) {
    if (isApiError(error) && error.httpStatus === 409) {
      if (await refreshAvatarProfile()) message.warning(t("avatar.conflict"));
    } else {
      message.error(isApiError(error) ? error.message : t("avatar.replaceFailed"));
    }
    submitting.value = false;
    return;
  }
  pendingAvatar.value = null;
  if (typeof committedAvatarProjection.value === "string") {
    URL.revokeObjectURL(committedAvatarProjection.value);
  }
  committedAvatarProjection.value = value.previewUrl;
  auth.avatarUrl = value.previewUrl;
  message.success(t("avatar.replaced"));
  await refreshAvatarProfile();
  submitting.value = false;
}

async function acceptUploadedAvatar(value: { fileId: Id; uploadGrant: string; previewUrl: string }): Promise<void> {
  discardPendingAvatar();
  pendingAvatar.value = value;
  await usePendingAvatar();
}

function removeAvatar(): void {
  if (!auth.me?.avatar) return;
  Modal.confirm({
    title: t("avatar.deleteTitle"),
    content: t("avatar.deleteSelfConfirm"),
    okText: t("avatar.delete"),
    okType: "danger",
    cancelText: t("common.cancel"),
    async onOk() {
      if (!auth.me) return;
      submitting.value = true;
      try {
        await meApi.removeAvatar(auth.me.profileVersion);
      } catch (error) {
        if (isApiError(error) && error.httpStatus === 409) {
          if (await refreshAvatarProfile()) message.warning(t("avatar.conflict"));
        } else {
          message.error(isApiError(error) ? error.message : t("avatar.deleteFailed"));
        }
        submitting.value = false;
        throw error;
      }
      if (typeof committedAvatarProjection.value === "string") {
        URL.revokeObjectURL(committedAvatarProjection.value);
      }
      committedAvatarProjection.value = null;
      auth.avatarUrl = null;
      message.success(t("avatar.deleted"));
      await refreshAvatarProfile();
      submitting.value = false;
    }
  });
}

onBeforeUnmount(() => {
  discardPendingAvatar();
  if (typeof committedAvatarProjection.value === "string") {
    if (auth.avatarUrl === committedAvatarProjection.value) auth.avatarUrl = null;
    URL.revokeObjectURL(committedAvatarProjection.value);
  }
});
</script>

<template>
  <div>
    <PageHeader :title="t('route.profile')" />
    <section class="page-panel account-panel">
      <div class="account-section">
        <h2>{{ t("profile.basic") }}</h2>
        <div class="profile-avatar-editor">
          <AvatarCropper
            :subject-id="auth.subjectId || undefined"
            :disabled="submitting"
            @uploaded="acceptUploadedAvatar"
          >
            <template #trigger>
              <DefaultProfileAvatar
                :name="auth.me?.subject?.displayName || t('common.unavailable')"
                :src="pendingAvatar?.previewUrl || (committedAvatarProjection === undefined ? auth.avatarUrl : committedAvatarProjection)"
                :size="88"
                @load-error="auth.refreshAvatar"
              />
            </template>
          </AvatarCropper>
          <div class="profile-avatar-actions">
            <strong>{{ t("avatar.label") }}</strong>
            <a-space wrap>
              <a-button
                v-if="pendingAvatar"
                type="primary"
                :loading="submitting"
                @click="usePendingAvatar"
              >
                {{ t("avatar.retryUse") }}
              </a-button>
              <a-button
                v-if="pendingAvatar"
                :disabled="submitting"
                @click="discardPendingAvatar"
              >
                {{ t("avatar.discardUpload") }}
              </a-button>
              <a-button
                v-if="avatarRefreshRequired"
                :loading="submitting"
                @click="retryAvatarRefresh"
              >
                <RefreshCw :size="16" />{{ t("avatar.refresh") }}
              </a-button>
              <a-button
                v-if="committedAvatarProjection !== null && !avatarRefreshRequired && auth.me?.avatar"
                danger
                :disabled="submitting"
                @click="removeAvatar"
              >
                {{ t("avatar.delete") }}
              </a-button>
            </a-space>
          </div>
        </div>
        <a-descriptions
          :column="1"
          size="small"
        >
          <a-descriptions-item :label="t('profile.displayName')">
            {{ auth.me?.subject?.displayName || t("common.unavailable") }}
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <a-divider />

      <div class="account-section">
        <h2>{{ t("profile.loginStatus") }}</h2>
        <a-descriptions
          :column="{ xs: 1, sm: 2 }"
          size="small"
        >
          <a-descriptions-item :label="t('profile.currentStatus')">
            <a-tag color="success">
              {{ t("profile.signedIn") }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item :label="t('profile.terminal')">
            {{ auth.me?.session?.terminalName || t("common.unavailable") }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('profile.loginTime')">
            {{ auth.me?.session?.loginTime ? formatDateTime(auth.me.session.loginTime) : t("common.unavailable") }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('profile.expiresAt')">
            {{ auth.me?.session?.expireTime ? formatDateTime(auth.me.session.expireTime) : t("profile.neverExpires") }}
          </a-descriptions-item>
        </a-descriptions>
      </div>
    </section>
  </div>
</template>

<style scoped>
.account-panel {
  padding: 20px 24px;
}

.account-section h2 {
  margin: 0 0 18px;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 600;
}

.profile-avatar-editor {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 22px;
}

.profile-avatar-actions {
  display: grid;
  gap: 10px;
}

.account-panel :deep(.ant-descriptions-item-label) {
  color: var(--app-text-secondary);
}

.account-panel :deep(.ant-descriptions-item-content) {
  color: var(--app-text);
  font-weight: 500;
}

@media (max-width: 560px) {
  .account-panel {
    padding: 16px;
  }

  .profile-avatar-editor {
    align-items: flex-start;
    gap: 16px;
  }
}
</style>
