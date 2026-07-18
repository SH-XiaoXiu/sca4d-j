import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ResourceLink from "./ResourceLink.vue";

describe("ResourceLink", () => {
  it("可导航资源使用真实链接并保留可见名称", () => {
    const wrapper = mount(ResourceLink, {
      props: { label: "平台操作员", to: "/platform/operators/principal-1" },
      global: {
        stubs: {
          RouterLink: { template: '<a class="router-link"><slot /></a>' },
          "a-tooltip": { template: "<span><slot /></span>" }
        }
      }
    });

    expect(wrapper.get("a").text()).toContain("平台操作员");
    expect(wrapper.find("svg").attributes("aria-hidden")).toBe("true");
  });

  it("不可导航资源显示名称和文字原因且不伪装成链接", () => {
    const wrapper = mount(ResourceLink, {
      props: { label: "平台管理员", navigationMessage: "资源已删除" },
      global: {
        stubs: {
          RouterLink: { template: '<a class="router-link"><slot /></a>' },
          "a-tooltip": { template: "<span><slot /></span>" }
        }
      }
    });

    expect(wrapper.find("a").exists()).toBe(false);
    expect(wrapper.text()).toContain("平台管理员");
    expect(wrapper.text()).toBe("平台管理员资源已删除");
  });

  it("未知历史名称只显示一次导航状态", () => {
    const wrapper = mount(ResourceLink, {
      props: { label: null, navigationMessage: "资源已删除" },
      global: {
        stubs: {
          RouterLink: { template: '<a class="router-link"><slot /></a>' },
          "a-tooltip": { template: "<span><slot /></span>" }
        }
      }
    });

    expect(wrapper.find("a").exists()).toBe(false);
    expect(wrapper.text()).toBe("资源已删除");
  });
});
