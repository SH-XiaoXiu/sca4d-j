<script setup lang="ts">
import { formatDateTime, type AccountSessionView } from "@sca4d/admin-common";
import { LogOut, Monitor, RefreshCw } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

defineProps<{
  currentSessionId: string | null;
  secondStepAvailable: boolean;
  sessions: AccountSessionView[];
  loading: boolean;
  error: boolean;
}>();
const emit = defineEmits<{
  refresh: [];
  end: [session: AccountSessionView];
}>();
const { t } = useI18n();
</script>

<template>
  <section class="security-section">
    <div class="section-heading section-heading--actions">
      <div>
        <Monitor :size="18" />
        <h3>{{ t("accountSecurity.sessions") }}</h3>
      </div>
      <a-tooltip :title="t('accountSecurity.refreshSessions')">
        <a-button
          type="text"
          :aria-label="t('accountSecurity.refreshSessions')"
          :loading="loading"
          @click="emit('refresh')"
        >
          <RefreshCw :size="16" />
        </a-button>
      </a-tooltip>
    </div>
    <a-skeleton
      v-if="loading && !sessions.length"
      active
      :paragraph="{ rows: 3 }"
    />
    <a-result
      v-else-if="error"
      status="error"
      :title="t('accountSecurity.sessionsFailed')"
    >
      <template #extra>
        <a-button @click="emit('refresh')">
          {{ t("common.refresh") }}
        </a-button>
      </template>
    </a-result>
    <a-empty
      v-else-if="!sessions.length"
      :description="t('accountSecurity.sessionsEmpty')"
    />
    <div
      v-else
      class="session-list"
    >
      <article
        v-for="session in sessions"
        :key="session.sessionId"
        class="session-row"
      >
        <span class="session-row__icon"><Monitor :size="18" /></span>
        <div class="session-row__body">
          <div class="session-row__title">
            <strong>{{ session.terminalName || t("accountSecurity.unknownTerminal") }}</strong>
            <a-tag
              v-if="session.sessionId === currentSessionId"
              color="blue"
            >
              {{ t("accountSecurity.currentSession") }}
            </a-tag>
          </div>
          <span>{{ t("accountSecurity.signedInAt", { time: formatDateTime(session.loginTime) }) }}</span>
          <span v-if="session.deviceId">{{ t("accountSecurity.deviceId", { deviceId: session.deviceId }) }}</span>
        </div>
        <a-button
          danger
          :disabled="!secondStepAvailable"
          @click="emit('end', session)"
        >
          <LogOut :size="16" />{{ t("accountSecurity.endSession") }}
        </a-button>
      </article>
    </div>
    <a-alert
      v-if="!secondStepAvailable"
      type="warning"
      show-icon
      :message="t('accountSecurity.secondStepUnavailable')"
    />
  </section>
</template>
