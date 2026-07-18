<script setup lang="ts">
defineOptions({ name: "SystemScheduledJobsPage" });

import { formatDateTime, isApiError, type SystemScheduledJobView } from "@sca4d/admin-common";
import { Eye, RotateCcw, ServerCog } from "lucide-vue-next";
import { message } from "ant-design-vue";
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { systemScheduledJobApi } from "@/api/client";
import PageHeader from "@/components/page/PageHeader.vue";
import RowActionButton from "@/components/actions/RowActionButton.vue";

const { t } = useI18n();
const loading = ref(false);
const detailLoading = ref(false);
const jobs = ref<SystemScheduledJobView[]>([]);
const detail = ref<SystemScheduledJobView | null>(null);
const detailOpen = ref(false);
const columns = computed(() => [
  { title: t("systemJob.job"), key: "job", width: 280 },
  { title: t("systemJob.schedule"), key: "schedule", width: 230 },
  { title: t("systemJob.triggerState"), key: "state", width: 120 },
  { title: t("systemJob.nextFireTime"), key: "next", width: 180 },
  { title: t("systemJob.lastResult"), key: "result", width: 150 },
  { title: t("common.actions"), key: "actions", width: 100, fixed: "right" as const }
]);

function errorMessage(error: unknown, fallbackKey: string): string {
  return isApiError(error) ? error.message : t(fallbackKey);
}

function stateLabel(state: SystemScheduledJobView["triggerState"]): string {
  return t(`systemJob.states.${state}`);
}

function stateColor(state: SystemScheduledJobView["triggerState"]): string {
  if (state === "NORMAL") return "green";
  if (state === "PAUSED") return "orange";
  return "red";
}

function resultLabel(result: SystemScheduledJobView["lastResult"]): string {
  return result ? t(`systemJob.results.${result}`) : t("systemJob.neverRun");
}

function scheduleLabel(job: SystemScheduledJobView): string {
  return job.scheduleType === "CRON"
    ? t("systemJob.cron", { value: job.cronExpression ?? "--" })
    : t("systemJob.interval", { value: job.interval ?? "--" });
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    jobs.value = await systemScheduledJobApi.jobs();
  } catch (error) {
    message.error(errorMessage(error, "systemJob.loadFailed"));
  } finally {
    loading.value = false;
  }
}

async function openDetail(job: SystemScheduledJobView): Promise<void> {
  detailOpen.value = true;
  detailLoading.value = true;
  try {
    detail.value = await systemScheduledJobApi.detail(job.jobGroup, job.jobName);
  } catch (error) {
    detailOpen.value = false;
    message.error(errorMessage(error, "systemJob.detailLoadFailed"));
  } finally {
    detailLoading.value = false;
  }
}

onMounted(() => { void load(); });
</script>

