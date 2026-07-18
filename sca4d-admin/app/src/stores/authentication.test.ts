import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  establish: vi.fn(),
  createAuthentication: vi.fn(),
  createOidcAuthorization: vi.fn(),
  createOAuth2Authorization: vi.fn(),
  ldapProof: vi.fn(),
  passwordProof: vi.fn(),
  createEmailCodeChallenge: vi.fn(),
  emailCodeProof: vi.fn(),
  totpProof: vi.fn(),
  createPasskeyAssertion: vi.fn(),
  passkeyProof: vi.fn(),
  createSession: vi.fn()
}));

vi.mock("../api/client", () => ({
  authenticationApi: {
    createAuthentication: mocks.createAuthentication,
    createOidcAuthorization: mocks.createOidcAuthorization,
    createOAuth2Authorization: mocks.createOAuth2Authorization,
    ldapProof: mocks.ldapProof,
    passwordProof: mocks.passwordProof,
    createEmailCodeChallenge: mocks.createEmailCodeChallenge,
    emailCodeProof: mocks.emailCodeProof,
    totpProof: mocks.totpProof,
    createPasskeyAssertion: mocks.createPasskeyAssertion,
    passkeyProof: mocks.passkeyProof,
    createSession: mocks.createSession
  }
}));
vi.mock("./session", () => ({ useSessionStore: () => ({ establish: mocks.establish }) }));

import { useAuthenticationStore } from "./authentication";

const pending = (authenticationId: string) => ({
  authenticationId,
  purpose: "LOGIN" as const,
  activityId: "primary-proof" as const,
  subjectId: null,
  accountId: null,
  expiresAt: "2026-07-18T00:00:00Z"
});

const session = {
  sessionId: "session-1",
  tokenName: "Authorization",
  tokenValue: "token",
  subjectId: "10",
  accountId: "100",
  deviceId: null,
  terminalName: "SCA4D Admin",
  loginTime: null,
  expireTime: null
};

describe("authentication store", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.clearAllMocks();
    setActivePinia(createPinia());
    mocks.createSession.mockResolvedValue(session);
    mocks.establish.mockResolvedValue(undefined);
  });

  it("密码第一步完成后直接创建 Session", async () => {
    mocks.createAuthentication.mockResolvedValue(pending("authentication-1"));
    mocks.passwordProof.mockResolvedValue({
      ...pending("authentication-1"),
      subjectId: "10",
      accountId: "100",
      activityId: "completion"
    });
    const authentication = useAuthenticationStore();

    const needsSecondStep = await authentication.loginWithPassword(
      { identifier: "alice", password: "secret" },
      true,
      { scopeType: "GLOBAL" }
    );

    expect(needsSecondStep).toBe(false);
    expect(mocks.passwordProof).toHaveBeenCalledWith("authentication-1", {
      identifierType: "USERNAME",
      identifier: "alice",
      password: "secret"
    });
    expect(mocks.createSession).toHaveBeenCalledWith("authentication-1");
    expect(mocks.establish).toHaveBeenCalledWith(session, true);
  });

  it("账号要求二步验证时保留 Authentication", async () => {
    mocks.createAuthentication.mockResolvedValue(pending("authentication-2"));
    mocks.passwordProof.mockResolvedValue({
      ...pending("authentication-2"),
      subjectId: "10",
      accountId: "100",
      activityId: "second-factor-proof"
    });
    const authentication = useAuthenticationStore();

    const needsSecondStep = await authentication.loginWithPassword(
      { identifierType: "EMAIL", identifier: "alice@example.com", password: "secret" },
      false,
      { scopeType: "TENANT", tenantCode: "acme" }
    );

    expect(needsSecondStep).toBe(true);
    expect(authentication.pendingAuthenticationId).toBe("authentication-2");
    expect(authentication.secondStepRequired).toBe(true);
    expect(mocks.createSession).not.toHaveBeenCalled();
  });

  it("邮件验证码完成登录二步验证", async () => {
    mocks.createAuthentication.mockResolvedValue(pending("authentication-3"));
    mocks.passwordProof.mockResolvedValue({ ...pending("authentication-3"), activityId: "second-factor-proof" });
    mocks.createEmailCodeChallenge.mockResolvedValue({
      challengeId: "challenge-1",
      maskedRecipient: "a***@example.com",
      expiresAt: "2026-07-18T00:00:00Z",
      resendAt: "2026-07-17T23:59:00Z"
    });
    mocks.emailCodeProof.mockResolvedValue({ ...pending("authentication-3"), activityId: "completion" });
    const authentication = useAuthenticationStore();
    await authentication.loginWithPassword(
      { identifier: "alice", password: "secret" },
      false,
      { scopeType: "GLOBAL" }
    );

    await authentication.startEmailCode();
    await authentication.completeEmailCode("123456");

    expect(mocks.emailCodeProof).toHaveBeenCalledWith("authentication-3", {
      challengeId: "challenge-1",
      code: "123456"
    });
    expect(mocks.establish).toHaveBeenCalledWith(session, false);
    expect(authentication.pendingEmailCode).toBeNull();
  });

  it("OIDC 回调使用 activityId 继续二步验证", async () => {
    mocks.createAuthentication.mockResolvedValue(pending("authentication-oidc"));
    mocks.createOidcAuthorization.mockResolvedValue({
      authorizationUri: "https://issuer.example.com/authorize",
      expiresAt: "2026-07-18T00:00:00Z"
    });
    const authentication = useAuthenticationStore();
    await authentication.startOidcLogin("corporate", false, "/dashboard", { scopeType: "GLOBAL" });

    const result = await authentication.finishOidcLogin("authentication-oidc", "second-factor-proof");

    expect(result).toEqual({ requiresSecondFactor: true, redirect: "/dashboard" });
    expect(authentication.secondStepRequired).toBe(true);
    expect(mocks.createSession).not.toHaveBeenCalled();
  });

  it("仅在当前账号且未过期时复用 STEP_UP", () => {
    const authentication = useAuthenticationStore();
    authentication.rememberStepUp({
      authenticationId: "step-up-1",
      purpose: "STEP_UP",
      activityId: "completion",
      subjectId: "10",
      accountId: "100",
      expiresAt: new Date(Date.now() + 60_000).toISOString()
    });

    expect(authentication.reusableStepUp("100")).toBe("step-up-1");
    expect(authentication.reusableStepUp("101")).toBeNull();
  });

  it("外部身份关联只接受当前标签保存的 Authentication", async () => {
    mocks.createAuthentication.mockResolvedValue({ ...pending("authentication-link"), purpose: "LINKING" });
    mocks.createOAuth2Authorization.mockResolvedValue({
      authorizationUri: "https://provider.example.com/authorize",
      expiresAt: "2026-07-18T00:00:00Z"
    });
    const authentication = useAuthenticationStore();
    await authentication.startOAuth2Link("corporate", "/account-security");

    expect(authentication.finishExternalIdentityLink("OAUTH2", "authentication-link", "completed"))
      .toBe("/account-security");
    expect(sessionStorage.getItem("sca4d-admin.oauth2-link")).toBeNull();
  });
});
