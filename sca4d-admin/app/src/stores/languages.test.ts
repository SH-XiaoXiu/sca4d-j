import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ languages: vi.fn() }));

vi.mock("../api/client", () => ({
  languageApi: { languages: mocks.languages }
}));

import { useLanguageStore } from "./languages";

describe("language store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.languages.mockReset();
  });

  it("并发加载只读取一次后端语言目录", async () => {
    mocks.languages.mockResolvedValue([
      { code: "zh-CN", nativeName: "简体中文", defaultLanguage: true, required: true },
      { code: "en-US", nativeName: "English", defaultLanguage: false, required: false }
    ]);
    const store = useLanguageStore();

    await Promise.all([store.load(), store.load(), store.load()]);

    expect(mocks.languages).toHaveBeenCalledTimes(1);
    expect(store.defaultLanguage?.code).toBe("zh-CN");
    expect(store.requiredLanguages.map((language) => language.code)).toEqual(["zh-CN"]);
  });

  it("应用语言只包含已经注册前端消息资源的目录项", async () => {
    mocks.languages.mockResolvedValue([
      { code: "zh-CN", nativeName: "简体中文", defaultLanguage: true, required: true },
      { code: "en-US", nativeName: "English", defaultLanguage: false, required: false },
      { code: "fr-FR", nativeName: "Français", defaultLanguage: false, required: false }
    ]);
    const store = useLanguageStore();

    await store.load();

    expect(store.languages).toHaveLength(3);
    expect(store.applicationLanguages.map((language) => language.code)).toEqual(["zh-CN", "en-US"]);
  });
});
