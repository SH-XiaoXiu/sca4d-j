<script setup lang="ts">
import type { Int64String } from '@sca4d/admin';

import { computed, ref } from 'vue';

import { message } from 'ant-design-vue';
import { KeyRound, Laptop, Trash2 } from 'lucide-vue-next';

import { SjResourceEditor, SjResourceState } from '#/components/sj';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { useSecurityStore } from '#/store/security';

import { browserPasskeyErrorText } from './presentation';

/** Passkey registration 使用的账号显示名称。 */
const props = defineProps<{ displayName: string }>();

const { dateTime, errorText } = useSjPresentation();

const store = useSecurityStore();
const registrationOpen = ref(false);
const passkeyName = ref('');
const loading = computed(
  () =>
    store.passkeys.phase === 'loading' ||
    store.verification.phase === 'loading',
);

/** 收集 Passkey 名称后开始动作认证和浏览器 registration ceremony。 */
async function register(): Promise<void> {
  const name = passkeyName.value.trim();
  if (!name) return;
  try {
    await store.registerPasskey(name, props.displayName);
    registrationOpen.value = false;
    passkeyName.value = '';
  } catch (error) {
    message.error(
      browserPasskeyErrorText(error) ??
        errorText(store.passkeys.error ?? store.verification.error),
    );
  }
}

/** 删除指定 Passkey；动作认证未满足时由统一窗口继续。 */
async function remove(credentialId: Int64String): Promise<void> {
  try {
    await store.removePasskey(credentialId);
  } catch {
    message.error(errorText(store.passkeys.error ?? store.verification.error));
  }
}
</script>

<template>
  <section class="pt-7">
    <header
      class="mb-5 flex min-h-10 items-start justify-between gap-5 max-sm:flex-col"
    >
      <div class="flex min-w-0 items-center gap-3">
        <h2 class="text-base font-semibold">
          {{ $t('page.security.passkeys.title') }}
        </h2>
        <a-tag>{{ store.passkeys.credentials.length }}</a-tag>
      </div>
      <a-tooltip
        :title="
          store.passkeySupported
            ? undefined
            : $t('page.security.passkeys.unsupported')
        "
      >
        <span>
          <a-button
            :disabled="!store.passkeySupported"
            type="primary"
            @click="registrationOpen = true"
          >
            <KeyRound :size="17" aria-hidden="true" />
            {{ $t('page.security.passkeys.add') }}
          </a-button>
        </span>
      </a-tooltip>
    </header>

    <a-alert
      v-if="!store.passkeySupported"
      class="mb-5"
      :message="$t('page.security.passkeys.unsupported')"
      show-icon
      type="warning"
    />

    <SjResourceState
      v-if="
        store.passkeys.phase === 'error' || store.passkeys.phase === 'loading'
      "
      :error="store.passkeys.error"
      :phase="store.passkeys.phase"
      @retry="store.loadPasskeys"
    />
    <a-empty
      v-else-if="store.passkeys.credentials.length === 0"
      :description="$t('page.security.passkeys.empty')"
    />
    <div v-else class="divide-y border-y">
      <div
        v-for="passkey in store.passkeys.credentials"
        :key="passkey.id"
        class="flex min-h-16 items-center justify-between gap-4 py-3"
      >
        <div class="flex min-w-0 items-center gap-3">
          <Laptop :size="20" aria-hidden="true" />
          <div class="min-w-0">
            <div class="truncate font-medium">{{ passkey.name }}</div>
            <div class="text-muted-foreground text-xs">
              {{ $t('page.security.passkeys.createdAt') }}
              {{ dateTime(passkey.createdAt) }} ·
              {{ $t('page.security.passkeys.lastUsedAt') }}
              {{
                passkey.lastUsedAt
                  ? dateTime(passkey.lastUsedAt)
                  : $t('page.security.passkeys.neverUsed')
              }}
            </div>
          </div>
        </div>
        <a-popconfirm
          :title="$t('page.security.passkeys.removeConfirm')"
          @confirm="remove(passkey.id)"
        >
          <a-button danger type="text">
            <Trash2 :size="17" aria-hidden="true" />
            {{ $t('page.security.common.remove') }}
          </a-button>
        </a-popconfirm>
      </div>
    </div>

    <SjResourceEditor
      create
      size="md"
      v-model:open="registrationOpen"
      :saving="loading"
      :submit-disabled="!passkeyName.trim()"
      :title="$t('page.security.passkeys.register')"
      @submit="register"
    >
      <a-form layout="vertical" @finish="register">
        <a-form-item :label="$t('page.security.passkeys.name')" required>
          <a-input
            v-model:value="passkeyName"
            :aria-label="$t('page.security.passkeys.name')"
            autofocus
            :maxlength="100"
          />
        </a-form-item>
      </a-form>
    </SjResourceEditor>
  </section>
</template>
