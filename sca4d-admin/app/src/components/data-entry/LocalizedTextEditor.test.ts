/* eslint-disable vue/one-component-per-file */
import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { defineComponent, h } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { applyApplicationLocale, i18n } from "@/i18n";
import { useLanguageStore } from "@/stores/languages";

vi.mock("@/api/client", () => ({
  languageApi: { languages: vi.fn() }
}));

import LocalizedTextEditor from "./LocalizedTextEditor.vue";

const SelectStub = defineComponent({
  setup(_, { slots }) {
    return () => h("div", { class: "select-stub" }, slots.default?.());
  }
});
const OptionStub = defineComponent({
  setup(_, { slots }) {
    return () => h("div", { class: "option-stub" }, slots.default?.());
  }
});
const InputStub = defineComponent({
  props: { value: { type: String, default: "" } },
  emits: ["update:value"],
  setup(props) {
    return () => h("input", { value: props.value });
  }
});

describe("LocalizedTextEditor", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    applyApplicationLocale("zh-CN");
  });

  it("二十种语言仍只渲染一个文本输入并显示完成状态", () => {
    const store = useLanguageStore();
    store.languages = Array.from({ length: 20 }, (_, index) => ({
      code: index === 0 ? "zh-CN" : `x-${index}`,
      nativeName: index === 0 ? "简体中文" : `Language ${index}`,
      defaultLanguage: index === 0,
      required: index === 0
    }));
    store.loaded = true;

    const wrapper = mount(LocalizedTextEditor, {
      props: { modelValue: { "zh-CN": "菜单" } },
      global: {
        plugins: [i18n],
        stubs: {
          "a-select": SelectStub,
          "a-select-option": OptionStub,
          "a-input": InputStub,
          "a-tag": { template: "<span class='tag-stub'><slot /></span>" },
          "a-alert": { template: "<div><slot /></div>" }
        }
      }
    });

    expect(wrapper.findAll(".option-stub")).toHaveLength(20);
    expect(wrapper.findAll("input")).toHaveLength(1);
    expect(wrapper.text()).toContain("简体中文*");
    expect(wrapper.text()).toContain("已完成");
  });
});
