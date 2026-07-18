import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createAuthenticationApi } from "../apis/authentication";

const get = vi.fn();
const post = vi.fn();
const request = vi.fn();
const remove = vi.fn();
const client = { get, post, request, delete: remove } as unknown as ApiClient;

describe("authentication API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("读取后端注册的认证方式目录", async () => {
    get.mockResolvedValue([{ type: "PASSWORD", capabilities: ["LOGIN"] }]);

    const methods = await createAuthenticationApi(client).methods();

    expect(get).toHaveBeenCalledWith("/authentication-methods");
    expect(methods).toEqual([{ type: "PASSWORD", capabilities: ["LOGIN"] }]);
  });

  it("使用认证 Flow 资源路径", async () => {
    post.mockResolvedValue({ authenticationId: "authentication-1" });
    const api = createAuthenticationApi(client);

    await api.createAuthentication({ purpose: "LOGIN", scopeType: "GLOBAL" });
    await api.createAuthentication({ purpose: "STEP_UP" });
    await api.oidcProviders("TENANT", "acme");
    await api.createOidcAuthorization("flow / 1", { providerCode: "corporate" });
    await api.oauth2Providers("TENANT", "acme");
    await api.createOAuth2Authorization("flow / 1", { providerCode: "corporate-oauth2" });
    await api.ldapProviders("TENANT", "acme");
    await api.ldapProof("flow / 1", {
      providerCode: "corporate-ldap",
      username: "alice",
      password: "secret"
    });
    await api.passwordProof("flow / 1", {
      identifierType: "USERNAME",
      identifier: "alice",
      password: "secret"
    });
    await api.recoveryCodeProof("flow / 1", {
      identifierType: "USERNAME",
      identifier: "alice",
      recoveryCode: "AAAAA-BBBBB-CCCCC-DDDDD"
    });
    await api.replacePassword("flow / 1", { password: "new-secret" });
    await api.createEmailCodeChallenge("flow / 1");
    await api.emailCodeProof("flow / 1", {
      challengeId: "challenge-1",
      code: "123456"
    });
    await api.totpProof("flow / 1", { code: "123456" });
    await api.createPasskeyAssertion("flow / 1");
    await api.passkeyProof("flow / 1", {
      assertionId: "assertion-1",
      credential: { id: "credential-1" }
    });
    await api.createSession("flow / 1");

    expect(post).toHaveBeenNthCalledWith(
      1,
      "/authentications",
      { purpose: "LOGIN", scopeType: "GLOBAL" }
    );
    expect(post).toHaveBeenNthCalledWith(
      2,
      "/authentications",
      { purpose: "STEP_UP" }
    );
    expect(post).toHaveBeenCalledTimes(2);
    expect(get).toHaveBeenNthCalledWith(1, "/auth/oidc/providers", {
      scopeType: "TENANT",
      tenantCode: "acme"
    });
    expect(get).toHaveBeenNthCalledWith(2, "/auth/oauth2/providers", {
      scopeType: "TENANT",
      tenantCode: "acme"
    });
    expect(get).toHaveBeenNthCalledWith(3, "/auth/ldap/providers", {
      scopeType: "TENANT",
      tenantCode: "acme"
    });
    expect(request).toHaveBeenNthCalledWith(1, {
      path: "/authentications/flow%20%2F%201/oidc-authorizations",
      method: "POST",
      body: { providerCode: "corporate" },
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(2, {
      path: "/authentications/flow%20%2F%201/oauth2-authorizations",
      method: "POST",
      body: { providerCode: "corporate-oauth2" },
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(3, {
      path: "/authentications/flow%20%2F%201/ldap-proofs",
      method: "POST",
      body: {
        providerCode: "corporate-ldap",
        username: "alice",
        password: "secret"
      },
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(4, {
      path: "/authentications/flow%20%2F%201/password-proofs",
      method: "POST",
      body: { identifierType: "USERNAME", identifier: "alice", password: "secret" },
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(5, {
      path: "/authentications/flow%20%2F%201/recovery-code-proofs",
      method: "POST",
      body: {
        identifierType: "USERNAME",
        identifier: "alice",
        recoveryCode: "AAAAA-BBBBB-CCCCC-DDDDD"
      },
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(6, {
      path: "/authentications/flow%20%2F%201/password-replacements",
      method: "POST",
      body: { password: "new-secret" },
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(7, {
      path: "/authentications/flow%20%2F%201/email-code-challenges",
      method: "POST",
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(8, {
      path: "/authentications/flow%20%2F%201/email-code-proofs",
      method: "POST",
      body: { challengeId: "challenge-1", code: "123456" },
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(9, {
      path: "/authentications/flow%20%2F%201/totp-proofs",
      method: "POST",
      body: { code: "123456" },
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(10, {
      path: "/authentications/flow%20%2F%201/passkey-assertions",
      method: "POST",
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(11, {
      path: "/authentications/flow%20%2F%201/passkey-proofs",
      method: "POST",
      body: { assertionId: "assertion-1", credential: { id: "credential-1" } },
      preserveSessionOnUnauthorized: true
    });
    expect(request).toHaveBeenNthCalledWith(12, {
      path: "/sessions",
      method: "POST",
      body: { authenticationId: "flow / 1" },
      preserveSessionOnUnauthorized: true
    });
  });

  it("删除当前认证 Session", async () => {
    remove.mockResolvedValue(undefined);

    await createAuthenticationApi(client).logout();

    expect(remove).toHaveBeenCalledWith("/sessions/current");
  });
});
