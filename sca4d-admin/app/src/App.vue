<script setup lang="ts">
import enUS from "ant-design-vue/es/locale/en_US";
import zhCN from "ant-design-vue/es/locale/zh_CN";
import { theme as antTheme } from "ant-design-vue";
import { computed, onMounted, watchEffect } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute } from "vue-router";
import { useThemeStore } from "./stores/theme";
import { useLocaleStore } from "./stores/locale";
import { useLanguageStore } from "./stores/languages";
import { OVERLAY_Z_INDEX } from "./ui/overlay";

const themeStore = useThemeStore();
const localeStore = useLocaleStore();
const languageStore = useLanguageStore();
const route = useRoute();
const { t } = useI18n();
onMounted(() => void languageStore.load());
function popupContainer(): HTMLElement {
  return document.body;
}
const antLocale = computed(() => (localeStore.locale === "zh-CN" ? zhCN : enUS));
watchEffect(() => {
  const pageTitle = route.meta.titleKey ? t(route.meta.titleKey) : route.meta.title;
  document.title = pageTitle ? `${pageTitle} - ${t("app.name")}` : t("app.name");
});
const themeConfig = computed(() => ({
  algorithm: themeStore.isDark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
  token: {
    colorPrimary: themeStore.isDark ? "#3c7eff" : "#165dff",
    colorSuccess: themeStore.isDark ? "#23c343" : "#00b42a",
    colorWarning: themeStore.isDark ? "#ff9a2e" : "#ff7d00",
    colorError: themeStore.isDark ? "#f76965" : "#f53f3f",
    colorBgLayout: themeStore.isDark ? "#17171a" : "#f7f8fa",
    colorBgContainer: themeStore.isDark ? "#232324" : "#ffffff",
    colorText: themeStore.isDark ? "rgba(255, 255, 255, 0.9)" : "#1d2129",
    colorTextSecondary: themeStore.isDark ? "rgba(255, 255, 255, 0.7)" : "#4e5969",
    colorBorder: themeStore.isDark ? "#3a3a3c" : "#c9cdd4",
    colorBorderSecondary: themeStore.isDark ? "#333335" : "#e5e6eb",
    controlHeight: 34,
    borderRadius: 4,
    fontSize: 14,
    zIndexPopupBase: OVERLAY_Z_INDEX.modal,
    motionDurationFast: "0.16s",
    motionDurationMid: "0.2s",
    motionDurationSlow: "0.22s",
    boxShadowSecondary: themeStore.isDark
      ? "0 8px 24px rgba(0, 0, 0, 0.42)"
      : "0 8px 24px rgba(29, 33, 41, 0.12)",
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif'
  },
  components: {
    Button: { controlHeight: 34, borderRadius: 4, fontWeight: 500 },
    Layout: {
      bodyBg: themeStore.isDark ? "#17171a" : "#f7f8fa",
      headerBg: themeStore.isDark ? "#232324" : "#ffffff",
      siderBg: themeStore.isDark ? "#232324" : "#ffffff"
    },
    Menu: {
      itemHeight: 40,
      itemBorderRadius: 4,
      itemMarginInline: 8,
      itemMarginBlock: 2,
      itemColor: themeStore.isDark ? "rgba(255, 255, 255, 0.72)" : "#4e5969",
      itemIconColor: themeStore.isDark ? "rgba(255, 255, 255, 0.72)" : "#4e5969",
      itemHoverColor: themeStore.isDark ? "rgba(255, 255, 255, 0.9)" : "#1d2129",
      itemSelectedBg: themeStore.isDark ? "#172b4d" : "#e8f3ff",
      itemSelectedColor: themeStore.isDark ? "#6aa1ff" : "#165dff",
      itemHoverBg: themeStore.isDark ? "#2a2a2b" : "#f2f3f5",
      activeBarBorderWidth: 0
    },
    Table: {
      headerBg: themeStore.isDark ? "#2a2a2b" : "#f7f8fa",
      headerColor: themeStore.isDark ? "rgba(255, 255, 255, 0.9)" : "#1d2129",
      rowHoverBg: themeStore.isDark ? "#2a2a2b" : "#f7f8fa",
      borderColor: themeStore.isDark ? "#333335" : "#e5e6eb",
      cellPaddingBlock: 10,
      cellPaddingInline: 14
    },
    Descriptions: { labelBg: "transparent" },
    Drawer: { zIndexPopup: OVERLAY_Z_INDEX.drawer },
    Dropdown: { zIndexPopup: OVERLAY_Z_INDEX.popup },
    DatePicker: { zIndexPopup: OVERLAY_Z_INDEX.popup },
    Message: { zIndexPopup: OVERLAY_Z_INDEX.feedback },
    Popover: { zIndexPopup: OVERLAY_Z_INDEX.popup },
    Select: { zIndexPopup: OVERLAY_Z_INDEX.popup },
    Tooltip: { zIndexPopup: OVERLAY_Z_INDEX.popup }
  }
}));
</script>

<template>
  <a-config-provider
    :locale="antLocale"
    :theme="themeConfig"
    :get-popup-container="popupContainer"
  >
    <router-view />
  </a-config-provider>
</template>
