<script setup lang="ts">
import type { PlatformAccount } from '@sca4d/admin';

import { computed, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import { SjAvatarEditor, SjResourceEditor } from '#/components/sj';
import { useSjAccess } from '#/features/sj-access';
import { useSjControllerState } from '#/features/sj-controller-state';
import { useSjPresentation } from '#/features/sj-presentation';
import { businessApplication } from '#/platform/business-application';

const emit = defineEmits<{
  /** 通知调用方清除已消费的深链接。 */ closed: [];
}>();

const { t, errorText } = useSjPresentation();

/** 查看账号头像与编辑账号角色；读取中的旧结果不回填另一个账号。 */
const selectedAccount = defineModel<null | PlatformAccount>('account', {
  required: true,
});
const accounts = useSjControllerState(businessApplication.accounts);
const session = useSjAccess();
const canManage = computed(() => session.value.capabilities.accountsManage);
const roleLoading = ref(false);
const roleSaving = ref(false);
const selectedRoleIds = ref<string[]>([]);
const roleDrawerOpen = computed({
  get: () => selectedAccount.value !== null,
  set: (open) => {
    if (!open) selectedAccount.value = null;
  },
});
let generation = 0;
watch(
  () => selectedAccount.value?.id,
  async (id) => {
    const read = ++generation;
    selectedRoleIds.value = [];
    if (!id) return;
    roleLoading.value = true;
    try {
      await businessApplication.accounts.loadRoles(id);
      if (read === generation)
        selectedRoleIds.value = [
          ...(accounts.value.selectedRoles?.roleIds ?? []),
        ];
    } catch (error) {
      if (read === generation) message.error(errorText(error));
    } finally {
      if (read === generation) roleLoading.value = false;
    }
  },
);
/** 只使用当前读取的角色关系版本完整替换。 */
async function saveRoles() {
  if (roleSaving.value || roleLoading.value || !canManage.value) return;
  roleSaving.value = true;
  try {
    await businessApplication.accounts.replaceRoles(selectedRoleIds.value);
    selectedAccount.value = null;
    message.success(t('saveSuccess'));
  } catch (error) {
    message.error(errorText(error));
  } finally {
    roleSaving.value = false;
  }
}
</script>
<template>
  <SjResourceEditor
    v-model:open="roleDrawerOpen"
    :title="t('accountSettings')"
    :saving="roleSaving"
    size="md"
    @closed="emit('closed')"
  >
    <a-spin :spinning="roleLoading">
      <SjAvatarEditor
        v-if="selectedAccount"
        :key="selectedAccount.id"
        :account-id="selectedAccount.id"
        :file-id="selectedAccount.avatarFileId"
        :name="selectedAccount.displayName"
        :editable="canManage"
        :replace="
          (fileId) =>
            businessApplication.accounts.replaceAvatar(
              selectedAccount!.id,
              fileId,
            )
        "
        :remove="
          () => businessApplication.accounts.deleteAvatar(selectedAccount!.id)
        "
      />
      <h3 class="mb-3 text-sm font-semibold">
        {{ t('accountRoles') }}
      </h3>
      <a-checkbox-group v-model:value="selectedRoleIds" class="flex flex-col">
        <a-checkbox
          v-for="role in accounts.roles"
          :key="role.id"
          :value="role.id"
          :disabled="!canManage"
        >
          <span>{{ role.name }}</span>
          <small class="text-muted-foreground ml-2">{{ role.code }}</small>
        </a-checkbox>
      </a-checkbox-group>
    </a-spin>
    <template #footer>
      <div class="flex justify-end gap-2">
        <a-button @click="roleDrawerOpen = false">
          {{ t('cancel') }}
        </a-button>
        <a-button
          v-if="canManage"
          type="primary"
          :loading="roleSaving"
          @click="saveRoles"
        >
          {{ t('save') }}
        </a-button>
      </div>
    </template>
  </SjResourceEditor>
</template>
