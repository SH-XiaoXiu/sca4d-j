<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { message } from 'ant-design-vue';
import { RefreshCw, Smartphone, Trash2 } from 'lucide-vue-next';

import { SjResourceState } from '#/components/sj';
import { useSjPresentation } from '#/features/sj-presentation';
import { $t } from '#/locales';
import { useSecurityStore } from '#/store/security';

const { errorText } = useSjPresentation();

const store = useSecurityStore();
const activationCode = ref('');
const qrCodeUrl = ref<null | string>(null);
const loading = computed(
  () =>
    store.totp.phase === 'loading' || store.verification.phase === 'loading',
);

watch(
  () => store.totp.qrCode,
  (qrCode) => {
    revokeQrCode();
    if (qrCode) {
      qrCodeUrl.value = URL.createObjectURL(
        new Blob([qrCode], { type: 'image/png' }),
      );
    }
  },
  { immediate: true },
);
onBeforeUnmount(revokeQrCode);

/** 开始首次配置或替换 TOTP；动作认证未满足时由统一窗口继续。 */
async function beginEnrollment(): Promise<void> {
  try {
    await store.beginTotpEnrollment();
  } catch {
    message.error(errorText(store.totp.error ?? store.verification.error));
  }
}

/** 使用当前 enrollment 的首个验证码激活 TOTP Credential。 */
async function activate(): Promise<void> {
  if (!/^\d{6}$/u.test(activationCode.value)) return;
  try {
    await store.activateTotp(activationCode.value);
    activationCode.value = '';
  } catch {
    message.error(errorText(store.totp.error));
  }
}

/** 取消当前 enrollment 并释放二维码。 */
async function cancelEnrollment(): Promise<void> {
  try {
    await store.cancelTotpEnrollment();
    activationCode.value = '';
  } catch {
    message.error(errorText(store.totp.error));
  }
}

/** 删除当前 TOTP Credential；动作认证未满足时由统一窗口继续。 */
async function removeCredential(): Promise<void> {
  try {
    await store.removeTotp();
  } catch {
    message.error(errorText(store.totp.error ?? store.verification.error));
  }
}

function revokeQrCode(): void {
  if (qrCodeUrl.value) URL.revokeObjectURL(qrCodeUrl.value);
  qrCodeUrl.value = null;
}
</script>

<template>
  <section class="border-b pb-7">
    <header
      class="mb-5 flex min-h-10 items-start justify-between gap-5 max-sm:flex-col"
    >
      <div class="flex min-w-0 items-center gap-3">
        <h2 class="text-base font-semibold">
          {{ $t('page.security.totp.title') }}
        </h2>
        <a-tag :color="store.totp.credential ? 'green' : 'default'">
          {{
            store.totp.credential
              ? $t('page.security.common.configured')
              : $t('page.security.common.notConfigured')
          }}
        </a-tag>
      </div>
      <div v-if="store.totp.credential" class="flex items-center gap-2">
        <a-button :loading="loading" @click="beginEnrollment">
          <RefreshCw :size="17" aria-hidden="true" />
          {{ $t('page.security.common.replace') }}
        </a-button>
        <a-popconfirm
          :cancel-text="$t('page.security.common.cancel')"
          :ok-text="$t('page.security.common.remove')"
          :title="$t('page.security.totp.removeWarning')"
          @confirm="removeCredential"
        >
          <a-button danger :loading="loading">
            <Trash2 :size="17" aria-hidden="true" />
            {{ $t('page.security.common.remove') }}
          </a-button>
        </a-popconfirm>
      </div>
      <a-button
        v-else
        :loading="loading"
        type="primary"
        @click="beginEnrollment"
      >
        <Smartphone :size="17" aria-hidden="true" />
        {{ $t('page.security.common.configure') }}
      </a-button>
    </header>

    <SjResourceState
      v-if="store.totp.phase === 'error' || store.totp.phase === 'loading'"
      :error="store.totp.error"
      :phase="store.totp.phase"
      @retry="store.loadTotp"
    />

    <div
      v-else-if="store.totp.enrollment && qrCodeUrl"
      class="grid grid-cols-[220px_minmax(240px,360px)] items-center gap-8 max-sm:grid-cols-1 max-sm:gap-4"
    >
      <img
        :alt="$t('page.security.totp.qrCode')"
        class="rounded-md border"
        height="220"
        :src="qrCodeUrl"
        width="220"
      />
      <a-form class="min-w-0" layout="vertical" @finish="activate">
        <a-form-item :label="$t('page.security.verification.totp')" required>
          <a-input
            v-model:value="activationCode"
            :aria-label="$t('page.security.verification.totp')"
            autocomplete="one-time-code"
            inputmode="numeric"
            :maxlength="6"
          />
        </a-form-item>
        <div class="flex items-center gap-2">
          <a-button
            :disabled="!/^\d{6}$/u.test(activationCode)"
            :loading="loading"
            type="primary"
            @click="activate"
          >
            <Smartphone :size="17" aria-hidden="true" />
            {{ $t('page.security.totp.activate') }}
          </a-button>
          <a-button @click="cancelEnrollment">
            {{ $t('page.security.common.cancel') }}
          </a-button>
        </div>
      </a-form>
    </div>
  </section>
</template>
