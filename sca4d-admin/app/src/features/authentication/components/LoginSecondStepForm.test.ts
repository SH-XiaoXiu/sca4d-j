/* eslint-disable vue/one-component-per-file */
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, nextTick } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "@/i18n";

const mocks = vi.hoisted(() => ({
  pendingEmailCode: null as null | { challengeId: string; maskedRecipient: string; resendAt: string },
  completePasswordSecondStep: vi.fn(),
  completeTotp: vi.fn(),
  startEmailCode: vi.fn(),
  resendEmailCode: vi.fn(),
  completeEmailCode: vi.fn(),
  startPasskeySecondStep: vi.fn(),
  completePasskeySecondStep: vi.fn()
}));

vi.mock("@/stores/authentication", () => ({ useAuthenticationStore: () => mocks }));

import LoginSecondStepForm from "./LoginSecondStepForm.vue";

const FormStub = defineComponent({
  name: "FormStub",
  emits: ["finish"],
  setup(_, { slots }) {
    return () => h("form", { onSubmit: (event: Event) => { event.preventDefault(); } }, slots.default?.());
  }
});
const SegmentedStub = defineComponent({
  name: "SegmentedStub",
  props: { value: { type: String, default: "" } },
  emits: ["update:value"],
  setup(_, { slots }) {
    return () => h("div", slots.default?.());
  }
});
const InputStub = defineComponent({
  name: "InputStub",
  inheritAttrs: false,
  props: {
    value: { type: String, default: "" },
    size: { type: String, default: "" }
  },
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
const ButtonStub = defineComponent({
  name: "ButtonStub",
  inheritAttrs: false,
  emits: ["click"],
  setup(_, { attrs, emit, slots }) {
    return () => h("button", { ...attrs, onClick: () => emit("click") }, slots.default?.());
  }
});

function mountForm(
  methods: Array<"PASSWORD" | "TOTP" | "EMAIL_CODE" | "PASSKEY"> = ["TOTP", "EMAIL_CODE"],
  primaryMethod: "PASSWORD" | "PASSKEY" | "OIDC" | "OAUTH2" | "LDAP" | null = "PASSWORD"
) {
  return mount(LoginSecondStepForm, {
    props: { methods, primaryMethod, loading: false },
    global: {
      plugins: [i18n],
      stubs: {
        "a-form": FormStub,
        "a-form-item": { template: "<label><slot /></label>" },
        "a-segmented": SegmentedStub,
        "a-input": InputStub,
        "a-input-password": InputStub,
        "a-button": ButtonStub,
        "a-alert": { template: "<div class='alert-stub' />" },
        "a-tooltip": { template: "<span><slot /></span>" }
      }
    }
  });
}

describe("LoginSecondStepForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.pendingEmailCode = null;
  });

  afterEach(() => vi.useRealTimers());

  it("提交身份验证器验证码后通知页面完成登录", async () => {
    mocks.completeTotp.mockResolvedValue(undefined);
    const wrapper = mountForm(["TOTP"]);

    await wrapper.get("input").setValue("123456");
    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    expect(mocks.completeTotp).toHaveBeenCalledWith("123456");
    expect(wrapper.emitted("completed")).toHaveLength(1);
    wrapper.unmount();
  });

  it("非密码首因子可用本地密码完成登录二步验证", async () => {
    mocks.completePasswordSecondStep.mockResolvedValue(undefined);
    const wrapper = mountForm(["PASSWORD"], "OIDC");

    await wrapper.get("input").setValue("secret");
    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    expect(mocks.completePasswordSecondStep).toHaveBeenCalledWith("secret");
    expect(wrapper.emitted("completed")).toHaveLength(1);
    wrapper.unmount();
  });

  it("邮件验证码未发送时只提供发送动作", async () => {
    mocks.startEmailCode.mockResolvedValue(undefined);
    const wrapper = mountForm(["EMAIL_CODE"]);
    await nextTick();

    expect(wrapper.find("input").exists()).toBe(false);
    const sendButton = wrapper.findAll("button").at(-1);
    await sendButton?.trigger("click");
    await flushPromises();

    expect(mocks.startEmailCode).toHaveBeenCalledTimes(1);
    expect(wrapper.emitted("error")).toBeUndefined();
    wrapper.unmount();
  });

  it("切换验证方式时清空验证码并支持返回登录", async () => {
    const wrapper = mountForm();
    await wrapper.get("input").setValue("123456");

    wrapper.getComponent(SegmentedStub).vm.$emit("update:value", "EMAIL_CODE");
    await nextTick();
    expect(wrapper.find("input").exists()).toBe(false);
    wrapper.getComponent(SegmentedStub).vm.$emit("update:value", "TOTP");
    await nextTick();
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("");
    await wrapper.findAll("button")[0]?.trigger("click");
    expect(wrapper.emitted("back")).toHaveLength(1);
    wrapper.unmount();
  });

  it("验证失败时保留当前步骤并向页面返回错误", async () => {
    mocks.completeTotp.mockRejectedValue(new Error("验证码已失效"));
    const wrapper = mountForm(["TOTP"]);
    await wrapper.get("input").setValue("123456");

    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    expect(wrapper.emitted("completed")).toBeUndefined();
    expect(wrapper.emitted("error")?.[0]).toEqual(["验证码已失效"]);
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("123456");
    wrapper.unmount();
  });
});
