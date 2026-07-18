import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import { getApplicationLocale, LOCALE_STORAGE_KEY } from "../i18n";
import { useLocaleStore } from "./locale";

describe("locale store", () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it("切换语言时同步持久化、HTML 语言和全局目录", () => {
    const store = useLocaleStore();

    store.select("en-US");

    expect(store.locale).toBe("en-US");
    expect(getApplicationLocale()).toBe("en-US");
    expect(document.documentElement.lang).toBe("en-US");
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("en-US");
  });

  it("恢复时只接受 i18n 模块声明的语言", () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, "zh-CN");
    const store = useLocaleStore();

    store.restore();

    expect(store.locale).toBe("zh-CN");
    expect(getApplicationLocale()).toBe("zh-CN");
    expect(document.documentElement.lang).toBe("zh-CN");
  });
});
