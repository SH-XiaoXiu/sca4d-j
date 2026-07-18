import { defineStore } from "pinia";
import { ref } from "vue";
import {
  applyApplicationLocale,
  DEFAULT_LOCALE,
  getApplicationLocale,
  isApplicationLocale,
  LOCALE_STORAGE_KEY,
  type ApplicationLocale
} from "../i18n";

/** 保存界面语言并同步浏览器存储的 Pinia Store。 */
export const useLocaleStore = defineStore("locale", () => {
  const locale = ref<ApplicationLocale>(getApplicationLocale());
  const revision = ref(0);

  function restore(): void {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY);
    locale.value = isApplicationLocale(stored) ? stored : getApplicationLocale() ?? DEFAULT_LOCALE;
    applyApplicationLocale(locale.value);
  }

  function select(value: ApplicationLocale): void {
    const changed = locale.value !== value;
    locale.value = value;
    if (changed) revision.value += 1;
    localStorage.setItem(LOCALE_STORAGE_KEY, value);
    applyApplicationLocale(value);
  }

  return { locale, revision, restore, select };
});
