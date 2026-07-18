import type {
  AccountSecurityView,
  AccountSessionView,
  ExternalIdentityView
} from "@sca4d/admin-common";
import { shallowMount } from "@vue/test-utils";
import { defineComponent, h, nextTick } from "vue";
import { describe, expect, it } from "vitest";
import { i18n } from "@/i18n";
import AccountSecurityDetails from "./AccountSecurityDetails.vue";
import LinkedAccountsSection from "./LinkedAccountsSection.vue";
import LoginDevicesSection from "./LoginDevicesSection.vue";
import TwoStepVerificationSection from "./TwoStepVerificationSection.vue";
import VerificationMethodsSection from "./VerificationMethodsSection.vue";

const SegmentedStub = defineComponent({
  name: "SegmentedStub",
  props: { value: { type: String, default: "" } },
  emits: ["update:value"],
  setup() {
    return () => h("div");
  }
});

const account: AccountSecurityView = {
  accountId: "account-1",
  scopeType: "GLOBAL",
  scopeId: null,
  status: "ACTIVE",
  secondFactorRequired: true,
  version: "1",
  recoveryCodesIssuedAt: "2026-07-18T00:00:00Z",
  recoveryCodesRemaining: 8,
  identifiers: [],
  credentials: [],
  passkeyCredentials: []
};
const session: AccountSessionView = {
  sessionId: "session-1",
  deviceId: "device-1",
  terminalName: "Chrome",
  loginTime: "2026-07-18T00:00:00Z"
};
const identity: ExternalIdentityView = {
  identityId: "identity-1",
  providerId: "provider-1",
  providerCode: "corp",
  providerName: "Corporate identity",
  protocol: "OIDC",
  providerStatus: "ACTIVE",
  externalSubject: "alice",
  displayName: "Alice",
  email: "alice@example.com",
  emailVerified: true,
  avatarUri: null,
  lastAuthenticatedAt: null,
  version: "1"
};

function mountDetails() {
  return shallowMount(AccountSecurityDetails, {
    props: {
      account,
      accountTitle: "全局账号",
      currentAccountId: "account-1",
      currentSessionId: "session-1",
      secondStepAvailable: true,
      webAuthnAvailable: true,
      sessions: [session],
      sessionsLoading: false,
      sessionsError: false,
      externalIdentities: [identity],
      externalIdentitiesLoading: false,
      oidcProviders: [],
      oauth2Providers: [],
      ldapProviders: []
    },
    global: {
      plugins: [i18n],
      stubs: {
        "a-segmented": SegmentedStub,
        "a-tag": true
      }
    }
  });
}

async function selectSection(wrapper: ReturnType<typeof mountDetails>, section: string): Promise<void> {
  wrapper.getComponent(SegmentedStub).vm.$emit("update:value", section);
  await nextTick();
}

describe("AccountSecurityDetails", () => {
  it("默认展示验证方式并转发凭据操作", () => {
    const wrapper = mountDetails();
    const methods = wrapper.getComponent(VerificationMethodsSection);

    expect(methods.props("account")).toEqual(account);
    methods.vm.$emit("addPasskey");
    methods.vm.$emit("setupTotp", null);

    expect(wrapper.emitted("addPasskey")).toHaveLength(1);
    expect(wrapper.emitted("setupTotp")?.[0]).toEqual([null]);
  });

  it("四个业务视图独立切换并转发各自操作", async () => {
    const wrapper = mountDetails();

    await selectSection(wrapper, "two-step");
    wrapper.getComponent(TwoStepVerificationSection).vm.$emit("change", false);
    expect(wrapper.emitted("changeSecondFactor")?.[0]).toEqual([false]);

    await selectSection(wrapper, "identities");
    const identities = wrapper.getComponent(LinkedAccountsSection);
    identities.vm.$emit("link", "OIDC");
    identities.vm.$emit("unlink", identity);
    expect(wrapper.emitted("linkIdentity")?.[0]).toEqual(["OIDC"]);
    expect(wrapper.emitted("unlinkIdentity")?.[0]).toEqual([identity]);

    await selectSection(wrapper, "sessions");
    const sessions = wrapper.getComponent(LoginDevicesSection);
    sessions.vm.$emit("refresh");
    sessions.vm.$emit("end", session);
    expect(wrapper.emitted("refreshSessions")).toHaveLength(1);
    expect(wrapper.emitted("endSession")?.[0]).toEqual([session]);
  });
});
