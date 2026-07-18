import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createIdentityProviderApi } from "../apis/identity-providers";

const get = vi.fn();
const post = vi.fn();
const requestResult = vi.fn();
const client = { get, post, requestResult } as unknown as ApiClient;
const root = "/mg/system/auth/providers";

describe("identity provider API", () => {
  beforeEach(() => vi.clearAllMocks());

  it("主资源写入携带幂等键和强 If-Match", async () => {
    const api = createIdentityProviderApi(client, root);
    const createInput = {
      code: "corporate",
      name: "Corporate",
      protocol: "OAUTH2" as const,
      registrationPolicy: "DISABLED" as const,
      emailAutoLink: false
    };
    const updateInput = {
      name: "Corporate OAuth",
      registrationPolicy: "JIT" as const,
      emailAutoLink: false
    };

    await api.create(createInput, "intent-1");
    await api.update("provider / 1", updateInput, "2");
    await api.changeStatus("provider / 1", "ACTIVE", "3");
    await api.delete("provider / 1", "4");

    expect(requestResult).toHaveBeenNthCalledWith(1, {
      path: root,
      method: "POST",
      body: createInput,
      headers: { "Idempotency-Key": "intent-1" }
    });
    expect(requestResult).toHaveBeenNthCalledWith(2, {
      path: `${root}/provider%20%2F%201`,
      method: "PUT",
      body: updateInput,
      headers: { "If-Match": '"2"' }
    });
    expect(requestResult).toHaveBeenNthCalledWith(3, {
      path: `${root}/provider%20%2F%201`,
      method: "PATCH",
      body: { status: "ACTIVE" },
      headers: { "If-Match": '"3"' }
    });
    expect(requestResult).toHaveBeenNthCalledWith(4, {
      path: `${root}/provider%20%2F%201`,
      method: "DELETE",
      headers: { "If-Match": '"4"' }
    });
  });

  it("协议配置、预览和连接检查使用名词子资源", async () => {
    const api = createIdentityProviderApi(client, root);
    const oauth2Input = {
      clientId: "client",
      clientSecret: "secret",
      authorizationUri: "https://provider.example.com/authorize",
      tokenUri: "https://provider.example.com/token",
      profileUri: "https://provider.example.com/me",
      scopes: ["profile"],
      profileMapping: { subjectPath: "$.id" }
    };

    await api.oidcConfiguration("provider / 1");
    await api.discoverOidc("provider / 1");
    await api.replaceOAuth2Configuration("provider / 1", oauth2Input, "5");
    await api.previewOAuth2Profile("provider / 1", { id: "alice" }, [{ email: "a@example.com" }]);
    await api.ldapConfiguration("provider / 1");
    await api.testLdapConnection("provider / 1");

    expect(get).toHaveBeenNthCalledWith(1, `${root}/provider%20%2F%201/oidc`);
    expect(post).toHaveBeenNthCalledWith(1, `${root}/provider%20%2F%201/oidc/discoveries`);
    expect(requestResult).toHaveBeenCalledWith({
      path: `${root}/provider%20%2F%201/oauth2`,
      method: "PUT",
      body: oauth2Input,
      headers: { "If-Match": '"5"' }
    });
    expect(post).toHaveBeenNthCalledWith(2, `${root}/provider%20%2F%201/oauth2/previews`, {
      profile: { id: "alice" },
      auxiliaryProfile: [{ email: "a@example.com" }]
    });
    expect(get).toHaveBeenNthCalledWith(2, `${root}/provider%20%2F%201/ldap`);
    expect(post).toHaveBeenNthCalledWith(3, `${root}/provider%20%2F%201/ldap/tests`);
  });
});