<template>
  <div>
    <PageHeader
      :title="t('route.systemScheduledJobs')"
      :description="t('systemJob.description')"
    >
      <template #actions>
        <a-button
          :loading="loading"
          @click="load"
        >
          <RotateCcw :size="16" />{{ t("common.refresh") }}
        </a-button>
      </template>
    </PageHeader>
    <section class="page-panel management-panel system-jobs-panel">
      <a-table
        class="desktop-only"
        row-key="jobName"
        :columns="columns"
        :data-source="jobs"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1000 }"
      >
        <template #emptyText>
          {{ t("systemJob.empty") }}
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'job'">
            <div class="job-name">
              <span><ServerCog :size="18" /></span><div><strong>{{ record.description }}</strong><small>{{ record.jobGroup }} / {{ record.jobName }}</small></div>
            </div>
          </template>
          <template v-else-if="column.key === 'schedule'">
            {{ scheduleLabel(record) }}
          </template>
          <template v-else-if="column.key === 'state'">
            <a-tag :color="stateColor(record.triggerState)">
              {{ stateLabel(record.triggerState) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'next'">
            {{ formatDateTime(record.nextFireTime) }}
          </template>
          <template v-else-if="column.key === 'result'">
            <a-tag
              v-if="record.lastResult"
              :color="record.lastResult === 'SUCCESS' ? 'green' : 'red'"
            >
              {{ resultLabel(record.lastResult) }}
            </a-tag><span v-else>{{ resultLabel(null) }}</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <RowActionButton
              :label="t('common.details')"
              @click="openDetail(record)"
            >
              <Eye :size="17" />
            </RowActionButton>
          </template>
        </template>
      </a-table>
      <div class="mobile-only">
        <a-spin :spinning="loading">
          <a-empty
            v-if="!loading && jobs.length === 0"
            :description="t('systemJob.empty')"
          /><div
            v-else
            class="mobile-card-list"
          >
            <article
              v-for="job in jobs"
              :key="`${job.jobGroup}:${job.jobName}`"
              class="mobile-record"
            >
              <div class="mobile-record__header">
                <strong class="mobile-record__title">{{ job.description }}</strong><a-tag :color="stateColor(job.triggerState)">
                  {{ stateLabel(job.triggerState) }}
                </a-tag>
              </div><div class="mobile-record__meta">
                <span>{{ t("systemJob.schedule") }}</span><strong>{{ scheduleLabel(job) }}</strong><span>{{ t("systemJob.nextFireTime") }}</span><strong>{{ formatDateTime(job.nextFireTime) }}</strong><span>{{ t("systemJob.lastResult") }}</span><strong>{{ resultLabel(job.lastResult) }}</strong>
              </div><div class="mobile-record__actions">
                <RowActionButton
                  :label="t('common.details')"
                  @click="openDetail(job)"
                >
                  <Eye :size="17" />
                </RowActionButton>
              </div>
            </article>
          </div>
        </a-spin>
      </div>
    </section>
    <a-drawer
      v-model:open="detailOpen"
      :title="detail?.description ?? t('systemJob.job')"
      :width="640"
      class="responsive-drawer"
    >
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-alert
            v-if="detail.safeErrorSummary"
            type="error"
            show-icon
            :message="resultLabel(detail.lastResult)"
            :description="detail.safeErrorSummary"
          /><a-descriptions
            :column="1"
            bordered
            size="small"
          >
            <a-descriptions-item :label="t('systemJob.job')">
              {{ detail.jobGroup }} / {{ detail.jobName }}
            </a-descriptions-item><a-descriptions-item :label="t('systemJob.schedule')">
              {{ scheduleLabel(detail) }}
            </a-descriptions-item><a-descriptions-item :label="t('systemJob.triggerState')">
              <a-tag :color="stateColor(detail.triggerState)">
                {{ stateLabel(detail.triggerState) }}
              </a-tag>
            </a-descriptions-item><a-descriptions-item :label="t('systemJob.previousFireTime')">
              {{ formatDateTime(detail.previousFireTime) }}
            </a-descriptions-item><a-descriptions-item :label="t('systemJob.nextFireTime')">
              {{ formatDateTime(detail.nextFireTime) }}
            </a-descriptions-item><a-descriptions-item :label="t('systemJob.lastResult')">
              {{ resultLabel(detail.lastResult) }}
            </a-descriptions-item><a-descriptions-item :label="t('systemJob.lastDuration')">
              {{ detail.lastDuration ?? '--' }}
            </a-descriptions-item><a-descriptions-item :label="t('systemJob.instance')">
              {{ detail.executedInstanceId ?? '--' }}
            </a-descriptions-item>
          </a-descriptions>
        </template>
      </a-spin>
    </a-drawer>
  </div>
</template>

<style scoped>
.system-jobs-panel { overflow: hidden; }
.job-name { display: flex; align-items: center; gap: 10px; }
.job-name > span { display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; color: var(--app-primary); background: var(--app-primary-soft); border-radius: var(--app-radius); }
.job-name > div { display: grid; min-width: 0; gap: 2px; }
.job-name strong { overflow-wrap: anywhere; }
.job-name small { color: var(--app-text-tertiary); overflow-wrap: anywhere; }
</style>
