import { isApiError, type LanguageView } from "@sca4d/admin-common";
import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { languageApi } from "../api/client";
import { isApplicationLocale, translateApplicationMessage } from "../i18n";

/** 加载并缓存后端支持语言目录的 Pinia Store。 */
export const useLanguageStore = defineStore("languages", () => {
  const languages = ref<LanguageView[]>([]);
  const loading = ref(false);
  const loaded = ref(false);
  const loadError = ref("");
  const applicationLanguages = computed(() =>
    languages.value.filter((language) => isApplicationLocale(language.code))
  );
  const requiredLanguages = computed(() =>
    languages.value.filter((language) => language.required)
  );
  const defaultLanguage = computed(() =>
    languages.value.find((language) => language.defaultLanguage) ?? null
  );
  let pending: Promise<void> | null = null;

  function load(): Promise<void> {
    if (loaded.value) return Promise.resolve();
    if (pending) return pending;
    pending = (async () => {
      loading.value = true;
      loadError.value = "";
      try {
        languages.value = await languageApi.languages();
        loaded.value = true;
      } catch (error) {
        loadError.value = isApiError(error)
          ? error.message
          : translateApplicationMessage("client.api.network");
      } finally {
        loading.value = false;
        pending = null;
      }
    })();
    return pending;
  }

  return {
    languages,
    applicationLanguages,
    requiredLanguages,
    defaultLanguage,
    loading,
    loaded,
    loadError,
    load
  };
});
