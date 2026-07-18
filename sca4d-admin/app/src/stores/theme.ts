import { defineStore } from "pinia";
import { computed, ref } from "vue";

/** 管理端亮色和暗色主题模式。 */
export type ThemeMode = "light" | "dark";
const STORAGE_KEY = "sca4d-admin.theme";

/** 保存主题选择并同步 html 主题属性的 Pinia Store。 */
export const useThemeStore = defineStore("theme", () => {
  const mode = ref<ThemeMode>("light");
  const isDark = computed(() => mode.value === "dark");

  function apply(): void {
    document.documentElement.dataset.theme = mode.value;
    document.documentElement.style.colorScheme = mode.value;
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute("content", isDark.value ? "#141619" : "#f4f6f8");
  }

  function restore(): void {
    const stored = localStorage.getItem(STORAGE_KEY);
    mode.value =
      stored === "dark" || stored === "light"
        ? stored
        : window.matchMedia("(prefers-color-scheme: dark)").matches
          ? "dark"
          : "light";
    apply();
  }

  function toggle(): void {
    mode.value = isDark.value ? "light" : "dark";
    localStorage.setItem(STORAGE_KEY, mode.value);
    apply();
  }

  return { mode, isDark, restore, toggle };
});
