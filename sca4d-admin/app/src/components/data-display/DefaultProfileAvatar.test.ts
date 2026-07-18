import { mount } from "@vue/test-utils";
import { Avatar } from "ant-design-vue";
import { describe, expect, it } from "vitest";
import DefaultProfileAvatar from "./DefaultProfileAvatar.vue";

describe("DefaultProfileAvatar", () => {
  it("图片加载失败后显示姓名首字母", async () => {
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      value: () => ({
        matches: false,
        media: "",
        onchange: null,
        addListener() {},
        removeListener() {},
        addEventListener() {},
        removeEventListener() {},
        dispatchEvent: () => false
      })
    });
    const wrapper = mount(DefaultProfileAvatar, {
      props: { name: "平台管理员", src: "/unavailable-avatar.webp" },
      global: { components: { AAvatar: Avatar } }
    });

    await wrapper.get("img").trigger("error");

    expect(wrapper.find("img").exists()).toBe(false);
    expect(wrapper.text()).toContain("平");
  });
});
