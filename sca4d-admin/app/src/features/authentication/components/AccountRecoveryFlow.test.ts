/* eslint-disable vue/one-component-per-file */
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "@/i18n";

const mocks = vi.hoisted(() => ({
  createAuthentication: vi.fn(),
  recoveryCodeProof: vi.fn(),
  replacePassword: vi.fn()
}));

vi.mock("@/api/client", () => ({
  authenticationApi: {
    createAuthentication: mocks.createAuthentication,
    recoveryCodeProof: mocks.recoveryCodeProof,
    replacePassword: mocks.replacePassword
  }
}));

import AccountRecoveryFlow from "./AccountRecoveryFlow.vue";

const FormStub = defineComponent({
  name: "FormStub",
  emits: ["finish"],
  setup(_, { slots }) {
    return () => h("form", { onSubmit: (event: Event) => event.preventDefault() }, slots.default?.());
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

function mountFlow() {
  return mount(AccountRecoveryFlow, {
    props: {
      scope: { scopeType: "TENANT", tenantCode: "acme" },
      initialIdentifierType: "USERNAME",
      initialIdentifier: "alice"
    },
    global: {
      plugins: [i18n],
      stubs: {
        "a-form": FormStub,
        "a-form-item": { template: "<label><slot /></label>" },
        "a-input": InputStub,
        "a-input-password": InputStub,
        "a-segmented": true,
        "a-button": { template: "<button><slot /></button>" },
        "a-alert": { template: "<div class='alert-stub' />" },
        "a-tooltip": { template: "<span><slot /></span>" }
      }
    }
  });
}

describe("AccountRecoveryFlow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    i18n.global.locale.value = "zh-CN";
    mocks.createAuthentication.mockResolvedValue({ authenticationId: "recovery-1" });
    mocks.recoveryCodeProof.mockResolvedValue({ activityId: "completion" });
    mocks.replacePassword.mockResolvedValue(undefined);
  });

  it("按恢复认证顺序验证恢复码并替换密码", async () => {
    const wrapper = mountFlow();
    const recoveryInputs = wrapper.findAll("input");
    await recoveryInputs[1]?.setValue("AAAAA-BBBBB-CCCCC-DDDDD");

    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    expect(mocks.createAuthentication).toHaveBeenCalledWith({
      purpose: "RECOVERY",
      scopeType: "TENANT",
      tenantCode: "acme"
    });
    expect(mocks.recoveryCodeProof).toHaveBeenCalledWith("recovery-1", {
      identifierType: "USERNAME",
      identifier: "alice",
      recoveryCode: "AAAAA-BBBBB-CCCCC-DDDDD"
    });

    const passwordInputs = wrapper.findAll("input");
    await passwordInputs[0]?.setValue("new-password");
    await passwordInputs[1]?.setValue("new-password");
    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    expect(mocks.replacePassword).toHaveBeenCalledWith("recovery-1", { password: "new-password" });
    expect(wrapper.text()).toContain("密码已替换");
  });

  it("密码替换失败后废弃已完成的认证并返回恢复码步骤", async () => {
    mocks.replacePassword.mockRejectedValue(new Error("authentication consumed"));
    const wrapper = mountFlow();
    await wrapper.findAll("input")[1]?.setValue("AAAAA-BBBBB-CCCCC-DDDDD");
    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    await wrapper.findAll("input")[0]?.setValue("new-password");
    await wrapper.findAll("input")[1]?.setValue("new-password");
    wrapper.getComponent(FormStub).vm.$emit("finish");
    await flushPromises();

    expect(wrapper.text()).toContain("恢复登录账号");
    expect(wrapper.find(".alert-stub").exists()).toBe(true);
    expect(wrapper.findAll("input")).toHaveLength(2);
  });
});
