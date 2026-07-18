/* eslint-disable vue/one-component-per-file */
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, nextTick } from "vue";
import { describe, expect, it, vi } from "vitest";
import { i18n } from "@/i18n";

vi.mock("lucide-vue-next", () => {
  const icon = { render: () => h("svg") };
  return {
    CircleHelp: icon,
    Search: icon,
    icons: Object.fromEntries(Array.from({ length: 220 }, (_, index) => [
      `Icon${String(index).padStart(3, "0")}`,
      icon
    ]))
  };
});

import IconPicker from "./IconPicker.vue";

const PopoverStub = defineComponent({
  name: "PopoverStub",
  props: { open: Boolean },
  emits: ["update:open"],
  setup(props, { slots }) {
    return () => h("div", [slots.default?.(), props.open ? slots.content?.() : null]);
  }
});
const ButtonStub = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () => h("button", attrs, slots.default?.());
  }
});
const InputStub = defineComponent({
  inheritAttrs: false,
  props: { value: { type: String, default: "" } },
  emits: ["update:value"],
  setup(props, { attrs, emit, slots }) {
    return () => h("label", [
      slots.prefix?.(),
      h("input", {
        ...attrs,
        value: props.value,
        onInput: (event: Event) => emit("update:value", (event.target as HTMLInputElement).value)
      })
    ]);
  }
});

function mountPicker() {
  return mount(IconPicker, {
    props: { modelValue: "Icon219" },
    global: {
      plugins: [i18n],
      stubs: {
        "a-popover": PopoverStub,
        "a-tooltip": { template: "<span><slot /></span>" },
        "a-button": ButtonStub,
        "a-input": InputStub,
        "a-spin": { template: "<span />" },
        "a-empty": { template: "<span class='empty-stub' />" }
      }
    }
  });
}

describe("IconPicker", () => {
  it("打开后加载完整目录，分批只渲染图标并支持搜索", async () => {
    const wrapper = mountPicker();
    expect(wrapper.find("[role=listbox]").exists()).toBe(false);

    wrapper.getComponent(PopoverStub).vm.$emit("update:open", true);
    await nextTick();
    await flushPromises();

    expect(wrapper.findAll("[role=option]")).toHaveLength(180);
    expect(wrapper.find("[role=option]").text()).toBe("");
    expect(wrapper.find("[role=option]").attributes("aria-label")).toBe("Icon000");

    await wrapper.get("input").setValue("Icon219");
    await nextTick();
    const options = wrapper.findAll("[role=option]");
    expect(options).toHaveLength(1);
    expect(options[0]?.attributes("aria-label")).toBe("Icon219");

    await options[0]?.trigger("click");
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual(["Icon219"]);
  });
});
