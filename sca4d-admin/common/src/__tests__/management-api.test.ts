import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import {
  createPlatformAccountApi,
  createPlatformTenantAccountApi,
  createTenantAccountManagementApi
} from "../apis/management";

const get = vi.fn();
const request = vi.fn();
const client = { get, request } as unknown as ApiClient;

describe("managed account APIs", () => {
  beforeEach(() => vi.clearAllMocks());

  it("按关键词分页并读取平台账号资料", async () => {
    const signal = new AbortController().signal;
    const api = createPlatformAccountApi(client);

    await api.page({ page: 2, size: 20, search: "管理员", status: "ACTIVE" }, signal);
    await api.profile("80 / 1", signal);

    expect(get).toHaveBeenNthCalledWith(
      1,
      "/mg/platform/accounts",
      { page: 2, size: 20, search: "管理员", status: "ACTIVE" },
      signal
    );
    expect(get).toHaveBeenNthCalledWith(
      2,
      "/mg/platform/accounts/80%20%2F%201/profile",
      undefined,
      signal
    );
  });

  it("资料修改使用 STEP_UP，状态和删除使用强 ETag", async () => {
    const api = createPlatformAccountApi(client);

    await api.updateProfile("80 / 1", {
      displayName: "平台管理员",
      email: "admin@example.test",
      subjectVersion: "3"
    }, "step-up-1");
    await api.changeStatus("80 / 1", "DISABLED", "4");
    await api.delete("80 / 1", "5");

    expect(request).toHaveBeenNthCalledWith(1, {
      path: "/mg/platform/accounts/80%20%2F%201/profile",
      method: "PUT",
      headers: { "X-Authentication-Id": "step-up-1" },
      body: {
        displayName: "平台管理员",
        email: "admin@example.test",
        subjectVersion: "3"
      }
    });
    expect(request).toHaveBeenNthCalledWith(2, {
      path: "/mg/platform/accounts/80%20%2F%201",
      method: "PATCH",
      headers: { "If-Match": '"4"' },
      body: { status: "DISABLED" }
    });
    expect(request).toHaveBeenNthCalledWith(3, {
      path: "/mg/platform/accounts/80%20%2F%201",
      method: "DELETE",
      headers: { "If-Match": '"5"' }
    });
  });

  it("三种管理作用域退出全部设备都提交 Account ETag 和操作人 STEP_UP", async () => {
    await createPlatformAccountApi(client).endSessions("80", "4", "step-up-1");
    await createPlatformTenantAccountApi(client).endSessions("20", "81", "5", "step-up-2");
    await createTenantAccountManagementApi(client).endSessions("20", "82", "6", "step-up-3");

    expect(request).toHaveBeenNthCalledWith(1, {
      path: "/mg/platform/accounts/80/sessions",
      method: "DELETE",
      headers: { "If-Match": '"4"', "X-Authentication-Id": "step-up-1" }
    });
    expect(request).toHaveBeenNthCalledWith(2, {
      path: "/mg/platform/tenants/20/accounts/81/sessions",
      method: "DELETE",
      headers: { "If-Match": '"5"', "X-Authentication-Id": "step-up-2" }
    });
    expect(request).toHaveBeenNthCalledWith(3, {
      path: "/mg/tenant/tenants/20/accounts/82/sessions",
      method: "DELETE",
      headers: { "If-Match": '"6"', "X-Authentication-Id": "step-up-3" }
    });
  });
});
