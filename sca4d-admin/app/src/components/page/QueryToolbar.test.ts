import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import QueryToolbar from "./QueryToolbar.vue";

describe("QueryToolbar", () => {
  it("将搜索、筛选和操作保持为三个稳定布局区域", () => {
    const wrapper = mount(QueryToolbar, {
      slots: {
        search: "<input aria-label='搜索' />",
        filters: "<select aria-label='状态'></select>",
        actions: "<button>查询</button>"
      }
    });

    expect(wrapper.get(".query-toolbar-container").find(".query-toolbar").exists()).toBe(true);
    expect(wrapper.get(".query-toolbar__search input").attributes("aria-label")).toBe("搜索");
    expect(wrapper.get(".query-toolbar__filters select").attributes("aria-label")).toBe("状态");
    expect(wrapper.get(".query-toolbar__actions button").text()).toBe("查询");
  });

  it("未提供的区域不生成空布局节点", () => {
    const wrapper = mount(QueryToolbar, { slots: { actions: "<button>刷新</button>" } });

    expect(wrapper.find(".query-toolbar__search").exists()).toBe(false);
    expect(wrapper.find(".query-toolbar__filters").exists()).toBe(false);
    expect(wrapper.find(".query-toolbar__actions").exists()).toBe(true);
  });
});
