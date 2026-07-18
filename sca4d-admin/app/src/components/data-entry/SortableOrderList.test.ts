import { mount } from "@vue/test-utils";
import { defineComponent, h, nextTick, ref } from "vue";
import { describe, expect, it, vi } from "vitest";

vi.mock("sortablejs", () => ({
  default: {
    create: vi.fn(() => ({
      destroy: vi.fn(),
      option: vi.fn()
    }))
  }
}));

import SortableOrderList, { type SortableOrderItem } from "./SortableOrderList.vue";

describe("SortableOrderList", () => {
  it("支持抓取、键盘移动、取消和确认排序", async () => {
    const Host = defineComponent({
      setup() {
        const items = ref<SortableOrderItem[]>([
          { id: "first", label: "第一项" },
          { id: "second", label: "第二项" },
          { id: "third", label: "第三项" }
        ]);
        return () => h(SortableOrderList, {
          modelValue: items.value,
          dragLabel: "调整顺序",
          "onUpdate:modelValue": (value: SortableOrderItem[]) => { items.value = value; }
        });
      }
    });
    const wrapper = mount(Host, {
      global: { stubs: { "a-tooltip": { template: "<span><slot /></span>" } } }
    });

    let handles = wrapper.findAll(".sortable-order-list__handle");
    await handles[0]!.trigger("keydown", { key: " ", code: "Space" });
    expect(handles[0]!.attributes("aria-pressed")).toBe("true");
    await handles[0]!.trigger("keydown", { key: "ArrowDown" });
    await nextTick();
    expect(wrapper.findAll(".sortable-order-list__content strong").map((item) => item.text()))
      .toEqual(["第二项", "第一项", "第三项"]);

    handles = wrapper.findAll(".sortable-order-list__handle");
    await handles[1]!.trigger("keydown", { key: "Escape" });
    await nextTick();
    expect(wrapper.findAll(".sortable-order-list__content strong").map((item) => item.text()))
      .toEqual(["第一项", "第二项", "第三项"]);

    handles = wrapper.findAll(".sortable-order-list__handle");
    await handles[0]!.trigger("keydown", { key: " ", code: "Space" });
    await handles[0]!.trigger("keydown", { key: "ArrowDown" });
    await nextTick();
    handles = wrapper.findAll(".sortable-order-list__handle");
    await handles[1]!.trigger("keydown", { key: "Enter" });
    await nextTick();
    expect(wrapper.findAll(".sortable-order-list__content strong").map((item) => item.text()))
      .toEqual(["第二项", "第一项", "第三项"]);
    expect(handles[1]!.attributes("aria-pressed")).toBe("false");
  });
});
