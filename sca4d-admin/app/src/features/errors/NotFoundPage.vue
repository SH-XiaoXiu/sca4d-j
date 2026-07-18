<script setup lang="ts">
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
const router = useRouter();
const { t } = useI18n();
</script>
<template>
  <main class="empty-page">
    <div class="empty-page__content">
      <div class="empty-page__code">
        404
      </div>
      <h1>{{ t("route.notFound") }}</h1>
      <p>{{ t("errorPage.notFoundDescription") }}</p>
      <a-button
        type="primary"
        @click="router.push('/dashboard')"
      >
        {{ t("errorPage.backDashboard") }}
      </a-button>
    </div>
  </main>
</template>
