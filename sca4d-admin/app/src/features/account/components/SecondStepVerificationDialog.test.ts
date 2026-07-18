/* eslint-disable vue/one-component-per-file */
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, nextTick } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { i18n } from "@/i18n";

const mocks = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  createAuthentication: vi.fn(),
  passwordProof: vi.fn(),
  createEmailCodeChallenge: vi.fn(),
  emailCodeProof: vi.fn(),
  totpProof: vi.fn(),
  createPasskeyAssertion: vi.fn(),
  passkeyProof: vi.fn()
}));

vi.mock("ant-design-vue", () => ({ message: { success: mocks.success, error: mocks.error } }));
vi.mock("@/api/client", () => ({
  authenticationApi: {
    createAuthentication: mocks.createAuthentication,
    passwordProof: mocks.passwordProof,
    createEmailCodeChallenge: mocks.createEmailCodeChallenge,
    emailCodeProof: mocks.emailCodeProof,
    totpProof: mocks.totpProof,
    createPasskeyAssertion: mocks.createPasskeyAssertion,
    passkeyProof: mocks.passkeyProof
  }
}));

import SecondStepVerificationDialog from "./SecondStepVerificationDialog.vue";

const ModalStub = defineComponent({
  name: "ModalStub",
  props: { open: Boolean },
  emits: ["cancel"],
  setup(props, { slots }) {
    return () => props.open ? h("section", slots.default?.()) : null;
  }
});
const FormStub = defineComponent({
  name: "FormStub",
  emits: ["finish"],
  setup(_, { slots }) {
    return () => h("form", { onSubmit: (event: Event) => event.preventDefault() }, slots.default?.());
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
  props: { danger: Boolean },
  emits: ["click"],
  setup(props, { attrs, emit, slots }) {
    return () => h("button", {
      ...attrs,
      "data-danger": String(props.danger),
      onClick: () => emit("click")
    }, slots.default?.());
  }
});
const AlertStub = defineComponent({
  name: "AlertStub",
  props: {
    type: { type: String, default: "" },
    message: { type: String, default: "" },
    description: { type: String, default: "" }
  },
  setup(props) {
    return () => h("div", {
      class: "alert-stub",
      "data-type": props.type
    }, `${props.message} ${props.description}`);
  }
});

function mountDialog(
  methods: Array<"PASSWORD" | "EMAIL_CODE" | "TOTP" | "PASSKEY">,
  danger = false
) {
  return mount(SecondStepVerificationDialog, {
    props: {
      open: true,
      methods,
      passwordIdentifier: {
        identifierId: "identifier-1",
        identifierType: "USERNAME",
        value: "alice",
        status: "ACTIVE",
        version: "1"
      },
      title: "完成二步验证",
      impact: "验证通过后将结束所选登录设备。",
      submitLabel: "完成二步验证并结束会话",
      danger
    },
    global: {
      plugins: [i18n],
      stubs: {
        "a-modal": ModalStub,
        "a-form": FormStub,
        "a-form-item": { template: "<label><slot /></label>" },
        "a-segmented": SegmentedStub,
        "a-input": InputStub,
        "a-input-password": InputStub,
        "a-button": ButtonStub,
        "a-alert": AlertStub
      }
    }
  });
}

describe("SecondStepVerificationDialog", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    i18n.global.locale.value = "zh-CN";
    mocks.createAuthentication.mockResolvedValue({ authenticationId: "authentication-1" });
  });

  it("使用当前账号密码完成二步验证", async () => {
    mocks.passwordProof.mockResolvedValue({ authenticationId: "authentication-1", activityId: "completion" });
    const wrapper = mountDialog(["PASSWORD"]);
    await wrapper.get("input").setValue("secret");

    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    expect(mocks.createAuthentication).toHaveBeenCalledWith({ purpose: "STEP_UP" });
    expect(mocks.passwordProof).toHaveBeenCalledWith("authentication-1", {
      identifierType: "USERNAME",
      identifier: "alice",
      password: "secret"
    });
    expect(wrapper.emitted("verified")?.[0]).toEqual(["authentication-1"]);
  });

  it("邮件验证码沿用发送时创建的 Authentication", async () => {
    mocks.createEmailCodeChallenge.mockResolvedValue({
      challengeId: "challenge-1",
      maskedRecipient: "a***@example.com"
    });
    mocks.emailCodeProof.mockResolvedValue({ authenticationId: "authentication-1", activityId: "completion" });
    const wrapper = mountDialog(["EMAIL_CODE"]);

    const sendButton = wrapper.findAll("button").at(-1);
    await sendButton?.trigger("click");
    await flushPromises();
    await wrapper.get("input").setValue("123456");
    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    expect(mocks.createAuthentication).toHaveBeenCalledTimes(1);
    expect(mocks.emailCodeProof).toHaveBeenCalledWith("authentication-1", {
      challengeId: "challenge-1",
      code: "123456"
    });
    expect(wrapper.emitted("verified")?.[0]).toEqual(["authentication-1"]);
  });

  it("重新打开时清空上一次输入并恢复首选方式", async () => {
    const wrapper = mountDialog(["PASSWORD", "TOTP"]);
    await wrapper.get("input").setValue("secret");
    wrapper.getComponent(SegmentedStub).vm.$emit("update:value", "TOTP");
    await nextTick();
    await wrapper.get("input").setValue("123456");

    await wrapper.setProps({ open: false });
    await wrapper.setProps({ open: true });

    expect(wrapper.getComponent(SegmentedStub).props("value")).toBe("PASSWORD");
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("");
  });

  it("后端拒绝验证时保留对话框并显示具体原因", async () => {
    mocks.totpProof.mockRejectedValue(new Error("验证码已失效"));
    const wrapper = mountDialog(["TOTP"]);
    await wrapper.get("input").setValue("123456");

    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    expect(mocks.error).toHaveBeenCalledWith("验证码已失效");
    expect(wrapper.emitted("verified")).toBeUndefined();
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("123456");
  });

  it("敏感操作使用警告说明和危险提交按钮", () => {
    const wrapper = mountDialog(["PASSWORD"], true);

    expect(wrapper.get(".alert-stub").attributes("data-type")).toBe("warning");
    expect(wrapper.get(".alert-stub").text()).toContain("敏感操作确认");
    expect(wrapper.get(".alert-stub").text()).toContain("结束所选登录设备");
    expect(wrapper.find(".second-step-impact").exists()).toBe(false);
    expect(wrapper.find("[data-danger='true']").exists()).toBe(true);
  });

  it("普通二步验证使用中性说明和普通主按钮", () => {
    const wrapper = mountDialog(["PASSWORD"]);

    expect(wrapper.find(".alert-stub").exists()).toBe(false);
    expect(wrapper.get(".second-step-impact").text()).toContain("结束所选登录设备");
    expect(wrapper.find("[data-danger='true']").exists()).toBe(false);
  });
});
