import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../api-client";
import { createRoleApi } from "../apis/roles";

const request = vi.fn();
const get = vi.fn();
const post = vi.fn();
const put = vi.fn();

const client = {
  request,
  get,
  post,
  put,
  patch: vi.fn(),
  delete: vi.fn()
} as unknown as ApiClient;

describe("role API", () => {
  beforeEach(() => vi.clearAllMocks());

  it("权限目录和角色使用量使用角色子资源", async () => {
    get.mockResolvedValue([]);
    const api = createRoleApi(client, "/mg/platform/roles");

    await api.permissions();
    await api.usage("role-1");

    expect(get).toHaveBeenNthCalledWith(1, "/mg/platform/roles/permissions", undefined, undefined);
    expect(get).toHaveBeenNthCalledWith(2, "/mg/platform/roles/role-1/usage");
  });

  it("权限替换只提交权限集合和当前角色版本", async () => {
    put.mockResolvedValue(undefined);

    await createRoleApi(client, "/mg/tenant/tenants/tenant-1/roles")
      .replacePermissions("role-2", ["permission-3", "permission-4"], "version-5");

    expect(put).toHaveBeenCalledWith(
      "/mg/tenant/tenants/tenant-1/roles/role-2/permissions",
      { permissionIds: ["permission-3", "permission-4"], version: "version-5" }
    );
  });

  it("删除角色使用 DELETE 请求体传递版本和幂等键", async () => {
    request.mockResolvedValue(undefined);

    await createRoleApi(client, "/mg/platform/roles").delete("role-1", "version-2", "request-3");

    expect(request).toHaveBeenCalledWith({
      path: "/mg/platform/roles/role-1",
      method: "DELETE",
      body: { version: "version-2", idempotencyKey: "request-3" }
    });
  });
});
