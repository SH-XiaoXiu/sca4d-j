import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createAccountSecurityApi } from "../apis/account-security";

const get = vi.fn();
const request = vi.fn();
const getBlob = vi.fn();
const client = { get, request, getBlob } as unknown as ApiClient;

describe("account security API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("读取当前 Subject 的账号与指定账号登录会话", async () => {
    get.mockResolvedValue([]);
    const api = createAccountSecurityApi(client);

    await api.accounts();
    await api.sessions("30 / 1");

    expect(get).toHaveBeenNthCalledWith(1, "/me/accounts");
    expect(get).toHaveBeenNthCalledWith(2, "/me/accounts/30%20%2F%201/sessions");
  });

  it("使用已验证 STEP_UP Flow 撤销登录会话", async () => {
    request.mockResolvedValue(undefined);

    await createAccountSecurityApi(client).endSession("30 / 1", "session / 1", "flow-1");

    expect(request).toHaveBeenCalledWith({
      path: "/me/accounts/30%20%2F%201/sessions/session%20%2F%201",
      method: "DELETE",
      headers: { "X-Authentication-Id": "flow-1" }
    });
  });

  it("读取账号已关联身份和可以关联的 Provider", async () => {
    get.mockResolvedValue([]);
    const api = createAccountSecurityApi(client);

    await api.externalIdentities("30 / 1");
    await api.availableIdentityProviders("30 / 1");

    expect(get).toHaveBeenNthCalledWith(
      1,
      "/me/accounts/30%20%2F%201/external-identities"
    );
    expect(get).toHaveBeenNthCalledWith(
      2,
      "/me/accounts/30%20%2F%201/external-identities/providers"
    );
  });

  it("使用已验证 STEP_UP Flow 修改账号第二因子要求", async () => {
    request.mockResolvedValue({ required: true, recoveryCodes: [] });

    await createAccountSecurityApi(client).changeSecondFactorRequirement(
      "30 / 1",
      { required: true, version: "4" },
      "flow-2"
    );

    expect(request).toHaveBeenCalledWith({
      path: "/me/accounts/30%20%2F%201/second-factor/requirement",
      method: "PUT",
      headers: { "X-Authentication-Id": "flow-2" },
      body: { required: true, version: "4" }
    });
  });

  it("使用 REST 资源创建、读取、激活、替换和删除 TOTP", async () => {
    request.mockResolvedValue({ credentialId: "40", version: "0" });
    getBlob.mockResolvedValue(new Blob());
    const api = createAccountSecurityApi(client);

    await api.createTotpEnrollment("30 / 1", "flow-1");
    await api.totpEnrollmentQrCode("30 / 1", "enrollment / 1");
    await api.activateTotpCredential("30 / 1", { enrollmentId: "enrollment-1", code: "123456" });
    await api.replaceTotpCredential("30 / 1", "40 / 1", "3", {
      enrollmentId: "enrollment-2",
      code: "654321"
    });
    await api.deleteTotpCredential("30 / 1", "40 / 1", "4", "flow-2");

    expect(request).toHaveBeenNthCalledWith(1, {
      path: "/me/accounts/30%20%2F%201/totp/enrollments",
      method: "POST",
      headers: { "X-Authentication-Id": "flow-1" }
    });
    expect(getBlob).toHaveBeenCalledWith(
      "/me/accounts/30%20%2F%201/totp/enrollments/enrollment%20%2F%201/qr-code"
    );
    expect(request).toHaveBeenNthCalledWith(2, {
      path: "/me/accounts/30%20%2F%201/totp/credentials",
      method: "POST",
      body: { enrollmentId: "enrollment-1", code: "123456" }
    });
    expect(request).toHaveBeenNthCalledWith(3, {
      path: "/me/accounts/30%20%2F%201/totp/credentials/40%20%2F%201",
      method: "PUT",
      headers: { "If-Match": '"3"' },
      body: { enrollmentId: "enrollment-2", code: "654321" }
    });
    expect(request).toHaveBeenNthCalledWith(4, {
      path: "/me/accounts/30%20%2F%201/totp/credentials/40%20%2F%201",
      method: "DELETE",
      headers: {
        "If-Match": '"4"',
        "X-Authentication-Id": "flow-2"
      }
    });
  });

  it("使用 REST 资源注册、激活和删除 Passkey", async () => {
    request.mockResolvedValue({ credentialId: "41", version: "0" });
    const api = createAccountSecurityApi(client);

    await api.createPasskeyRegistration("30 / 1", { displayName: "工作电脑" }, "flow-1");
    await api.activatePasskeyCredential("30 / 1", {
      registrationId: "registration-1",
      credential: { id: "credential-1" }
    });
    await api.deletePasskeyCredential("30 / 1", "41 / 1", "2", "flow-2");

    expect(request).toHaveBeenNthCalledWith(1, {
      path: "/me/accounts/30%20%2F%201/passkey/registrations",
      method: "POST",
      headers: { "X-Authentication-Id": "flow-1" },
      body: { displayName: "工作电脑" }
    });
    expect(request).toHaveBeenNthCalledWith(2, {
      path: "/me/accounts/30%20%2F%201/passkey/credentials",
      method: "POST",
      body: { registrationId: "registration-1", credential: { id: "credential-1" } }
    });
    expect(request).toHaveBeenNthCalledWith(3, {
      path: "/me/accounts/30%20%2F%201/passkey/credentials/41%20%2F%201",
      method: "DELETE",
      headers: {
        "If-Match": '"2"',
        "X-Authentication-Id": "flow-2"
      }
    });
  });
});
